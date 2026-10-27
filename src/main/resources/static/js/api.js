/* --- StayTrack — API client + tiny helpers shared by every page --- */

const API_BASE = '/api';
const TOKEN_KEY = 'staytrack_token';
const USER_KEY = 'staytrack_user';

const Store = {
  get token() {
    return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY);
  },
  get user() {
    const raw = localStorage.getItem(USER_KEY) || sessionStorage.getItem(USER_KEY);
    try {
      return JSON.parse(raw || 'null');
    } catch (_) {
      return null;
    }
  },

  /** I-save yung session; localStorage kung remember, sessionStorage kung hindi. */
  save({ token, user }, remember = true) {
    const primary = remember ? localStorage : sessionStorage;
    const secondary = remember ? sessionStorage : localStorage;

    if (token) {
      primary.setItem(TOKEN_KEY, token);
      secondary.removeItem(TOKEN_KEY);
    }
    if (user) {
      primary.setItem(USER_KEY, JSON.stringify(user));
      secondary.removeItem(USER_KEY);
    }
  },

  clear() {
    for (const storage of [localStorage, sessionStorage]) {
      storage.removeItem(TOKEN_KEY);
      storage.removeItem(USER_KEY);
    }
  },

  /** Idagdag yung bagong fields sa cached user, walang lilipat ng storage. */
  updateUser(partial) {
    const storage = localStorage.getItem(USER_KEY) ? localStorage : sessionStorage;
    const raw = storage.getItem(USER_KEY);
    if (!raw) return;
    try {
      storage.setItem(USER_KEY, JSON.stringify({ ...JSON.parse(raw), ...partial }));
    } catch (_) {
      /* balewalain kung sira yung cache */
    }
  },
};

class ApiError extends Error {
  constructor(message, status, details) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

/** Pangunahing helper sa request: JSON, token, file upload, at 401. */
async function apiFetch(path, { method = 'GET', body, auth = true, raw = false } = {}) {
  const headers = {};
  let payload = body;

  if (auth && Store.token) headers.Authorization = `Bearer ${Store.token}`;

  if (body instanceof FormData) {
    payload = body; // let the browser set multipart boundaries
  } else if (body !== undefined && body !== null) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  let res;
  try {
    res = await fetch(API_BASE + path, { method, headers, body: payload });
  } catch (_) {
    throw new ApiError('Cannot reach the server. Is the backend running?', 0);
  }

  // Expired o invalid na session, burahin tapos balik sa login.
  if (res.status === 401 && auth) {
    Store.clear();
    if (!/login\.html$/.test(location.pathname)) {
      location.href = '/login.html';
    }
    throw new ApiError('Session expired', 401);
  }

  if (raw) {
    if (!res.ok) throw new ApiError('Request failed', res.status);
    return res;
  }

  let data = null;
  const text = await res.text();
  if (text) {
    try {
      data = JSON.parse(text);
    } catch (_) {
      data = { raw: text };
    }
  }

  if (!res.ok) {
    throw new ApiError((data && data.error) || `Request failed (${res.status})`, res.status, data && data.details);
  }
  return data;
}

const api = {
  get: (path, opts) => apiFetch(path, { ...opts, method: 'GET' }),
  post: (path, body, opts) => apiFetch(path, { ...opts, method: 'POST', body }),
  patch: (path, body, opts) => apiFetch(path, { ...opts, method: 'PATCH', body }),
  del: (path, opts) => apiFetch(path, { ...opts, method: 'DELETE' }),
};

/* --- Mga UI helper --- */

function qs(sel, root = document) {
  return root.querySelector(sel);
}
function qsa(sel, root = document) {
  return Array.from(root.querySelectorAll(sel));
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function formatMoney(value) {
  return '₱' + Number(value || 0).toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDate(value) {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
}

function formatDateTime(value) {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' });
}

function badge(status) {
  const map = {
    pending: 'badge-pending',
    in_progress: 'badge-progress',
    resolved: 'badge-resolved',
    rejected: 'badge-rejected',
    cancelled: 'badge-neutral',
    unpaid: 'badge-unpaid',
    submitted: 'badge-submitted',
    verified: 'badge-verified',
    overdue: 'badge-overdue',
    active: 'badge-active',
    suspended: 'badge-suspended',
  };
  const label = String(status || '').replace(/_/g, ' ');
  return `<span class="badge ${map[status] || 'badge-neutral'}">${escapeHtml(label)}</span>`;
}

function showAlert(selector, message, type = 'error') {
  const el = typeof selector === 'string' ? qs(selector) : selector;
  if (!el) return;
  el.textContent = message;
  el.className = `alert alert-${type} show`;
  if (type !== 'info') {
    clearTimeout(el._t);
    el._t = setTimeout(() => el.classList.remove('show'), 6000);
  }
}

function hideAlert(selector) {
  const el = typeof selector === 'string' ? qs(selector) : selector;
  if (el) el.classList.remove('show');
}

function setLoading(button, loading, labelWhenLoading = 'Please wait…') {
  if (!button) return;
  if (loading) {
    button.dataset.label = button.textContent;
    button.disabled = true;
    button.innerHTML = `<span class="spinner"></span> ${labelWhenLoading}`;
  } else {
    button.disabled = false;
    button.textContent = button.dataset.label || button.textContent;
  }
}

/* --- Theme --- */
(function initTheme() {
  const saved = localStorage.getItem('staytrack_theme');
  if (saved) document.documentElement.setAttribute('data-theme', saved);
})();

/** Helper para i-redirect kung kailangan o bawal ang session. */
function requireSession(expectedRole) {
  const user = Store.user;
  if (!Store.token || !user) {
    location.href = '/login.html';
    return null;
  }
  if (expectedRole && user.role !== expectedRole) {
    location.href = user.role === 'admin' ? '/admin.html' : '/index.html';
    return null;
  }
  return user;
}

function logout() {
  Store.clear();
  location.href = '/login.html';
}
