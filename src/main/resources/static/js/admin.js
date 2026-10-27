/* --- StayTrack — Admin console logic --- */
'use strict';

const adminUser = requireSession('admin');
if (adminUser) {
  qs('#adminWho').textContent = `${adminUser.firstName || ''} ${adminUser.lastName || ''}`.trim() || adminUser.email;
}

/* --- Theme at logout --- */
qs('#themeSwitch').addEventListener('click', () => {
  const root = document.documentElement;
  const next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
  root.setAttribute('data-theme', next);
  localStorage.setItem('staytrack_theme', next);
});
qs('#logoutBtn').addEventListener('click', logout);

/* --- Cache para sa dropdowns at delete confirm --- */
let roomsCache = [];
let tenantsCache = [];
let paymentsCache = [];
let requestsCache = [];

/* --- Tabs --- */
const loaders = {
  overview: loadOverview,
  tenants: loadTenants,
  requests: loadRequests,
  payments: loadPayments,
  rooms: loadRooms,
  audit: loadAuditLogs,
};

function goTab(name) {
  qsa('#tabs button').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
  qsa('.tab-panel').forEach((p) => p.classList.toggle('active', p.id === `panel-${name}`));
  if (loaders[name]) loaders[name]();
}

qsa('#tabs button').forEach((b) => b.addEventListener('click', () => goTab(b.dataset.tab)));
qsa('[data-goto]').forEach((b) => b.addEventListener('click', () => goTab(b.dataset.goto)));

/* --- Modal helpers --- */
const backdrop = qs('#modalBackdrop');
const modal = qs('#modal');

function openModal(html) {
  modal.innerHTML = html;
  backdrop.classList.add('show');
}
function closeModal() {
  backdrop.classList.remove('show');
  modal.innerHTML = '';
}
backdrop.addEventListener('click', (e) => {
  if (e.target === backdrop) closeModal();
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') closeModal();
});

/* --- Overview --- */
async function loadOverview() {
  try {
    const { tenants, requests, payments } = await api.get('/admin/dashboard');

    qs('#statGrid').innerHTML = `
      <div class="stat-card"><div class="num">${tenants.active}</div><div class="lbl">Active tenants</div></div>
      <div class="stat-card"><div class="num">${tenants.pending}</div><div class="lbl">Pending accounts</div></div>
      <div class="stat-card"><div class="num">${requests.pending}</div><div class="lbl">Open requests</div></div>
      <div class="stat-card"><div class="num">${payments.awaitingVerification}</div><div class="lbl">Awaiting verification</div></div>
      <div class="stat-card"><div class="num">${formatMoney(payments.outstanding)}</div><div class="lbl">Outstanding balance</div></div>
      <div class="stat-card"><div class="num">${formatMoney(payments.collected)}</div><div class="lbl">Collected (verified)</div></div>
    `;

    const reqs = await api.get('/admin/requests?status=pending');
    const list = (reqs.requests || []).slice(0, 5);
    qs('#recentRequests').innerHTML = list.length
      ? `<thead><tr><th>Tenant</th><th>Type</th><th>Category</th><th>Submitted</th><th></th></tr></thead><tbody>` +
        list
          .map(
            (r) => `<tr>
              <td>${escapeHtml(r.tenant ? r.tenant.name : '—')}<div class="muted" style="font-size:.75rem;">Room ${escapeHtml(r.tenant?.roomNumber || '—')}</div></td>
              <td>${escapeHtml(r.type)}</td>
              <td>${escapeHtml(r.category)}</td>
              <td>${formatDate(r.createdAt)}</td>
              <td>${badge(r.status)}</td>
            </tr>`
          )
          .join('') +
        '</tbody>'
      : `<tbody><tr><td class="empty-state">No pending requests. 🎉</td></tr></tbody>`;

    const pays = await api.get('/admin/payments?status=submitted');
    const plist = (pays.payments || []).slice(0, 5);
    qs('#recentPayments').innerHTML = plist.length
      ? `<thead><tr><th>Tenant</th><th>Period</th><th>Amount</th><th>Submitted</th><th></th></tr></thead><tbody>` +
        plist
          .map(
            (p) => `<tr>
              <td>${escapeHtml(p.tenantName || '')}</td>
              <td>${formatDate(p.billingPeriod)}</td>
              <td>${formatMoney(p.totalAmount)}</td>
              <td>${formatDate(p.submittedAt)}</td>
              <td><button class="btn btn-accent btn-sm" data-verify="${p.id}">Verify</button></td>
            </tr>`
          )
          .join('') +
        '</tbody>'
      : `<tbody><tr><td class="empty-state">Nothing waiting for verification.</td></tr></tbody>`;
  } catch (err) {
    console.error(err);
  }
}

