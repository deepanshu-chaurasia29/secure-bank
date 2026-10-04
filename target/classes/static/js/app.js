const output = document.getElementById('output');

document.getElementById('registerForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const body = {
    fullName: document.getElementById('regFullName').value,
    email: document.getElementById('regEmail').value,
    phone: document.getElementById('regPhone').value,
    dateOfBirth: document.getElementById('regDob').value,
    password: document.getElementById('regPassword').value,
    accountType: document.getElementById('regAccountType').value
  };
  const res = await fetch('/api/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  const data = await res.json();
  output.textContent = JSON.stringify(data, null, 2);
  if (res.ok) localStorage.setItem('token', data.token);
});

document.getElementById('loginForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const body = {
    email: document.getElementById('loginEmail').value,
    password: document.getElementById('loginPassword').value
  };
  const res = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  const data = await res.json();
  output.textContent = JSON.stringify(data, null, 2);
  if (res.ok) localStorage.setItem('token', data.token);
});
