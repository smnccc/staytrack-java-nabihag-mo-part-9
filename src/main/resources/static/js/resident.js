/* --- StayTrack — resident portal logic (wires the existing UI to the API) --- */
'use strict';

const session = requireSession();
if (session) {
  if (session.role === 'admin') {
    location.href = '/admin.html';
  } else {
    const chip = qs('#userChip');
    if (chip) chip.textContent = `${session.firstName || ''} ${session.lastName || ''}`.trim() || session.email;
    loadResidentData();
  }
}

/* --- Load everything the home / pay / profile screens need --- */
async function loadResidentData() {
  try {
    const [me, paymentsRes, requestsRes] = await Promise.all([
      api.get('/auth/me'),
      api.get('/payments'),
      api.get('/requests'),
    ]);

    const user = me.user;
    const payments = paymentsRes.payments || [];
    const requests = requestsRes.requests || [];
    const nextDue = paymentsRes.nextDue;
    const summary = paymentsRes.summary || { outstanding: 0, totalPaid: 0 };

    /* --- Top bar at profile --- */
    const fullName = `${user.firstName || ''} ${user.lastName || ''}`.trim();
    const initials = (user.firstName?.[0] || '') + (user.lastName?.[0] || '');

    setText('#userChip', fullName || user.email);
    setText('#homeName', user.firstName || 'Resident');
    setText('#homeRoom', user.roomNumber ? `Room ${user.roomNumber}` : 'No room yet');

    renderAvatar(user.avatarUrl, initials);
    setText('#profileName', fullName || '—');
    setText('#profileRoom', user.roomNumber ? `Resident · Room ${user.roomNumber}` : 'Resident · Unassigned');
    setText('#profileEmail', user.email);
    setText('#profilePhone', user.phone || '—');
    setText('#profileBuilding', user.building || '—');
    setText('#profileMoveIn', formatDate(user.moveInDate));
    setText('#profileStatus', (user.status || '').replace(/^\w/, (c) => c.toUpperCase()) + ' Tenant');

    const contractEl = qs('#profileContract');
    if (contractEl) {
      if (user.contractUrl) {
        contractEl.innerHTML = `<a href="${user.contractUrl}" target="_blank" rel="noopener" style="color:var(--accent);font-weight:600;">View contract (PDF)</a>`;
      } else {
        contractEl.innerHTML = '<span style="color:var(--muted);">Not uploaded yet</span>';
      }
    }

    /* --- Stat cards sa Home --- */
    setText('#statBalance', formatMoney(summary.outstanding));
    setText('#statRequests', String(requests.filter((r) => ['pending', 'in_progress'].includes(r.status)).length));
    setText('#statPaid', formatMoney(summary.totalPaid));

    /* --- Reminder sa payment --- */
    if (nextDue) {
      setText('#dueAmount', formatMoney(nextDue.balance));
      const days = Math.ceil((new Date(nextDue.dueDate) - new Date()) / 86400000);
      let note;
      if (nextDue.status === 'submitted') {
        note = 'Payment under review';
      } else if (nextDue.status === 'rejected') {
        note = 'Payment rejected — please re-upload';
      } else if (days > 0) {
        note = `Due in ${days} day${days === 1 ? '' : 's'}`;
      } else if (days === 0) {
        note = 'Due today';
      } else {
        note = `Overdue by ${Math.abs(days)} day(s)`;
      }
      setText('#dueNote', note);
    } else {
      setText('#dueAmount', formatMoney(0));
      setText('#dueNote', 'You are all caught up 🎉');
    }

    /* --- Listahan ng My Payments --- */
    renderMyPayments(payments);

    /* --- Card ng active request --- */
    const active = requests.find((r) => ['pending', 'in_progress'].includes(r.status));
    const card = qs('#activeRequestCard');
    if (card) {
      if (active) {
        card.innerHTML = `
          <div style="flex:1;min-width:200px;">
            <div style="font-weight:600;font-size:.98rem;margin-bottom:5px;">${escapeHtml(active.category)}</div>
            <div style="color:var(--muted);font-size:.8rem;">Submitted: ${formatDate(active.createdAt)}</div>
            ${adminNoteBlock(active.adminNotes)}
          </div>
          <span class="status ${active.status === 'in_progress' ? 'progress' : 'pending'}">
            ${active.status === 'in_progress' ? 'In Progress' : 'Pending'}
          </span>`;
      } else {
        card.innerHTML = `
          <div>
            <div style="font-weight:600;font-size:.98rem;margin-bottom:5px;color:var(--muted);">No active requests</div>
            <div style="color:var(--muted);font-size:.8rem;">Submit one from the Requests tab</div>
          </div>`;
      }
    }

    /* --- Listahan ng My Requests --- */
    renderMyRequests(requests);

    /* --- Bill na babayaran sa Pay page --- */
    window.__currentBill = nextDue || null;
    if (nextDue) {
      setText('#billPeriod', new Date(nextDue.billingPeriod).toLocaleDateString('en-US', { month: 'long', year: 'numeric' }));
      setText('#payRent', formatMoney(nextDue.rentAmount));
      setText('#payUtilities', formatMoney(nextDue.utilitiesAmount));
      setText('#payOther', formatMoney(nextDue.otherAmount));
      setText('#payTotal', formatMoney(nextDue.balance));
      setText('#billDue', `Due ${formatDate(nextDue.dueDate)} · Status: ${nextDue.status.replace('_', ' ')}`);
      const badge = qs('#billStatus');
      if (badge) {
        badge.textContent = nextDue.status.replace('_', ' ').toUpperCase();
      }
    } else {
      setText('#billPeriod', 'No bill on file');
      setText('#billDue', 'You have no outstanding payments.');
    }
  } catch (err) {
    console.error('[resident] failed to load data', err);
  }
}