/* --- Tenants --- */
async function loadTenants() {
  const search = qs('#tenantSearch').value.trim();
  const status = qs('#tenantStatusFilter').value;
  const params = new URLSearchParams();
  if (search) params.set('search', search);
  if (status) params.set('status', status);

  const table = qs('#tenantsTable');
  table.innerHTML = `<tbody><tr><td class="empty-state">Loading…</td></tr></tbody>`;

  try {
    const { tenants } = await api.get('/admin/tenants?' + params.toString());
    tenantsCache = tenants;
    if (!tenants.length) {
      table.innerHTML = `<tbody><tr><td class="empty-state">No tenants found.</td></tr></tbody>`;
      return;
    }
    table.innerHTML =
      `<thead><tr>
        <th>Name</th><th>Email</th><th>Building</th><th>Room</th><th>Status</th><th>Contract</th><th>Balance</th><th>Actions</th>
      </tr></thead><tbody>` +
      tenants
        .map(
          (t) => `<tr>
            <td>${escapeHtml(t.firstName + ' ' + t.lastName)}${t.emailVerified ? '' : ' <span class="muted" style="font-size:.7rem;">(unverified)</span>'}</td>
            <td>${escapeHtml(t.email)}</td>
            <td>${t.building ? escapeHtml(t.building) : '<span class="muted">—</span>'}</td>
            <td>${t.roomNumber ? escapeHtml(t.roomNumber) : '<span class="muted">—</span>'}</td>
            <td>${badge(t.status)}</td>
            <td><div class="flex" style="gap:6px;flex-wrap:wrap;">${t.contractUrl
              ? `<a class="btn btn-ghost btn-sm" href="${escapeHtml(t.contractUrl)}" target="_blank" rel="noopener">View</a><button class="btn btn-ghost btn-sm" data-tenant-contract="${t.id}">Replace</button>`
              : `<button class="btn btn-accent btn-sm" data-tenant-contract="${t.id}">Upload PDF</button>`}</div></td>
            <td>${formatMoney(t.outstandingBalance)}</td>
            <td class="flex" style="gap:6px;flex-wrap:wrap;">
              <button class="btn btn-ghost btn-sm" data-tenant-bill="${t.id}">Add bill</button>
              <button class="btn btn-ghost btn-sm" data-tenant-view="${t.id}">View</button>
              <button class="btn btn-ghost btn-sm" data-tenant-edit="${t.id}">Edit</button>
              ${t.status !== 'rejected' ? `<button class="btn btn-danger btn-sm" data-tenant-deactivate="${t.id}">Deactivate</button>` : ''}
              <button class="btn btn-danger btn-sm" data-tenant-delete="${t.id}">Delete</button>
            </td>
          </tr>`
        )
        .join('') +
      '</tbody>';
  } catch (err) {
    table.innerHTML = `<tbody><tr><td class="empty-state">${escapeHtml(err.message)}</td></tr></tbody>`;
  }
}

async function showTenant(id) {
  openModal('<p class="muted">Loading…</p>');
  try {
    const { tenant, payments, requests } = await api.get(`/admin/tenants/${id}`);
    openModal(`
      <h3>${escapeHtml(tenant.firstName + ' ' + tenant.lastName)}</h3>
      <div class="muted" style="font-size:.85rem;margin-bottom:16px;">${escapeHtml(tenant.email)}</div>
      <div class="info-list">
        ${infoRow('Phone', tenant.phone || '—')}
        ${infoRow('Status', tenant.status)}
        ${infoRow('Room', tenant.roomNumber ? tenant.building + ' · ' + tenant.roomNumber : '—')}
        ${infoRow('Move-in', formatDate(tenant.moveInDate))}
        ${infoRow('Last login', formatDateTime(tenant.lastLoginAt))}
      </div>
      <div class="section-title" style="margin:22px 0 10px;font-weight:700;">Billing history</div>
      ${miniList(payments, (p) => `${formatDate(p.billingPeriod)} · ${formatMoney(p.totalAmount)} · ${badge(p.status)}`)}
      <div class="section-title" style="margin:22px 0 10px;font-weight:700;">Requests</div>
      ${miniList(requests, (r) => `${escapeHtml(r.category)} · ${badge(r.status)}`)}
      <button class="btn btn-ghost btn-block mt-16" onclick="closeModal()">Close</button>
    `);
  } catch (err) {
    openModal(`<h3>Error</h3><p class="muted">${escapeHtml(err.message)}</p>`);
  }
}

async function editTenant(id) {
  if (!roomsCache.length) await loadRooms(true);
  let tenant = null;
  try {
    const data = await api.get(`/admin/tenants/${id}`);
    tenant = data.tenant;
  } catch (err) {
    return openModal(`<h3>Error</h3><p class="muted">${escapeHtml(err.message)}</p>`);
  }

  const roomOptions = roomsCache
    .map(
      (r) =>
        `<option value="${r.id}" ${tenant.roomId === r.id ? 'selected' : ''}>${escapeHtml(r.building + ' · ' + r.roomNumber)}</option>`
    )
    .join('');

  openModal(`
    <h3>Edit tenant</h3>
    <div class="row-2">
      <div class="field"><label>First name</label><input class="control" id="etFirst" value="${escapeHtml(tenant.firstName)}" /></div>
      <div class="field"><label>Last name</label><input class="control" id="etLast" value="${escapeHtml(tenant.lastName)}" /></div>
    </div>
    <div class="field"><label>Phone</label><input class="control" id="etPhone" value="${escapeHtml(tenant.phone || '')}" /></div>
    <div class="field"><label>Room assignment</label>
      <select class="control" id="etRoom"><option value="">— Unassigned —</option>${roomOptions}</select>
    </div>
    <div class="field"><label>Move-in date <span class="opt">(optional)</span></label>
      <input class="control" type="date" id="etMoveIn" value="${tenant.moveInDate || ''}" />
    </div>
    <div class="field"><label>Account status</label>
      <select class="control" id="etStatus">
        ${['active', 'pending', 'suspended', 'rejected']
          .map((s) => `<option value="${s}" ${tenant.status === s ? 'selected' : ''}>${s}</option>`)
          .join('')}
      </select>
    </div>
    <div class="alert" id="modalAlert"></div>
    <div class="flex mt-16" style="justify-content:flex-end;">
      <button class="btn btn-ghost" onclick="closeModal()">Cancel</button>
      <button class="btn btn-accent" id="saveTenantBtn">Save changes</button>
    </div>
  `);

  qs('#saveTenantBtn').addEventListener('click', async () => {
    const btn = qs('#saveTenantBtn');
    const payload = {
      firstName: qs('#etFirst').value.trim(),
      lastName: qs('#etLast').value.trim(),
      phone: qs('#etPhone').value.trim(),
      status: qs('#etStatus').value,
      roomId: qs('#etRoom').value || null,
      moveInDate: qs('#etMoveIn').value || null,
    };
    setLoading(btn, true, 'Saving…');
    try {
      await api.patch(`/admin/tenants/${id}`, payload);
      closeModal();
      loadTenants();
    } catch (err) {
      setLoading(btn, false);
      showAlert(qs('#modalAlert'), err.message);
    }
  });
}

