'use strict';

const token = localStorage.getItem('token');
const role = localStorage.getItem('role');
if (!token) {
  window.location.replace('/');
} else if (role === 'ADMIN') {
  window.location.replace('/admin.html');
}

let currentPage = 0;
const PAGE_SIZE = 10;
let totalPages = 0;
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

function setMessage(elementId, message, kind = 'info') {
  const element = document.getElementById(elementId);
  element.textContent = message;
  element.className = 'feedback form-feedback ' + kind;
  element.hidden = !message;
}

function addAccountMeta(container, label, value, extraClass = '') {
  const item = document.createElement('div');
  item.className = 'account-meta-item';
  const labelNode = document.createElement('span');
  labelNode.className = 'account-meta-label';
  labelNode.textContent = label;
  const valueNode = document.createElement('span');
  valueNode.className = 'account-meta-value' + (extraClass ? ' ' + extraClass : '');
  valueNode.textContent = value == null || value === '' ? 'Not available' : String(value);
  item.append(labelNode, valueNode);
  container.appendChild(item);
}

async function loadAccount() {
  const box = document.getElementById('accountInfo');
  box.replaceChildren();
  const loading = document.createElement('div');
  loading.className = 'loading-state';
  loading.textContent = 'Loading account information…';
  box.appendChild(loading);

  try {
    const account = await api('/api/v1/accounts/me');
    box.replaceChildren();

    const balance = document.createElement('div');
    balance.className = 'account-balance';
    balance.textContent = money(account.balance);
    box.appendChild(balance);

    const meta = document.createElement('div');
    meta.className = 'account-meta-grid';
    addAccountMeta(meta, 'Account number', account.accountNumber);
    addAccountMeta(meta, 'Account type', account.accountType);
    addAccountMeta(meta, 'Account status', account.status,
      'account-status' + (account.status === 'FROZEN' ? ' is-frozen' : ''));
    box.appendChild(meta);
  } catch (error) {
    box.replaceChildren();
    const state = document.createElement('div');
    state.className = 'loading-state';
    state.textContent = error.message;
    box.appendChild(state);
  }
}

function formatDate(value) {
  if (!value) return 'Not available';
  const datePart = String(value).slice(0, 10);
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(datePart);
  if (!match) return 'Not available';
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return Number.isNaN(date.getTime())
    ? 'Not available'
    : date.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

function addProfileField(container, label, value) {
  const item = document.createElement('div');
  item.className = 'profile-detail-item';
  const labelNode = document.createElement('span');
  labelNode.className = 'profile-detail-label';
  labelNode.textContent = label;
  const valueNode = document.createElement('strong');
  valueNode.className = 'profile-detail-value';
  valueNode.textContent = value == null || String(value).trim() === '' ? 'Not available' : String(value);
  item.append(labelNode, valueNode);
  container.appendChild(item);
}

function setProfileError(message) {
  const card = document.getElementById('profileCard');
  const personal = document.getElementById('profileInfo');
  const account = document.getElementById('profileAccountInfo');
  card.setAttribute('aria-busy', 'false');
  personal.replaceChildren();
  account.replaceChildren();
  const state = document.createElement('p');
  state.className = 'profile-error';
  state.textContent = message || 'Your profile could not be loaded. Please refresh and try again.';
  personal.appendChild(state);
  account.appendChild(state.cloneNode(true));
}

async function loadProfile() {
  const card = document.getElementById('profileCard');
  const personal = document.getElementById('profileInfo');
  const accountBox = document.getElementById('profileAccountInfo');
  try {
    const [profile, account] = await Promise.all([
      api('/api/v1/auth/me'),
      api('/api/v1/accounts/me')
    ]);

    document.getElementById('profileName').textContent = profile.fullName || 'SecureBank customer';
    document.getElementById('profileEmail').textContent = profile.email || 'Email not available';
    const initials = String(profile.fullName || 'SB')
      .trim().split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part.charAt(0).toUpperCase()).join('');
    document.getElementById('profileAvatar').textContent = initials || 'SB';

    personal.replaceChildren();
    addProfileField(personal, 'Full name', profile.fullName);
    addProfileField(personal, 'Email address', profile.email);
    addProfileField(personal, 'Phone number', profile.phone);
    addProfileField(personal, 'Date of birth', formatDate(profile.dateOfBirth));
    addProfileField(personal, 'Customer since', formatDate(profile.memberSince));

    accountBox.replaceChildren();
    addProfileField(accountBox, 'Account number', account.accountNumber);
    addProfileField(accountBox, 'Account type', account.accountType);
    addProfileField(accountBox, 'Account status', account.status);
    card.setAttribute('aria-busy', 'false');
  } catch (error) {
    setProfileError(error.message);
  }
}