function setText(selector, value) {
  const el = qs(selector);
  if (el) el.textContent = value;
}

// Admin notes sa My Payments cards lang, hindi sa Home, para walang lumang note na maiwan.

/** Ipakita yung photo, o initials kung walang picture. */
function renderAvatar(avatarUrl, initials) {
  const el = qs('#profileAvatar');
  if (!el) return;
  if (avatarUrl) {
    el.textContent = '';
    el.style.backgroundImage = `url('${avatarUrl}')`;
    el.style.backgroundSize = 'cover';
    el.style.backgroundPosition = 'center';
  } else {
    el.style.backgroundImage = '';
    el.textContent = (initials || '?').toUpperCase();
  }
}

/** Box para sa admin note; pula kapag rejected ang payment. */
function adminNoteBlock(note, tone) {
  if (!note) return '';
  const danger = tone === 'danger';
  const label = danger ? 'Payment rejected' : 'Note from admin';
  const background = danger ? 'var(--danger-bg)' : 'var(--accent-soft)';
  const message = danger ? String(note) + ' Please upload a new proof of payment.' : note;
  return `<div style="margin-top:12px;padding:11px 13px;border-radius:12px;background:${background};border:1px solid var(--danger-border);">
      <div style="font-size:.68rem;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:var(--accent);">${label}</div>
      <div style="font-size:.86rem;margin-top:5px;line-height:1.5;">${escapeHtml(message)}</div>
    </div>`;
}

/** Badge ng status ng payment. */
function paymentBadge(status) {
  const meta =
    {
      unpaid: { label: 'Unpaid', cls: 'pending', style: '' },
      submitted: { label: 'Under review', cls: 'progress', style: '' },
      verified: { label: 'Verified', cls: 'resolved', style: '' },
      rejected: { label: 'Rejected', cls: 'pending', style: 'background:rgba(231,76,60,.16);color:#e74c3c;' },
      overdue: { label: 'Overdue', cls: 'pending', style: '' },
    }[status] || { label: status, cls: 'pending', style: '' };
  const extra = meta.style ? ` style="${meta.style}"` : '';
  return `<span class="status ${meta.cls}"${extra}>${escapeHtml(meta.label)}</span>`;
}