async function addTenant() {
  if (!roomsCache.length) await loadRooms(true);
  const roomOptions = roomsCache
    .filter((r) => r.status !== 'maintenance')
    .map((r) => `<option value="${r.id}">${escapeHtml(r.building + ' · ' + r.roomNumber)}</option>`)
    .join('');

  openModal(`
    <h3>Add a tenant</h3>
    <p class="muted" style="font-size:.84rem;margin-bottom:18px;">A verification code will be emailed so they can activate their account.</p>
    <div class="row-2">
      <div class="field"><label>First name</label><input class="control" id="atFirst" /></div>
      <div class="field"><label>Last name</label><input class="control" id="atLast" /></div>
    </div>
    <div class="field"><label>Email</label><input class="control" type="email" id="atEmail" /></div>
    <div class="field"><label>Phone <span class="opt">(optional)</span></label><input class="control" id="atPhone" /></div>
    <div class="row-2">
      <div class="field"><label>Room <span class="opt">(optional)</span></label>
        <select class="control" id="atRoom"><option value="">— Unassigned —</option>${roomOptions}</select>
      </div>
      <div class="field"><label>Move-in date <span class="opt">(optional)</span></label><input class="control" type="date" id="atMoveIn" /></div>
    </div>
    <div class="alert" id="modalAlert"></div>
    <div class="flex mt-16" style="justify-content:flex-end;">
      <button class="btn btn-ghost" onclick="closeModal()">Cancel</button>
      <button class="btn btn-accent" id="createTenantBtn">Create account</button>
    </div>
  `);

  qs('#createTenantBtn').addEventListener('click', async () => {
    const btn = qs('#createTenantBtn');
    const payload = {
      firstName: qs('#atFirst').value.trim(),
      lastName: qs('#atLast').value.trim(),
      email: qs('#atEmail').value.trim(),
      phone: qs('#atPhone').value.trim(),
      roomId: qs('#atRoom').value || undefined,
      moveInDate: qs('#atMoveIn').value || undefined,
    };
    if (!payload.firstName || !payload.lastName || !payload.email) {
      return showAlert(qs('#modalAlert'), 'First name, last name and email are required.');
    }
    setLoading(btn, true, 'Creating…');
    try {
      const data = await api.post('/admin/tenants', payload);
      closeModal();
      loadTenants();
      if (data.devOtp) alert(`Development mode — tenant verification code: ${data.devOtp}`);
    } catch (err) {
      setLoading(btn, false);
      showAlert(qs('#modalAlert'), err.message);
    }
  });
}

async function deactivateTenant(id) {
  if (!confirm('Deactivate this tenant account? They will no longer be able to log in.')) return;
  try {
    await api.del(`/admin/tenants/${id}`);
    loadTenants();
  } catch (err) {
    alert(err.message);
  }
}

/** Buong-burang bura ng tenant at lahat ng history niya. */
async function deleteTenantPermanently(id) {
  const tenant = tenantsCache.find((t) => t.id === id);
  const label = tenant ? `${tenant.firstName} ${tenant.lastName} (${tenant.email})` : 'this tenant';

  if (
    !confirm(
      `Permanently delete ${label}?\n\nThis removes their account AND all their payment and request history.\nThis cannot be undone.`
    )
  ) {
    return;
  }

  if (!tenant) {
    return alert('Could not find that tenant. Refresh and try again.');
  }

  const typed = prompt(`Type the tenant's email to confirm permanent deletion:\n${tenant.email}`);
  if (typed === null) return;
  if (typed.trim().toLowerCase() !== tenant.email.toLowerCase()) {
    return alert('The email did not match. Nothing was deleted.');
  }

  try {
    const data = await api.del(`/admin/tenants/${id}/permanent`);
    await loadTenants();
    loadRooms(true);
    alert((data && data.message) || 'Tenant deleted.');
  } catch (err) {
    alert(err.message);
  }
}

