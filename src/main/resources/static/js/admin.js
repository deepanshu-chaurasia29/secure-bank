'use strict';

const token = localStorage.getItem('token');
const role = localStorage.getItem('role');
if (!token) {
  window.location.replace('/');
} else if (role === 'CUSTOMER') {
  window.location.replace('/dashboard.html');
}

const PAGE_SIZE = 10;
let currentPage = 0;
let totalPages = 0;
let currentSearch = '';
let historyAccountId = null;
let historyPage = 0;
let historyTotalPages = 0;
let busy = false;

async function readResponse(response) {
  try {
    return await response.json();
  } catch (_) {
    return {};
  }
}

async function api(path, options = {}) {
  let response;
  try {
    response = await fetch(path, {
      ...options,
      headers: {
        Accept: 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        Authorization: 'Bearer ' + token,
        ...(options.headers || {})
      }
    });
  } catch (_) {
    throw new Error('Could not connect to SecureBank. Check your connection and try again.');
  }
  if (response.status === 401) {
    localStorage.removeItem('token');
    localStorage.removeItem('role');
    window.location.replace('/');
    throw new Error('Your session has expired. Please sign in again.');
  }
  if (response.status === 403) {
    window.location.replace('/dashboard.html');
    throw new Error('Administrator access is required.');
  }
  const data = await readResponse(response);
  if (!response.ok) {
    throw new Error(data.message || data.error || 'The request could not be completed. Please try again.');
  }
  return data;
}

const money = (value) => {
  const amount = Number(value);
  return Number.isFinite(amount)
    ? '\u20B9' + amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : 'Not available';
};

function cell(value) {
  const td = document.createElement('td');
  td.textContent = value == null || value === '' ? '—' : String(value);
  return td;
}

function badge(text, cls) {
  const span = document.createElement('span');
  span.className = 'badge ' + cls;
  span.textContent = text;
  return span;
}

function showMessage(message, isError = false) {
  const element = document.getElementById('message');
  element.textContent = message;
  element.className = 'feedback global-feedback ' + (isError ? 'error' : 'ok');
  element.hidden = !message;
}

function messageRow(tbody, colspan, message) {
  tbody.replaceChildren();
  const tr = document.createElement('tr');
  const td = document.createElement('td');
  td.colSpan = colspan;
  td.className = 'table-message';
  td.textContent = message;
  tr.appendChild(td);
  tbody.appendChild(tr);
}

function loadingRow(tbody, colspan, message) {
  tbody.replaceChildren();
  const tr = document.createElement('tr');
  const td = document.createElement('td');
  td.colSpan = colspan;
  const state = document.createElement('div');
  state.className = 'loading-state';
  const spinner = document.createElement('span');
  spinner.className = 'loading-dot';
  spinner.setAttribute('aria-hidden', 'true');
  state.append(spinner, document.createTextNode(message));
  td.appendChild(state);
  tr.appendChild(td);
  tbody.appendChild(tr);
}

async function loadAccounts() {
  const body = document.getElementById('accountsBody');
  loadingRow(body, 8, 'Loading account records…');
  const params = new URLSearchParams({ page: currentPage, size: PAGE_SIZE });
  if (currentSearch) params.append('search', currentSearch);

  try {
    const data = await api('/api/v1/admin/accounts?' + params.toString());
    const content = Array.isArray(data.content) ? data.content : [];
    totalPages = Number(data.totalPages) || 0;
    body.replaceChildren();
    if (content.length === 0) {
      messageRow(body, 8, currentSearch ? 'No accounts match your search.' : 'No customer accounts found.');
    } else {
      for (const account of content) body.appendChild(accountRow(account));
    }
    document.getElementById('pageInfo').textContent =
      'Page ' + ((Number(data.page) || 0) + 1) + ' of ' + Math.max(totalPages, 1) + ' · ' + (Number(data.totalElements) || 0) + ' accounts';
    document.getElementById('prevBtn').disabled = currentPage <= 0;
    document.getElementById('nextBtn').disabled = currentPage + 1 >= totalPages;
  } catch (error) {
    messageRow(body, 8, error.message);
    document.getElementById('pageInfo').textContent = 'Account records could not be loaded.';
    document.getElementById('prevBtn').disabled = true;
    document.getElementById('nextBtn').disabled = true;
  }
}

function accountRow(account) {
  const tr = document.createElement('tr');
  tr.appendChild(cell(account.accountNumber));
  tr.appendChild(cell(account.fullName));
  tr.appendChild(cell(account.email));
  tr.appendChild(cell(account.accountType));

  const status = document.createElement('td');
  const isFrozen = account.status === 'FROZEN';
  status.appendChild(badge(account.status || 'UNKNOWN', isFrozen ? 'frozen' : 'active'));
  tr.appendChild(status);
  tr.appendChild(cell(money(account.balance)));

  const lockStatus = document.createElement('td');
  lockStatus.appendChild(account.locked ? badge('LOCKED', 'locked') : badge('AVAILABLE', 'active'));
  tr.appendChild(lockStatus);

  const actionsCell = document.createElement('td');
  const actions = document.createElement('div');
  actions.className = 'actions';
  actions.appendChild(makeButton('History', 'btn btn-small btn-outline', () => openHistory(account)));
  if (isFrozen) {
    actions.appendChild(makeButton('Unfreeze', 'btn btn-small btn-outline', () => changeStatus(account, 'unfreeze')));
  } else {
    actions.appendChild(makeButton('Freeze', 'btn btn-small btn-danger', () => changeStatus(account, 'freeze')));
  }
  if (account.locked) {
    actions.appendChild(makeButton('Unlock user', 'btn btn-small btn-outline', () => unlockUser(account)));
  }
  actionsCell.appendChild(actions);
  tr.appendChild(actionsCell);
  return tr;
}

