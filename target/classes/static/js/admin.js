const token = localStorage.getItem('token');
if (!token) {
  window.location.href = '/';
}

const PAGE_SIZE = 10;
let currentPage = 0;
let totalPages = 0;
let currentSearch = '';

let historyAccountId = null;
let historyPage = 0;
let historyTotalPages = 0;

let busy = false; // blocks double clicks on Freeze / Unfreeze / Unlock

// ---- helper: call the API with the token ----
async function api(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + token,
      ...(options.headers || {})
    }
  });
  if (res.status === 401) {                 // not logged in, or token expired
    localStorage.removeItem('token');
    localStorage.removeItem('role');
    window.location.href = '/';
    throw new Error('Not logged in');
  }
  if (res.status === 403) {                 // logged in, but not an admin
    window.location.href = '/dashboard.html';
    throw new Error('Admins only');
  }
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.message || 'Something went wrong');
  }
  return data;
}

const money = (n) => '\u20B9' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 2 });

// Small helper: make a table cell with plain text (textContent = safe from XSS).
function cell(text) {
  const td = document.createElement('td');
  td.textContent = text;
  return td;
}

function badge(text, cls) {
  const span = document.createElement('span');
  span.className = 'badge ' + cls;
  span.textContent = text;
  return span;
}

function showMessage(text, isError) {
  const p = document.getElementById('message');
  p.className = isError ? 'error' : 'ok';
  p.textContent = text;
}

function messageRow(tbody, colspan, text) {
  tbody.innerHTML = '';
  const tr = document.createElement('tr');
  const td = document.createElement('td');
  td.colSpan = colspan;
  td.textContent = text;
  tr.appendChild(td);
  tbody.appendChild(tr);
}

// ---- accounts list ----
async function loadAccounts() {
  const body = document.getElementById('accountsBody');
  const params = new URLSearchParams({ page: currentPage, size: PAGE_SIZE });
  if (currentSearch) params.append('search', currentSearch);

  try {
    const data = await api('/api/v1/admin/accounts?' + params.toString());
    totalPages = data.totalPages;
    body.innerHTML = '';
    if (data.content.length === 0) {
      messageRow(body, 8, 'No accounts found');
    }
    for (const a of data.content) {
      body.appendChild(accountRow(a));
    }
    document.getElementById('pageInfo').textContent =
      'Page ' + (data.page + 1) + ' of ' + Math.max(data.totalPages, 1) + '  (' + data.totalElements + ' accounts)';
    document.getElementById('prevBtn').disabled = currentPage <= 0;
    document.getElementById('nextBtn').disabled = currentPage + 1 >= totalPages;
  } catch (e) {
    messageRow(body, 8, e.message);
  }
}

function accountRow(a) {
  const tr = document.createElement('tr');
  tr.appendChild(cell(a.accountNumber));
  tr.appendChild(cell(a.fullName));
  tr.appendChild(cell(a.email));
  tr.appendChild(cell(a.accountType));

  const statusTd = document.createElement('td');
  statusTd.appendChild(badge(a.status, a.status === 'FROZEN' ? 'frozen' : 'active'));
  tr.appendChild(statusTd);

  tr.appendChild(cell(money(a.balance)));

  const lockTd = document.createElement('td');
  if (a.locked) {
    lockTd.appendChild(badge('LOCKED', 'locked'));
  } else {
    lockTd.textContent = 'OK';
  }
  tr.appendChild(lockTd);

  const actionsTd = document.createElement('td');
  const actions = document.createElement('div');
  actions.className = 'actions';

  actions.appendChild(button('History', 'secondary', () => openHistory(a)));
  if (a.status === 'FROZEN') {
    actions.appendChild(button('Unfreeze', 'secondary', () => changeStatus(a, 'unfreeze')));
  } else {
    actions.appendChild(button('Freeze', 'danger', () => changeStatus(a, 'freeze')));
  }
  if (a.locked) {
    actions.appendChild(button('Unlock user', 'secondary', () => unlockUser(a)));
  }
  actionsTd.appendChild(actions);
  tr.appendChild(actionsTd);
  return tr;
}

function button(text, cls, onClick) {
  const b = document.createElement('button');
  b.textContent = text;
  b.className = cls;
  b.addEventListener('click', onClick);
  return b;
}