/* --- Requests --- */
async function loadRequests() {
  const params = new URLSearchParams();
  const search = qs('#requestSearch').value.trim();
  const status = qs('#requestStatusFilter').value;
  const type = qs('#requestTypeFilter').value;
  if (search) params.set('search', search);
  if (status) params.set('status', status);
  if (type) params.set('type', type);

  const table = qs('#requestsTable');
  table.innerHTML = `<tbody><tr><td class="empty-state">Loading…</td></tr></tbody>`;

  try {
    const { requests } = await api.get('/admin/requests?' + params.toString());
    requestsCache = requests;
    if (!requests.length) {
      table.innerHTML = `<tbody><tr><td class="empty-state">No requests found.</td></tr></tbody>`;
      return;
    }
    table.innerHTML =
      `<thead><tr><th>Tenant</th><th>Type</th><th>Category</th><th>Description</th><th>Status</th><th>Submitted</th><th></th></tr></thead><tbody>` +
      requests
        .map(
          (r) => `<tr>
            <td>${escapeHtml(r.tenant ? r.tenant.name : '—')}<div class="muted" style="font-size:.72rem;">Room ${escapeHtml(r.tenant?.roomNumber || '—')}</div></td>
            <td>${escapeHtml(r.type)}</td>
            <td>${escapeHtml(r.category)}</td>
            <td style="max-width:260px;">${escapeHtml(r.description)}${r.photoUrl ? ` <a href="${r.photoUrl}" target="_blank" rel="noopener" style="color:var(--accent);font-weight:600;">photo</a>` : ''}</td>
            <td>${badge(r.status)}</td>
            <td>${formatDate(r.createdAt)}</td>
            <td class="flex" style="gap:6px;flex-wrap:wrap;">
              <button class="btn btn-ghost btn-sm" data-request-manage="${r.id}">Manage</button>
              ${r.status === 'resolved'
                ? `<button class="btn btn-danger btn-sm" data-request-delete="${r.id}">Delete</button>`
                : ''}
            </td>
          </tr>`
        )
        .join('') +
      '</tbody>';
  } catch (err) {
    table.innerHTML = `<tbody><tr><td class="empty-state">${escapeHtml(err.message)}</td></tr></tbody>`;
  }
}

/** Magtatanong muna bago burahin ang resolved na request. */
function deleteRequestModal(id) {
  const r = requestsCache.find((x) => x.id === id);
  if (!r) {
    return alert('Could not find that request. Refresh the table and try again.');
  }

  openModal(`
    <h3>Delete this resolved request?</h3>
    <p class="muted" style="font-size:.84rem;margin-bottom:16px;">
      This permanently removes the request for you and the tenant. It cannot be undone.
    </p>
    <div class="card" style="background:var(--surface-2);border:1px solid var(--border);margin-bottom:16px;">
      ${infoRow('Tenant', r.tenant ? r.tenant.name : '—')}
      ${infoRow('Room', r.tenant && r.tenant.roomNumber ? r.tenant.roomNumber : '—')}
      ${infoRow('Type', r.type)}
      ${infoRow('Category', r.category)}
      ${infoRow('Status', r.status)}
      ${infoRow('Submitted', formatDate(r.createdAt))}
    </div>
    <div class="card" style="background:var(--surface-2);border:1px solid var(--border);margin-bottom:16px;font-size:.85rem;line-height:1.55;">
      ${escapeHtml(r.description)}
    </div>
    <div class="alert" id="modalAlert"></div>
    <div class="flex mt-16" style="justify-content:flex-end;">
      <button class="btn btn-ghost" onclick="closeModal()">Cancel</button>
      <button class="btn btn-danger" id="drBtn">Delete request</button>
    </div>
  `);

  qs('#drBtn').addEventListener('click', async () => {
    const btn = qs('#drBtn');
    setLoading(btn, true, 'Deleting…');
    try {
      const data = await api.del(`/admin/requests/${id}`);
      closeModal();
      loadRequests();
      loadOverview();
      if (data && data.message) alert(data.message);
    } catch (err) {
      setLoading(btn, false);
      showAlert(qs('#modalAlert'), err.message);
    }
  });
}

async function manageRequest(id) {
  let request;
  try {
    const data = await api.get('/admin/requests?search='); // fallback not needed
    request = (data.requests || []).find((r) => r.id === id);
  } catch (_) {
    request = null;
  }
  if (!request) {
    // Di pwedeng sa tenant routes, sa admin list na lang kunin.
    return alert('Could not load that request. Refresh and try again.');
  }

  openModal(`
    <h3>Manage request</h3>
    <div class="muted" style="font-size:.85rem;line-height:1.6;margin-bottom:16px;">
      <strong>${escapeHtml(request.category)}</strong> · ${escapeHtml(request.type)}<br/>
      ${escapeHtml(request.description)}
    </div>
    <div class="field"><label>Status</label>
      <select class="control" id="mrStatus">
        ${['pending', 'in_progress', 'resolved', 'rejected', 'cancelled']
          .map((s) => `<option value="${s}" ${request.status === s ? 'selected' : ''}>${s.replace('_', ' ')}</option>`)
          .join('')}
      </select>
    </div>
    <div class="field"><label>Notes to tenant <span class="opt">(optional)</span></label>
      <textarea class="control" id="mrNotes" rows="3"></textarea>
    </div>
    <div class="alert" id="modalAlert"></div>
    <div class="flex mt-16" style="justify-content:flex-end;">
      <button class="btn btn-ghost" onclick="closeModal()">Cancel</button>
      <button class="btn btn-accent" id="saveRequestBtn">Save</button>
    </div>
  `);

  qs('#saveRequestBtn').addEventListener('click', async () => {
    const btn = qs('#saveRequestBtn');
    setLoading(btn, true, 'Saving…');
    try {
      await api.patch(`/admin/requests/${id}`, {
        status: qs('#mrStatus').value,
        adminNotes: qs('#mrNotes').value.trim() || undefined,
      });
      closeModal();
      loadRequests();
    } catch (err) {
      setLoading(btn, false);
      showAlert(qs('#modalAlert'), err.message);
    }
  });
}