function makeButton(text, className, onClick) {
  const button = document.createElement('button');
  button.type = 'button';
  button.textContent = text;
  button.className = className;
  button.addEventListener('click', onClick);
  return button;
}

async function changeStatus(account, action) {
  if (busy) return;
  const actionWord = action === 'freeze' ? 'freeze' : 'unfreeze';
  const confirmed = window.confirm('Are you sure you want to ' + actionWord + ' account ' + account.accountNumber + '?');
  if (!confirmed) return;

  busy = true;
  showMessage('Updating account status…');
  try {
    await api('/api/v1/admin/accounts/' + encodeURIComponent(account.accountId) + '/' + action, { method: 'PUT' });
    showMessage('Account ' + account.accountNumber + ' was ' + (action === 'freeze' ? 'frozen' : 'unfrozen') + '.');
    await loadAccounts();
  } catch (error) {
    showMessage(error.message, true);
  } finally {
    busy = false;
  }
}

async function unlockUser(account) {
  if (busy) return;
  if (!window.confirm('Unlock sign-in for ' + account.email + '?')) return;
  busy = true;
  showMessage('Updating sign-in status…');
  try {
    await api('/api/v1/admin/users/' + encodeURIComponent(account.userId) + '/unlock', { method: 'PUT' });
    showMessage('Sign-in was unlocked for ' + account.email + '.');
    await loadAccounts();
  } catch (error) {
    showMessage(error.message, true);
  } finally {
    busy = false;
  }
}

function openHistory(account) {
  historyAccountId = account.accountId;
  historyPage = 0;
  document.getElementById('historyTitle').textContent = 'History of account ' + account.accountNumber;
  document.getElementById('historyCard').hidden = false;
  loadHistory();
  document.getElementById('historyCard').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function loadHistory() {
  if (historyAccountId == null) return;
  const body = document.getElementById('historyBody');
  loadingRow(body, 6, 'Loading transactions…');
  const params = new URLSearchParams({ page: historyPage, size: PAGE_SIZE });

  try {
    const data = await api('/api/v1/admin/accounts/' + encodeURIComponent(historyAccountId) + '/transactions?' + params.toString());
    const content = Array.isArray(data.content) ? data.content : [];
    historyTotalPages = Number(data.totalPages) || 0;
    body.replaceChildren();
    if (content.length === 0) {
      messageRow(body, 6, 'No transactions found for this account.');
    } else {
      for (const transaction of content) {
        const tr = document.createElement('tr');
        tr.appendChild(cell(transaction.createdAt
          ? new Date(transaction.createdAt).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' })
          : 'Not available'));
        tr.appendChild(cell(transaction.type));
        tr.appendChild(cell(money(transaction.amount)));
        tr.appendChild(cell(money(transaction.balanceAfter)));
        tr.appendChild(cell(transaction.relatedAccount));
        tr.appendChild(cell(transaction.remark));
        body.appendChild(tr);
      }
    }
    document.getElementById('hPageInfo').textContent =
      'Page ' + ((Number(data.page) || 0) + 1) + ' of ' + Math.max(historyTotalPages, 1);
    document.getElementById('hPrevBtn').disabled = historyPage <= 0;
    document.getElementById('hNextBtn').disabled = historyPage + 1 >= historyTotalPages;
  } catch (error) {
    messageRow(body, 6, error.message);
    document.getElementById('hPageInfo').textContent = 'History could not be loaded.';
    document.getElementById('hPrevBtn').disabled = true;
    document.getElementById('hNextBtn').disabled = true;
  }
}

document.getElementById('searchBtn').addEventListener('click', () => {
  currentSearch = document.getElementById('searchBox').value.trim();
  currentPage = 0;
  loadAccounts();
});
document.getElementById('searchBox').addEventListener('keydown', (event) => {
  if (event.key === 'Enter') {
    event.preventDefault();
    document.getElementById('searchBtn').click();
  }
});
document.getElementById('clearBtn').addEventListener('click', () => {
  document.getElementById('searchBox').value = '';
  currentSearch = '';
  currentPage = 0;
  loadAccounts();
});
document.getElementById('prevBtn').addEventListener('click', () => {
  if (currentPage > 0) { currentPage -= 1; loadAccounts(); }
});
document.getElementById('nextBtn').addEventListener('click', () => {
  if (currentPage + 1 < totalPages) { currentPage += 1; loadAccounts(); }
});
document.getElementById('hPrevBtn').addEventListener('click', () => {
  if (historyPage > 0) { historyPage -= 1; loadHistory(); }
});
document.getElementById('hNextBtn').addEventListener('click', () => {
  if (historyPage + 1 < historyTotalPages) { historyPage += 1; loadHistory(); }
});
document.getElementById('closeHistoryBtn').addEventListener('click', () => {
  document.getElementById('historyCard').hidden = true;
});
document.getElementById('logoutBtn').addEventListener('click', () => {
  localStorage.removeItem('token');
  localStorage.removeItem('role');
  window.location.replace('/');
});
document.getElementById('focusSearchLink').addEventListener('click', () => {
  window.setTimeout(() => document.getElementById('searchBox').focus(), 50);
});

loadAccounts();