// ---- freeze / unfreeze / unlock ----
async function changeStatus(account, action) {
  if (busy) return;
  const word = (action === 'freeze') ? 'Freeze' : 'Unfreeze';
  if (!confirm(word + ' account ' + account.accountNumber + ' of ' + account.fullName + '?')) return;
  busy = true;
  try {
    await api('/api/v1/admin/accounts/' + account.accountId + '/' + action, { method: 'PUT' });
    showMessage(word + ' done for account ' + account.accountNumber, false);
    await loadAccounts();
  } catch (e) {
    showMessage(e.message, true);
  } finally {
    busy = false;
  }
}

async function unlockUser(account) {
  if (busy) return;
  if (!confirm('Unlock the login of ' + account.fullName + '?')) return;
  busy = true;
  try {
    await api('/api/v1/admin/users/' + account.userId + '/unlock', { method: 'PUT' });
    showMessage('User unlocked: ' + account.email, false);
    await loadAccounts();
  } catch (e) {
    showMessage(e.message, true);
  } finally {
    busy = false;
  }
}

// ---- history of one account ----
function openHistory(account) {
  historyAccountId = account.accountId;
  historyPage = 0;
  document.getElementById('historyTitle').textContent =
    'History of account ' + account.accountNumber + ' (' + account.fullName + ')';
  document.getElementById('historyCard').style.display = 'block';
  loadHistory();
  document.getElementById('historyCard').scrollIntoView({ behavior: 'smooth' });
}

async function loadHistory() {
  const body = document.getElementById('historyBody');
  const params = new URLSearchParams({ page: historyPage, size: PAGE_SIZE });
  try {
    const data = await api('/api/v1/admin/accounts/' + historyAccountId + '/transactions?' + params.toString());
    historyTotalPages = data.totalPages;
    body.innerHTML = '';
    if (data.content.length === 0) {
      messageRow(body, 6, 'No transactions found');
    }
    for (const t of data.content) {
      const tr = document.createElement('tr');
      tr.appendChild(cell(new Date(t.createdAt).toLocaleString('en-IN')));
      tr.appendChild(cell(t.type));
      tr.appendChild(cell(money(t.amount)));
      tr.appendChild(cell(money(t.balanceAfter)));
      tr.appendChild(cell(t.relatedAccount || ''));
      tr.appendChild(cell(t.remark || ''));
      body.appendChild(tr);
    }
    document.getElementById('hPageInfo').textContent =
      'Page ' + (data.page + 1) + ' of ' + Math.max(data.totalPages, 1);
    document.getElementById('hPrevBtn').disabled = historyPage <= 0;
    document.getElementById('hNextBtn').disabled = historyPage + 1 >= historyTotalPages;
  } catch (e) {
    messageRow(body, 6, e.message);
  }
}

// ---- buttons ----
document.getElementById('searchBtn').addEventListener('click', () => {
  currentSearch = document.getElementById('searchBox').value.trim();
  currentPage = 0;
  loadAccounts();
});
document.getElementById('searchBox').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') document.getElementById('searchBtn').click();
});
document.getElementById('clearBtn').addEventListener('click', () => {
  document.getElementById('searchBox').value = '';
  currentSearch = '';
  currentPage = 0;
  loadAccounts();
});
document.getElementById('prevBtn').addEventListener('click', () => { if (currentPage > 0) { currentPage--; loadAccounts(); } });
document.getElementById('nextBtn').addEventListener('click', () => { if (currentPage + 1 < totalPages) { currentPage++; loadAccounts(); } });
document.getElementById('hPrevBtn').addEventListener('click', () => { if (historyPage > 0) { historyPage--; loadHistory(); } });
document.getElementById('hNextBtn').addEventListener('click', () => { if (historyPage + 1 < historyTotalPages) { historyPage++; loadHistory(); } });
document.getElementById('closeHistoryBtn').addEventListener('click', () => {
  document.getElementById('historyCard').style.display = 'none';
});
document.getElementById('logoutBtn').addEventListener('click', () => {
  localStorage.removeItem('token');
  localStorage.removeItem('role');
  window.location.href = '/';
});

loadAccounts();