/* --- Payments --- */
async function loadPayments() {
  const params = new URLSearchParams();
  const search = qs('#paymentSearch').value.trim();
  const status = qs('#paymentStatusFilter').value;
  if (search) params.set('search', search);
  if (status) params.set('status', status);

  const table = qs('#paymentsTable');
  table.innerHTML = `<tbody><tr><td class="empty-state">Loading…</td></tr></tbody>`;

  try {
    const { payments } = await api.get('/admin/payments?' + params.toString());
    paymentsCache = payments;
    if (!payments.length) {
      table.innerHTML = `<tbody><tr><td class="empty-state">No payments found.</td></tr></tbody>`;
      return;
    }
    table.innerHTML =
      `<thead><tr><th>Tenant</th><th>Room</th><th>Period</th><th>Total</th><th>Paid</th><th>Status</th><th>Due</th><th>Reference no.</th><th>Proof</th><th>Actions</th></tr></thead><tbody>` +
      payments
        .map(
          (p) => `<tr>
            <td>${escapeHtml(p.tenantName || '')}</td>
            <td>${escapeHtml(p.roomNumber || '—')}</td>
            <td>${formatDate(p.billingPeriod)}</td>
            <td>${formatMoney(p.totalAmount)}</td>
            <td>${formatMoney(p.amountPaid)}</td>
            <td>${badge(p.status)}${p.remarks ? `<div class="muted" style="font-size:.7rem;max-width:160px;">${escapeHtml(p.remarks)}</div>` : ''}</td>
            <td>${formatDate(p.dueDate)}</td>
            <td>${p.referenceNo ? escapeHtml(p.referenceNo) : '<span class="muted">—</span>'}</td>
            <td>${p.proofUrl ? `<a href="${p.proofUrl}" target="_blank" rel="noopener" style="color:var(--accent);font-weight:600;">View</a>` : '<span class="muted">—</span>'}</td>
            <td class="flex" style="gap:6px;flex-wrap:wrap;">
              ${['unpaid', 'overdue', 'rejected'].includes(p.status)
                ? `<button class="btn btn-ghost btn-sm" data-pay-remind="${p.id}">Remind</button>`
                : ''}
              ${
                p.status !== 'verified'
                  ? `<button class="btn btn-accent btn-sm" data-pay-verify="${p.id}">Verify</button>
                     <button class="btn btn-danger btn-sm" data-pay-reject="${p.id}">Reject</button>`
                  : `<button class="btn btn-danger btn-sm" data-pay-delete="${p.id}">Delete</button>`
              }
            </td>
          </tr>`
        )
        .join('') +
      '</tbody>';
  } catch (err) {
    table.innerHTML = `<tbody><tr><td class="empty-state">${escapeHtml(err.message)}</td></tr></tbody>`;
  }
}

/** Magtatanong muna bago burahin ang verified na payment. */
function deletePaymentModal(id) {
  const p = paymentsCache.find((x) => x.id === id);
  if (!p) {
    return alert('Could not find that payment. Refresh the table and try again.');
  }

  openModal(`
    <h3>Delete this payment record?</h3>
    <p class="muted" style="font-size:.84rem;margin-bottom:16px;">
      This permanently removes the approved payment from the system. It cannot be undone.
    </p>
    <div class="card" style="background:var(--surface-2);border:1px solid var(--border);margin-bottom:16px;">
      ${infoRow('Tenant', p.tenantName || '—')}
      ${infoRow('Room', p.roomNumber || '—')}
      ${infoRow('Period', formatDate(p.billingPeriod))}
      ${infoRow('Amount paid', formatMoney(p.amountPaid))}
      ${infoRow('Reference no.', p.referenceNo || '—')}
      ${infoRow('Status', p.status)}
    </div>
    <div class="alert" id="modalAlert"></div>
    <div class="flex mt-16" style="justify-content:flex-end;">
      <button class="btn btn-ghost" onclick="closeModal()">Cancel</button>
      <button class="btn btn-danger" id="dpBtn">Delete payment</button>
    </div>
  `);

  qs('#dpBtn').addEventListener('click', async () => {
    const btn = qs('#dpBtn');
    setLoading(btn, true, 'Deleting…');
    try {
      const data = await api.del(`/admin/payments/${id}`);
      closeModal();
      loadPayments();
      loadOverview();
      if (data && data.message) alert(data.message);
    } catch (err) {
      setLoading(btn, false);
      showAlert(qs('#modalAlert'), err.message);
    }
  });
}

/** Padalhan ng rent reminder sa email yung tenant ng isang bill. */
async function remindPayment(id) {
  if (!confirm('Send a rent reminder email to this tenant?')) return;
  try {
    const data = await api.post(`/admin/payments/${id}/remind`, {});
    alert((data && data.message) || 'Reminder sent.');
  } catch (err) {
    alert(err.message);
  }
}

