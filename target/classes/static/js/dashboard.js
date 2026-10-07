const token = localStorage.getItem('token');
if (!token) {
  window.location.href = '/';
}

let currentPage = 0;
const PAGE_SIZE = 10;
let totalPages = 0;
let busy = false; // blocks a double click from sending two requests (FR-C8)

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
  if (res.status === 401) {          // token missing / expired
    localStorage.removeItem('token');
    window.location.href = '/';
    throw new Error('Not logged in');
  }
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.message || 'Something went wrong');
  }
  return data;
}

const money = (n) => '\u20B9' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 2 });

// ---- account card ----
async function loadAccount() {
  const box = document.getElementById('accountInfo');
  try {
    const a = await api('/api/v1/accounts/me');
    box.innerHTML = '';
    const lines = [
      ['Account number', a.accountNumber],
      ['Type', a.accountType],
      ['Status', a.status],
      ['Balance', money(a.balance)]
    ];
    for (const [label, value] of lines) {
      const p = document.createElement('p');
      const b = document.createElement('b');
      b.textContent = label + ': ';
      p.appendChild(b);
      p.appendChild(document.createTextNode(value));   // textContent style = safe from XSS
      box.appendChild(p);
    }
  } catch (e) {
    box.textContent = e.message;
  }
}

// ---- deposit / withdraw ----
document.getElementById('moneyForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  if (busy) return;
  const action = e.submitter.dataset.action;     // "deposit" or "withdraw"
  const msg = document.getElementById('message');
  busy = true;
  setButtons(true);
  try {
    const body = {
      amount: document.getElementById('amount').value,
      remark: document.getElementById('remark').value
    };
    // the API expects a number, not text
    body.amount = Number(body.amount);
    const t = await api('/api/v1/transactions/' + action, { method: 'POST', body: JSON.stringify(body) });
    msg.className = 'ok';
    msg.textContent = action + ' successful. New balance: ' + money(t.balanceAfter);
    document.getElementById('moneyForm').reset();
    await loadAccount();
    currentPage = 0;
    await loadHistory();
  } catch (err) {
    msg.className = 'error';
    msg.textContent = err.message;
  } finally {
    busy = false;
    setButtons(false);
  }
});

function setButtons(disabled) {
  document.getElementById('depositBtn').disabled = disabled;
  document.getElementById('withdrawBtn').disabled = disabled;
}

// ---- transfer ----
document.getElementById('transferForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  if (busy) return;
  const msg = document.getElementById('transferMessage');
  const toAccountNumber = document.getElementById('toAccount').value.trim();
  const amount = Number(document.getElementById('tAmount').value);

  // A last chance to cancel, because a transfer cannot be undone by the customer.
  if (!confirm('Send ' + money(amount) + ' to account ' + toAccountNumber + '?')) return;

  busy = true;
  document.getElementById('transferBtn').disabled = true;
  try {
    const body = {
      toAccountNumber: toAccountNumber,
      amount: amount,
      remark: document.getElementById('tRemark').value
    };
    const t = await api('/api/v1/transactions/transfer', { method: 'POST', body: JSON.stringify(body) });
    msg.className = 'ok';
    msg.textContent = 'Transfer successful. New balance: ' + money(t.balanceAfter);
    document.getElementById('transferForm').reset();
    await loadAccount();
    currentPage = 0;
    await loadHistory();
  } catch (err) {
    msg.className = 'error';
    msg.textContent = err.message;
  } finally {
    busy = false;
    document.getElementById('transferBtn').disabled = false;
  }
});

// ---- history ----
async function loadHistory() {
  const body = document.getElementById('historyBody');
  const params = new URLSearchParams({ page: currentPage, size: PAGE_SIZE });
  const type = document.getElementById('fType').value;
  const from = document.getElementById('fFrom').value;
  const to = document.getElementById('fTo').value;
  if (type) params.append('type', type);
  if (from) params.append('from', from);
  if (to) params.append('to', to);

  try {
    const data = await api('/api/v1/transactions/me?' + params.toString());
    totalPages = data.totalPages;
    body.innerHTML = '';
    if (data.content.length === 0) {
      const tr = document.createElement('tr');
      const td = document.createElement('td');
      td.colSpan = 6;
      td.textContent = 'No transactions found';
      tr.appendChild(td);
      body.appendChild(tr);
    }
    for (const t of data.content) {
      const tr = document.createElement('tr');
      const cells = [
        new Date(t.createdAt).toLocaleString('en-IN'),
        t.type,
        money(t.amount),
        money(t.balanceAfter),
        t.relatedAccount || '',
        t.remark || ''
      ];
      for (const text of cells) {
        const td = document.createElement('td');
        td.textContent = text;       // remark is typed by a user, so never use innerHTML here
        tr.appendChild(td);
      }
      body.appendChild(tr);
    }
    document.getElementById('pageInfo').textContent =
      'Page ' + (data.page + 1) + ' of ' + Math.max(data.totalPages, 1);
    document.getElementById('prevBtn').disabled = currentPage <= 0;
    document.getElementById('nextBtn').disabled = currentPage + 1 >= totalPages;
  } catch (e) {
    body.innerHTML = '';
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 6;
    td.textContent = e.message;
    tr.appendChild(td);
    body.appendChild(tr);
  }
}

document.getElementById('applyFilters').addEventListener('click', () => { currentPage = 0; loadHistory(); });
document.getElementById('prevBtn').addEventListener('click', () => { if (currentPage > 0) { currentPage--; loadHistory(); } });
document.getElementById('nextBtn').addEventListener('click', () => { if (currentPage + 1 < totalPages) { currentPage++; loadHistory(); } });

document.getElementById('logoutBtn').addEventListener('click', () => {
  localStorage.removeItem('token');
  window.location.href = '/';
});

loadAccount();
loadHistory();