function setMoneyButtons(disabled) {
  document.getElementById('depositBtn').disabled = disabled;
  document.getElementById('withdrawBtn').disabled = disabled;
}

document.getElementById('moneyForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (busy) return;
  const action = event.submitter?.dataset.action || 'deposit';
  const amountField = document.getElementById('amount');
  if (!amountField.reportValidity()) return;

  busy = true;
  setMoneyButtons(true);
  setMessage('message', 'Processing your request…', 'info');
  try {
    const body = { amount: Number(amountField.value), remark: document.getElementById('remark').value.trim() };
    const transaction = await api('/api/v1/transactions/' + action, {
      method: 'POST', body: JSON.stringify(body)
    });
    setMessage('message', (action === 'deposit' ? 'Deposit' : 'Withdrawal') + ' completed. New balance: ' + money(transaction.balanceAfter), 'ok');
    document.getElementById('moneyForm').reset();
    await Promise.all([loadAccount(), reloadHistory()]);
  } catch (error) {
    setMessage('message', error.message, 'error');
  } finally {
    busy = false;
    setMoneyButtons(false);
  }
});

function openTransferConfirmation(amount, accountNumber) {
  const dialog = document.getElementById('transferConfirmDialog');
  document.getElementById('confirmTransferAmount').textContent = money(amount);
  document.getElementById('confirmTransferAccount').textContent = accountNumber;
  return new Promise((resolve) => {
    const confirmButton = document.getElementById('confirmTransferBtn');
    const cancelButton = document.getElementById('cancelTransferBtn');
    const close = (accepted) => {
      dialog.close();
      confirmButton.removeEventListener('click', confirm);
      cancelButton.removeEventListener('click', cancel);
      dialog.removeEventListener('cancel', cancel);
      resolve(accepted);
    };
    const confirm = () => close(true);
    const cancel = () => close(false);
    confirmButton.addEventListener('click', confirm);
    cancelButton.addEventListener('click', cancel);
    dialog.addEventListener('cancel', cancel, { once: true });
    dialog.showModal();
  });
}

document.getElementById('transferForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (busy) return;
  const form = document.getElementById('transferForm');
  if (!form.reportValidity()) return;
  const toAccountNumber = document.getElementById('toAccount').value.trim();
  const amount = Number(document.getElementById('tAmount').value);
  if (!Number.isFinite(amount) || amount <= 0) {
    setMessage('transferMessage', 'Enter a valid transfer amount.', 'error');
    return;
  }

  busy = true;
  const button = document.getElementById('transferBtn');
  button.disabled = true;
  try {
    const approved = await openTransferConfirmation(amount, toAccountNumber);
    if (!approved) return;

    setMessage('transferMessage', 'Sending transfer…', 'info');
    const body = {
      toAccountNumber,
      amount,
      remark: document.getElementById('tRemark').value.trim()
    };
    const transaction = await api('/api/v1/transactions/transfer', {
      method: 'POST', body: JSON.stringify(body)
    });
    setMessage('transferMessage', 'Transfer completed. New balance: ' + money(transaction.balanceAfter), 'ok');
    form.reset();
    await Promise.all([loadAccount(), reloadHistory()]);
  } catch (error) {
    setMessage('transferMessage', error.message, 'error');
  } finally {
    busy = false;
    button.disabled = false;
  }
});

document.getElementById('passwordForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (busy) return;
  const form = document.getElementById('passwordForm');
  if (!form.reportValidity()) return;
  busy = true;
  const button = document.getElementById('passwordBtn');
  button.disabled = true;
  setMessage('passwordMessage', 'Updating password…', 'info');
  try {
    const response = await api('/api/v1/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({
        oldPassword: document.getElementById('oldPassword').value,
        newPassword: document.getElementById('newPassword').value
      })
    });
    setMessage('passwordMessage', response.message || 'Password updated successfully.', 'ok');
    form.reset();
  } catch (error) {
    setMessage('passwordMessage', error.message, 'error');
  } finally {
    busy = false;
    button.disabled = false;
  }
});