function verifyPaymentModal(id) {
  openModal(`
    <h3>Verify payment</h3>
    <p class="muted" style="font-size:.84rem;margin-bottom:18px;">Confirm the amount received from the tenant.</p>
    <div class="field"><label>Amount paid (₱)</label><input class="control" type="number" step="0.01" id="vpAmount" placeholder="Leave blank to use the full amount" /></div>
    <div class="field"><label>Remarks <span class="opt">(optional)</span></label><input class="control" id="vpRemarks" /></div>
    <div class="alert" id="modalAlert"></div>
    <div class="flex mt-16" style="justify-content:flex-end;">
      <button class="btn btn-ghost" onclick="closeModal()">Cancel</button>
      <button class="btn btn-accent" id="vpBtn">Mark as verified</button>
    </div>
  `);
  qs('#vpBtn').addEventListener('click', async () => {
    const btn = qs('#vpBtn');
    const amount = qs('#vpAmount').value;
    setLoading(btn, true, 'Verifying…');
    try {
      await api.patch(`/admin/payments/${id}/verify`, {
        amountPaid: amount === '' ? undefined : Number(amount),
        remarks: qs('#vpRemarks').value.trim() || undefined,
      });
      closeModal();
      loadPayments();
    } catch (err) {
      setLoading(btn, false);
      showAlert(qs('#modalAlert'), err.message);
    }
  });
}

function rejectPaymentModal(id) {
  openModal(`
    <h3>Reject payment</h3>
    <div class="field"><label>Reason</label><textarea class="control" id="rpRemarks" rows="3" placeholder="e.g. Receipt is unreadable"></textarea></div>
    <div class="alert" id="modalAlert"></div>
    <div class="flex mt-16" style="justify-content:flex-end;">
      <button class="btn btn-ghost" onclick="closeModal()">Cancel</button>
      <button class="btn btn-danger" id="rpBtn">Reject payment</button>
    </div>
  `);
  qs('#rpBtn').addEventListener('click', async () => {
    const btn = qs('#rpBtn');
    setLoading(btn, true, 'Rejecting…');
    try {
      await api.patch(`/admin/payments/${id}/reject`, { remarks: qs('#rpRemarks').value.trim() || undefined });
      closeModal();
      loadPayments();
    } catch (err) {
      setLoading(btn, false);
      showAlert(qs('#modalAlert'), err.message);
    }
  });
}

/** Gumawa ng bill para sa isang tenant, POST /api/admin/payments. */
async function createBillModal(tenantId) {
  const today = new Date();
  const period = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-01`;
  const dueStr = new Date(today.getFullYear(), today.getMonth(), 10).toISOString().slice(0, 10);

  let tenants = [];
  try {
    tenants = (await api.get('/admin/tenants')).tenants || [];
  } catch (_) {
    /* kung wala, yung naka-select na tenant na lang */
  }

  if (!tenants.length && !tenantId) {
    return openModal(`<h3>Add a rent due</h3>
      <p class="muted">No tenants found. Add a tenant first.</p>
      <button class="btn btn-ghost btn-block mt-16" onclick="closeModal()">Close</button>`);
  }

  const options = tenants
    .map(
      (t) =>
        `<option value="${t.id}" ${t.id === tenantId ? 'selected' : ''}>${escapeHtml(
          (t.firstName + ' ' + t.lastName).trim()
        )}${t.roomNumber ? ' · Room ' + escapeHtml(t.roomNumber) : ''} — ${escapeHtml(t.email)}</option>`
    )
    .join('');

  const rentByIdAligned = {};
  tenants.forEach((t) => { rentByIdAligned[t.id] = t.monthlyRent == null ? null : Number(t.monthlyRent); });

  openModal(`
    <h3>Add a rent due</h3>
    <p class="muted" style="font-size:.84rem;margin-bottom:18px;">
      The bill appears on the tenant's dashboard and Pay page immediately. The rent follows the room's monthly rate.
    </p>
    <div class="field"><label>Tenant</label>
      <select class="control" id="cbTenant" ${tenantId ? 'disabled' : ''}>${options}</select>
    </div>
    <div class="row-2">
      <div class="field"><label>Billing period</label><input class="control" type="date" id="cbPeriod" value="${period}" /></div>
      <div class="field"><label>Due date</label><input class="control" type="date" id="cbDue" value="${dueStr}" /></div>
    </div>
    <div class="row-2">
      <div class="field"><label>Rent (₱) <span class="opt">(room rate)</span></label><input class="control" type="number" step="0.01" id="cbRent" placeholder="no room assigned" readonly /></div>
      <div class="field"><label>Utilities (₱)</label><input class="control" type="number" step="0.01" id="cbUtilities" value="0" /></div>
    </div>
    <div class="field"><label>Other charges (₱)</label><input class="control" type="number" step="0.01" id="cbOther" value="0" /></div>
    <div class="alert" id="modalAlert"></div>
    <div class="flex mt-16" style="justify-content:flex-end;">
      <button class="btn btn-ghost" onclick="closeModal()">Cancel</button>
      <button class="btn btn-accent" id="cbBtn">Create bill</button>
    </div>
  `);

  const syncRent = () => {
    const sel = qs('#cbTenant');
    const id = sel ? sel.value : tenantId;
    const rent = rentByIdAligned[id];
    const input = qs('#cbRent');
    if (!input) return;
    input.value = rent == null ? '' : Number(rent).toFixed(2);
  };
  syncRent();
  qs('#cbTenant')?.addEventListener('change', syncRent);

  qs('#cbBtn').addEventListener('click', async () => {
    const btn = qs('#cbBtn');
    const tid = qs('#cbTenant').value || tenantId;
    const periodVal = qs('#cbPeriod').value;
    const dueVal = qs('#cbDue').value;
    if (!tid) return showAlert(qs('#modalAlert'), 'Please choose a tenant.');
    if (!periodVal || !dueVal) return showAlert(qs('#modalAlert'), 'Billing period and due date are required.');

    setLoading(btn, true, 'Creating…');
    try {
      const data = await api.post('/admin/payments', {
        tenantId: tid,
        billingPeriod: periodVal,
        dueDate: dueVal,
        rentAmount: qs('#cbRent').value === '' ? undefined : Number(qs('#cbRent').value),
        utilitiesAmount: Number(qs('#cbUtilities').value) || 0,
        otherAmount: Number(qs('#cbOther').value) || 0,
      });
      closeModal();
      loadPayments();
      alert(data.message);
    } catch (err) {
      setLoading(btn, false);
      showAlert(qs('#modalAlert'), err.message);
    }
  });
}

function generateBillingModal() {
  const today = new Date();
  const period = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-01`;
  const due = new Date(today.getFullYear(), today.getMonth(), 10);
  const dueStr = due.toISOString().slice(0, 10);

  openModal(`
    <h3>Generate monthly bills</h3>
    <p class="muted" style="font-size:.84rem;margin-bottom:18px;">Creates a bill for every active tenant with an assigned room. Existing bills for the period are skipped.</p>
    <div class="row-2">
      <div class="field"><label>Billing period</label><input class="control" type="date" id="gbPeriod" value="${period}" /></div>
      <div class="field"><label>Due date</label><input class="control" type="date" id="gbDue" value="${dueStr}" /></div>
    </div>
    <div class="row-2">
      <div class="field"><label>Utilities (₱)</label><input class="control" type="number" step="0.01" id="gbUtilities" value="50" /></div>
      <div class="field"><label>Other (₱)</label><input class="control" type="number" step="0.01" id="gbOther" value="0" /></div>
    </div>
    <div class="alert" id="modalAlert"></div>
    <div class="flex mt-16" style="justify-content:flex-end;">
      <button class="btn btn-ghost" onclick="closeModal()">Cancel</button>
      <button class="btn btn-accent" id="gbBtn">Generate bills</button>
    </div>
  `);
  qs('#gbBtn').addEventListener('click', async () => {
    const btn = qs('#gbBtn');
    setLoading(btn, true, 'Generating…');
    try {
      const data = await api.post('/admin/payments/generate', {
        billingPeriod: qs('#gbPeriod').value,
        dueDate: qs('#gbDue').value,
        utilitiesAmount: Number(qs('#gbUtilities').value) || 0,
        otherAmount: Number(qs('#gbOther').value) || 0,
      });
      closeModal();
      loadPayments();
      alert(data.message);
    } catch (err) {
      setLoading(btn, false);
      showAlert(qs('#modalAlert'), err.message);
    }
  });
}