/** I-render lahat ng payment, may admin note sa baba ng card. */
function renderMyPayments(payments) {
  const host = qs('#myPaymentsList');
  if (!host) return;

  if (!payments || !payments.length) {
    host.innerHTML =
      '<div class="card"><div style="color:var(--muted);font-size:.86rem;">You have no payment records yet.</div></div>';
    return;
  }

  host.innerHTML = payments
    .map((p) => {
      const period = p.billingPeriod
        ? new Date(p.billingPeriod).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
        : '—';
      const balance = Number(p.balance || 0);
      const detail = [
        `Total ${formatMoney(p.totalAmount)}`,
        `Paid ${formatMoney(p.amountPaid)}`,
        p.referenceNo ? `Ref ${escapeHtml(p.referenceNo)}` : null,
      ]
        .filter(Boolean)
        .join(' · ');

      return `
      <div class="card" style="margin-bottom:12px;">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:14px;flex-wrap:wrap;">
          <div style="flex:1;min-width:180px;">
            <div style="font-weight:600;font-size:.95rem;">${escapeHtml(period)}</div>
            <div style="color:var(--muted);font-size:.78rem;margin-top:4px;">${detail}</div>
            ${balance > 0 ? `<div style="color:var(--muted);font-size:.78rem;margin-top:4px;">Balance ${formatMoney(balance)}</div>` : ''}
            ${adminNoteBlock(p.remarks, p.status === 'rejected' ? 'danger' : 'info')}
            ${
              p.status === 'verified'
                ? `<button class="btn btn-ghost" data-payment-delete="${p.id}" style="margin-top:10px;color:var(--accent);border-color:var(--danger-border);font-size:.8rem;padding:8px 14px;">Delete record</button>`
                : ''
            }
          </div>
          ${paymentBadge(p.status)}
        </div>
      </div>`;
    })
    .join('');
}

/** Burahin ang verified payment, kailangan i-type yung email muna. */
async function deletePaymentRecord(id) {
  const accountEmail = ((Store.user || {}).email || '').trim();
  if (!accountEmail) {
    toast('Could not read your account email. Please log in again.');
    return;
  }

  const typed = prompt(
    'Deleting this payment record cannot be undone.\n\nType your StayTrack email address to confirm:\n' +
      accountEmail
  );
  if (typed === null) return;
  if (typed.trim().toLowerCase() !== accountEmail.toLowerCase()) {
    toast('The email did not match. Nothing was deleted.');
    return;
  }

  try {
    await api.del(`/payments/${id}`);
    toast('Payment record deleted.');
    loadResidentData();
  } catch (err) {
    toast(err.message || 'Could not delete the payment record.');
  }
}

/** I-render lahat ng request ng tenant, kasama reply ng admin. */
function renderMyRequests(requests) {
  const host = qs('#myRequestsList');
  if (!host) return;

  if (!requests || !requests.length) {
    host.innerHTML =
      '<div class="card"><div style="color:var(--muted);font-size:.86rem;">You have not submitted any requests yet.</div></div>';
    return;
  }

  const meta = {
    pending: { label: 'Pending', cls: 'pending' },
    in_progress: { label: 'In Progress', cls: 'progress' },
    resolved: { label: 'Resolved', cls: 'resolved' },
    rejected: { label: 'Closed', cls: 'pending' },
    cancelled: { label: 'Cancelled', cls: 'pending' },
  };

  host.innerHTML = requests
    .map((r) => {
      const m = meta[r.status] || { label: r.status, cls: 'pending' };
      const typeLabel = r.type === 'neighbor' ? 'Neighbor report' : 'Maintenance';
      return `
        <div class="card" style="margin-bottom:12px;">
          <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:14px;flex-wrap:wrap;">
            <div style="flex:1;min-width:180px;">
              <div style="font-weight:600;font-size:.95rem;">${escapeHtml(r.category)}</div>
              <div style="color:var(--muted);font-size:.78rem;margin-top:4px;">
                ${typeLabel} · Submitted ${formatDate(r.createdAt)}
              </div>
              ${adminNoteBlock(r.adminNotes)}
              ${r.status === 'resolved'
                ? `<button class="btn btn-ghost" data-request-delete="${r.id}" style="margin-top:10px;color:var(--accent);border-color:var(--danger-border);font-size:.8rem;padding:8px 14px;">Delete request</button>`
                : ''}
            </div>
            <span class="status ${m.cls}">${escapeHtml(m.label)}</span>
          </div>
        </div>`;
    })
    .join('');
}