async function loadHistory() {
  const body = document.getElementById('historyBody');
  body.replaceChildren();
  const row = document.createElement('tr');
  const cell = document.createElement('td');
  cell.colSpan = 6;
  const state = document.createElement('div');
  state.className = 'loading-state';
  const spinner = document.createElement('span');
  spinner.className = 'loading-dot';
  spinner.setAttribute('aria-hidden', 'true');
  state.append(spinner, document.createTextNode('Loading transactions…'));
  cell.appendChild(state);
  row.appendChild(cell);
  body.appendChild(row);

  const params = new URLSearchParams({ page: currentPage, size: PAGE_SIZE });
  const type = document.getElementById('fType').value;
  const from = document.getElementById('fFrom').value;
  const to = document.getElementById('fTo').value;
  if (from && to && from > to) {
    body.replaceChildren();
    addTableMessage(body, 'Choose a start date that is on or before the end date.');
    updatePager(0, 0);
    return;
  }
  if (type) params.append('type', type);
  if (from) params.append('from', from);
  if (to) params.append('to', to);

  try {
    const data = await api('/api/v1/transactions/me?' + params.toString());
    totalPages = Number(data.totalPages) || 0;
    body.replaceChildren();
    if (!Array.isArray(data.content) || data.content.length === 0) {
      addTableMessage(body, 'No transactions match these filters.');
    } else {
      for (const transaction of data.content) {
        const tr = document.createElement('tr');
        const values = [
          transaction.createdAt ? new Date(transaction.createdAt).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' }) : 'Not available',
          transaction.type || 'Not available',
          money(transaction.amount),
          money(transaction.balanceAfter),
          transaction.relatedAccount || '—',
          transaction.remark || '—'
        ];
        for (const value of values) {
          const td = document.createElement('td');
          td.textContent = String(value);
          tr.appendChild(td);
        }
        body.appendChild(tr);
      }
    }
    updatePager(data.page, totalPages);
  } catch (error) {
    body.replaceChildren();
    addTableMessage(body, error.message);
    updatePager(0, 0);
  }
}

function addTableMessage(body, message) {
  const tr = document.createElement('tr');
  const td = document.createElement('td');
  td.colSpan = 6;
  td.className = 'table-message';
  td.textContent = message;
  tr.appendChild(td);
  body.appendChild(tr);
}

function updatePager(page, pages) {
  document.getElementById('pageInfo').textContent = 'Page ' + (Number(page || 0) + 1) + ' of ' + Math.max(Number(pages) || 0, 1);
  document.getElementById('prevBtn').disabled = currentPage <= 0;
  document.getElementById('nextBtn').disabled = currentPage + 1 >= totalPages;
}

async function reloadHistory() {
  currentPage = 0;
  await loadHistory();
}

document.getElementById('applyFilters').addEventListener('click', () => { currentPage = 0; loadHistory(); });
document.getElementById('prevBtn').addEventListener('click', () => {
  if (currentPage > 0) { currentPage -= 1; loadHistory(); }
});
document.getElementById('nextBtn').addEventListener('click', () => {
  if (currentPage + 1 < totalPages) { currentPage += 1; loadHistory(); }
});
document.getElementById('logoutBtn').addEventListener('click', () => {
  localStorage.removeItem('token');
  localStorage.removeItem('role');
  window.location.replace('/');
});

// Sidebar navigation is visual only; this keeps the current section clear as users scroll.
const navLinks = Array.from(document.querySelectorAll('.sidebar-nav .side-link'));
const sectionObserver = ('IntersectionObserver' in window)
  ? new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          navLinks.forEach((link) => link.classList.toggle('is-current', link.hash === '#' + entry.target.id));
        }
      }
    }, { rootMargin: '-20% 0px -65% 0px' })
  : null;
if (sectionObserver) {
  ['overview', 'profile', 'money', 'transfer', 'history', 'security'].forEach((id) => {
    const section = document.getElementById(id);
    if (section) sectionObserver.observe(section);
  });
}

loadAccount();
loadProfile();
loadHistory();