/* --- Rooms --- */
async function loadRooms(silent = false) {
  const table = qs('#roomsTable');
  if (!silent) table.innerHTML = `<tbody><tr><td class="empty-state">Loading…</td></tr></tbody>`;
  try {
    const { rooms } = await api.get('/admin/rooms');
    roomsCache = rooms;
    if (silent) return;
    table.innerHTML = rooms.length
      ? `<thead><tr><th>Room</th><th>Building</th><th>Floor</th><th>Rent</th><th>Occupants</th><th>Status</th><th></th></tr></thead><tbody>` +
        rooms
          .map(
            (r) => `<tr>
              <td>${escapeHtml(r.roomNumber)}</td>
              <td>${escapeHtml(r.building)}</td>
              <td>${r.floor ?? '—'}</td>
              <td>${formatMoney(r.monthlyRent)}</td>
              <td>${r.occupants} / ${r.capacity}</td>
              <td>${badge(r.status)}</td>
              <td><button class="btn btn-danger btn-sm" data-room-delete="${r.id}" data-room-label="${escapeHtml(r.building + ' · ' + r.roomNumber)}">Delete</button></td>
            </tr>`
          )
          .join('') +
        '</tbody>'
      : `<tbody><tr><td class="empty-state">No rooms yet.</td></tr></tbody>`;
  } catch (err) {
    table.innerHTML = `<tbody><tr><td class="empty-state">${escapeHtml(err.message)}</td></tr></tbody>`;
  }
}

async function addRoom() {
  const roomNumber = qs('#roomNumberInput').value.trim();
  const building = qs('#roomBuildingInput').value.trim() || 'North Hall';
  const monthlyRent = Number(qs('#roomRentInput').value) || 0;
  const floorVal = qs('#roomFloorInput').value;
  const capacityVal = qs('#roomCapacityInput').value;
  if (!roomNumber) return alert('Enter a room number.');

  try {
    await api.post('/admin/rooms', {
      roomNumber,
      building,
      monthlyRent,
      floor: floorVal === '' ? undefined : Number(floorVal),
      capacity: capacityVal === '' ? undefined : Number(capacityVal),
    });
    qs('#roomNumberInput').value = '';
    qs('#roomFloorInput').value = '';
    qs('#roomCapacityInput').value = '';
    qs('#roomRentInput').value = '';
    loadRooms();
  } catch (err) {
    alert(err.message);
  }
}

async function deleteRoom(id, label) {
  if (
    !confirm(
      `Delete room ${label}?\n\nThis cannot be undone. Historical payments keep their amounts but lose the room label.`
    )
  ) {
    return;
  }
  try {
    const data = await api.del(`/admin/rooms/${id}`);
    loadRooms();
    if (data && data.message) alert(data.message);
  } catch (err) {
    alert(err.message);
  }
}