window.loadResidentData = loadResidentData;

/* --- Submit payment proof --- */
async function submitPaymentProof() {
  const bill = window.__currentBill;
  if (!bill) {
    toast('You have no outstanding bill to pay.');
    return;
  }
  const fileInput = qs('#proofFile');
  if (!fileInput.files[0]) {
    toast('Please select a screenshot of your payment first.');
    return;
  }

  const ref = qs('#referenceNo')?.value.trim();
  if (!ref) {
    toast('Please enter the reference number from your payment receipt.');
    return;
  }

  const btn = qs('#submitProofBtn');
  setLoading(btn, true, 'Uploading…');

  const form = new FormData();
  form.append('proof', fileInput.files[0]);
  form.append('referenceNo', ref);

  try {
    await api.post(`/payments/${bill.id}/proof`, form);
    toast('Payment proof submitted! An admin will verify it.');
    fileInput.value = '';
    qs('#fileName').textContent = '';
    if (qs('#referenceNo')) qs('#referenceNo').value = '';
    loadResidentData();
  } catch (err) {
    toast(err.message || 'Could not upload proof.');
  } finally {
    setLoading(btn, false);
  }
}

/* --- Profile: change photo + edit details --- */
async function uploadAvatar() {
  const file = qs('#avatarFile')?.files[0];
  if (!file) return;

  const form = new FormData();
  form.append('avatar', file);
  try {
    await api.post('/tenant/avatar', form);
    toast('Profile photo updated.');
    qs('#avatarFile').value = '';
    loadResidentData();
  } catch (err) {
    toast(err.message || 'Could not update your photo.');
  }
}

async function saveProfile() {
  const btn = qs('#saveProfileBtn');
  const firstName = qs('#editFirstName').value.trim();
  const lastName = qs('#editLastName').value.trim();
  const phone = qs('#editPhone').value.trim();

  if (!firstName || !lastName || !phone) {
    toast('First name, last name and phone number are required.');
    return;
  }

  setLoading(btn, true, 'Saving…');
  try {
    await api.patch('/tenant/profile', { firstName, lastName, phone });
    Store.updateUser({ firstName, lastName, phone });
    qs('#editProfileCard').style.display = 'none';
    toast('Profile updated.');
    loadResidentData();
  } catch (err) {
    toast(err.message || 'Could not update your profile.');
  } finally {
    setLoading(btn, false);
  }
}

qs('#changePhotoBtn')?.addEventListener('click', () => qs('#avatarFile')?.click());
qs('#avatarFile')?.addEventListener('change', uploadAvatar);
qs('#saveProfileBtn')?.addEventListener('click', saveProfile);
qs('#cancelProfileBtn')?.addEventListener('click', () => {
  qs('#editProfileCard').style.display = 'none';
});
qs('#editProfileBtn')?.addEventListener('click', () => {
  const u = Store.user || {};
  qs('#editFirstName').value = u.firstName || '';
  qs('#editLastName').value = u.lastName || '';
  qs('#editPhone').value = u.phone || '';
  const card = qs('#editProfileCard');
  card.style.display = 'block';
  card.scrollIntoView({ behavior: 'smooth', block: 'center' });
});

/* --- Delete a resolved request --- */
async function deleteRequest(id) {
  if (!confirm('Delete this resolved request? This cannot be undone.')) return;
  try {
    await api.del(`/requests/${id}`);
    toast('Request deleted.');
    loadResidentData();
  } catch (err) {
    toast(err.message || 'Could not delete the request.');
  }
}

document.addEventListener('click', (e) => {
  const requestBtn = e.target.closest('[data-request-delete]');
  if (requestBtn) return deleteRequest(requestBtn.dataset.requestDelete);

  const paymentBtn = e.target.closest('[data-payment-delete]');
  if (paymentBtn) deletePaymentRecord(paymentBtn.dataset.paymentDelete);
});

/* --- Keep the theme toggle persistent --- */
document.getElementById('themeSwitch')?.addEventListener('click', () => {
  const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
  localStorage.setItem('staytrack_theme', next);
});
