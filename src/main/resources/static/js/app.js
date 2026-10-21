'use strict';

const output = document.getElementById('output');
const loginForm = document.getElementById('loginForm');
const registerForm = document.getElementById('registerForm');
const loginPanel = document.getElementById('loginPanel');
const registerPanel = document.getElementById('registerPanel');
const loginTab = document.getElementById('loginTab');
const registerTab = document.getElementById('registerTab');

function showFeedback(message, kind = 'info') {
  output.textContent = message;
  output.className = `feedback ${kind}`;
  output.hidden = !message;
}

function setActiveTab(tabName) {
  const showLogin = tabName === 'login';
  loginPanel.hidden = !showLogin;
  registerPanel.hidden = showLogin;
  loginTab.classList.toggle('is-active', showLogin);
  registerTab.classList.toggle('is-active', !showLogin);
  loginTab.setAttribute('aria-selected', String(showLogin));
  registerTab.setAttribute('aria-selected', String(!showLogin));
  showFeedback('');
  const heading = document.getElementById('auth-heading');
  const intro = document.querySelector('.auth-intro');
  heading.textContent = showLogin ? 'Welcome back.' : 'Open your account.';
  intro.textContent = showLogin
    ? 'Sign in to continue to your SecureBank account.'
    : 'Create a demo account to explore the banking tools.';
}

loginTab.addEventListener('click', () => setActiveTab('login'));
registerTab.addEventListener('click', () => setActiveTab('register'));
document.querySelectorAll('[data-open-tab]').forEach((button) => {
  button.addEventListener('click', () => setActiveTab(button.dataset.openTab));
});

document.querySelectorAll('[data-toggle-password]').forEach((button) => {
  button.addEventListener('click', () => {
    const field = document.getElementById(button.dataset.togglePassword);
    if (!field) return;
    const reveal = field.type === 'password';
    field.type = reveal ? 'text' : 'password';
    button.textContent = reveal ? 'Hide' : 'Show';
    button.setAttribute('aria-label', reveal ? 'Hide password' : 'Show password');
    button.setAttribute('aria-pressed', String(reveal));
  });
});

// The API returns tokens on success. Never print the response object to the page.
function goAfterLogin(data) {
  if (!data || typeof data.token !== 'string' || !data.token || !['ADMIN', 'CUSTOMER'].includes(data.role)) {
    throw new Error('The server returned an unexpected sign-in response. Please try again.');
  }
  localStorage.setItem('token', data.token);
  localStorage.setItem('role', data.role);
  window.location.replace(data.role === 'ADMIN' ? '/admin.html' : '/dashboard.html');
}

async function readResponse(response) {
  try {
    return await response.json();
  } catch (_) {
    return {};
  }
}

async function submitAuth(form, endpoint, createBody, busyLabel) {
  if (form.dataset.busy === 'true') return;
  form.dataset.busy = 'true';
  const submitButton = form.querySelector('button[type="submit"]');
  const originalLabel = submitButton ? submitButton.innerHTML : '';
  if (submitButton) {
    submitButton.disabled = true;
    submitButton.innerHTML = `<span>${busyLabel}</span><span class="loading-dot" aria-hidden="true"></span>`;
  }
  showFeedback('');

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify(createBody())
    });
    const data = await readResponse(response);
    if (!response.ok) {
      throw new Error(data.message || data.error || 'We could not complete your request. Check your details and try again.');
    }
    goAfterLogin(data);
  } catch (error) {
    const message = error instanceof TypeError
      ? 'Unable to reach SecureBank. Check your connection and try again.'
      : (error.message || 'Something went wrong. Please try again.');
    showFeedback(message, 'error');
  } finally {
    form.dataset.busy = 'false';
    if (submitButton) {
      submitButton.disabled = false;
      submitButton.innerHTML = originalLabel;
    }
  }
}

registerForm.addEventListener('submit', (event) => {
  event.preventDefault();
  if (!registerForm.reportValidity()) return;
  submitAuth(registerForm, '/api/v1/auth/register', () => ({
    fullName: document.getElementById('regFullName').value.trim(),
    email: document.getElementById('regEmail').value.trim(),
    phone: document.getElementById('regPhone').value.trim(),
    dateOfBirth: document.getElementById('regDob').value,
    password: document.getElementById('regPassword').value,
    accountType: document.getElementById('regAccountType').value
  }), 'Creating account…');
});

loginForm.addEventListener('submit', (event) => {
  event.preventDefault();
  if (!loginForm.reportValidity()) return;
  submitAuth(loginForm, '/api/v1/auth/login', () => ({
    email: document.getElementById('loginEmail').value.trim(),
    password: document.getElementById('loginPassword').value
  }), 'Signing in…');
});

// Do not allow a future date to be selected in the registration form.
const dobField = document.getElementById('regDob');
if (dobField) {
  const today = new Date();
  const localDate = [today.getFullYear(), String(today.getMonth() + 1).padStart(2, '0'), String(today.getDate()).padStart(2, '0')].join('-');
  dobField.max = localDate;
}
const copyrightYear = document.getElementById('copyrightYear');
if (copyrightYear) copyrightYear.textContent = String(new Date().getFullYear());