/* --- Audit --- */
async function loadAuditLogs() {
  const table = qs('#auditTable');
  table.innerHTML = `<tbody><tr><td class="empty-state">Loading…</td></tr></tbody>`;
  try {
    const { logs } = await api.get('/admin/audit-logs');
    table.innerHTML = logs.length
      ? `<thead><tr><th>When</th><th>Admin</th><th>Action</th><th>Entity</th></tr></thead><tbody>` +
        logs
          .map(
            (l) => `<tr>
              <td>${formatDateTime(l.createdAt)}</td>
              <td>${escapeHtml(l.actor ? l.actor.name : 'system')}</td>
              <td>${escapeHtml(l.action)}</td>
              <td>${escapeHtml(l.entityType || '')}</td>
            </tr>`
          )
          .join('') +
        '</tbody>'
      : `<tbody><tr><td class="empty-state">No activity yet.</td></tr></tbody>`;
  } catch (err) {
    table.innerHTML = `<tbody><tr><td class="empty-state">${escapeHtml(err.message)}</td></tr></tbody>`;
  }
}

/* --- Small templates + event delegation --- */
function infoRow(k, v) {
  return `<div class="between" style="padding:9px 0;border-bottom:1px solid var(--border);font-size:.86rem;">
    <span class="muted">${escapeHtml(k)}</span><span style="font-weight:600;">${escapeHtml(v)}</span>
  </div>`;
}
function miniList(items, render) {
  if (!items || !items.length) return '<p class="muted" style="font-size:.82rem;">None yet.</p>';
  return `<div style="font-size:.84rem;line-height:2;">${items.map((i) => `<div>${render(i)}</div>`).join('')}</div>`;
}

/* --- Contract PDF upload --- */
let contractTenantId = null;

function uploadContract(id) {
  contractTenantId = id;
  const input = qs('#contractFile');
  input.value = '';
  input.click();
}

qs('#contractFile').addEventListener('change', async () => {
  const file = qs('#contractFile').files[0];
  if (!file || !contractTenantId) return;
  if (file.type !== 'application/pdf') {
    return alert('Please choose a PDF file.');
  }
  const form = new FormData();
  form.append('contract', file);
  try {
    await api.post(`/admin/tenants/${contractTenantId}/contract`, form);
    alert('Contract uploaded.');
    loadTenants();
  } catch (err) {
    alert(err.message || 'Could not upload the contract.');
  }
});

document.addEventListener('click', (e) => {
  const t = e.target.closest('button');
  if (!t) return;

  if (t.dataset.tenantContract) return uploadContract(t.dataset.tenantContract);
  if (t.dataset.tenantView) return showTenant(t.dataset.tenantView);
  if (t.dataset.tenantEdit) return editTenant(t.dataset.tenantEdit);
  if (t.dataset.tenantDeactivate) return deactivateTenant(t.dataset.tenantDeactivate);
  if (t.dataset.tenantDelete) return deleteTenantPermanently(t.dataset.tenantDelete);
  if (t.dataset.tenantBill) return createBillModal(t.dataset.tenantBill);
  if (t.dataset.roomDelete) return deleteRoom(t.dataset.roomDelete, t.dataset.roomLabel);
  if (t.dataset.requestManage) return manageRequest(t.dataset.requestManage);
  if (t.dataset.requestDelete) return deleteRequestModal(t.dataset.requestDelete);
  if (t.dataset.payRemind) return remindPayment(t.dataset.payRemind);
  if (t.dataset.payDelete) return deletePaymentModal(t.dataset.payDelete);
  if (t.dataset.payVerify) return verifyPaymentModal(t.dataset.payVerify);
  if (t.dataset.payReject) return rejectPaymentModal(t.dataset.payReject);
  if (t.dataset.verify) return verifyPaymentModal(t.dataset.verify);
});

/* --- Mga button sa toolbar --- */
qs('#addTenantBtn').addEventListener('click', addTenant);
qs('#refreshTenants').addEventListener('click', loadTenants);
qs('#tenantStatusFilter').addEventListener('change', loadTenants);
let tenantSearchTimer;
qs('#tenantSearch').addEventListener('input', () => {
  clearTimeout(tenantSearchTimer);
  tenantSearchTimer = setTimeout(loadTenants, 300);
});

qs('#refreshRequests').addEventListener('click', loadRequests);
qs('#requestStatusFilter').addEventListener('change', loadRequests);
qs('#requestTypeFilter').addEventListener('change', loadRequests);
let requestSearchTimer;
qs('#requestSearch').addEventListener('input', () => {
  clearTimeout(requestSearchTimer);
  requestSearchTimer = setTimeout(loadRequests, 300);
});

qs('#refreshPayments').addEventListener('click', loadPayments);
qs('#paymentStatusFilter').addEventListener('change', loadPayments);
qs('#addBillBtn').addEventListener('click', () => createBillModal());
qs('#generateBillingBtn').addEventListener('click', generateBillingModal);
let paymentSearchTimer;
qs('#paymentSearch').addEventListener('input', () => {
  clearTimeout(paymentSearchTimer);
  paymentSearchTimer = setTimeout(loadPayments, 300);
});

qs('#addRoomBtn').addEventListener('click', addRoom);
qs('#refreshRooms').addEventListener('click', () => loadRooms());
qs('#refreshAudit').addEventListener('click', loadAuditLogs);

/* --- Simula ng page --- */
if (adminUser) {
  loadRooms(true);
  loadOverview();
}
