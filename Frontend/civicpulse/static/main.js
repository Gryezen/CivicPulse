// CivicPulse — shared front-end utilities

// ---------------------------------------------------------------------
// ACCOUNT STORE + API
// localStorage is kept as a fast local cache (so the header/forms paint
// instantly instead of flashing "Anon Citizen" while a request is in
// flight) but the source of truth is now the Flask/Postgres backend —
// see auth.py. initNavUser() re-syncs the cache from the server on every
// page load.
// ---------------------------------------------------------------------
const CP_ACCOUNT_KEY = 'civicpulse_account';

const CP_DEFAULT_ACCOUNT = {
  name: '',
  email: '',
  region: '',
  education: '',
  employed: true,
  occupation: '',
  language: 'English',
};

function getAccount(){
  try{
    const raw = localStorage.getItem(CP_ACCOUNT_KEY);
    if(!raw) return { ...CP_DEFAULT_ACCOUNT };
    return { ...CP_DEFAULT_ACCOUNT, ...JSON.parse(raw) };
  } catch(e){
    return { ...CP_DEFAULT_ACCOUNT };
  }
}

function cacheAccount(data){
  const updated = { ...CP_DEFAULT_ACCOUNT, ...data };
  localStorage.setItem(CP_ACCOUNT_KEY, JSON.stringify(updated));
  return updated;
}

function clearAccount(){
  localStorage.removeItem(CP_ACCOUNT_KEY);
}

function accountInitial(account){
  const source = (account.name || account.email || 'Anon Citizen').trim();
  return source ? source[0].toUpperCase() : 'A';
}

function accountDisplayName(account){
  return account.name && account.name.trim() ? account.name.trim() : 'Anon Citizen';
}

function paintNavUser(account){
  document.querySelectorAll('.nav-user').forEach(el=>{
    const avatar = el.querySelector('.nav-avatar');
    const label = el.querySelector('span');
    if(avatar) avatar.textContent = accountInitial(account);
    if(label) label.textContent = accountDisplayName(account);
  });
}

// Paints from cache immediately, then refreshes from the server (protected
// pages are only reachable when logged in, so this should always succeed
// there — on the public pages, a 401 is expected and just leaves the
// default "Anon Citizen" in place).
function initNavUser(){
  paintNavUser(getAccount());
  apiGet('/api/user/me').then(data=>{
    paintNavUser(cacheAccount(data));
  }).catch(()=>{ /* not logged in on this page — fine */ });
}

// ---------------------------------------------------------------------
// Small fetch wrappers around the auth/account API. Every call rejects
// with an Error whose message is the server's `error` string, so callers
// can just `.catch(e => showToast(e.message))`.
// ---------------------------------------------------------------------
async function apiCall(method, url, body){
  const res = await fetch(url, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
    credentials: 'same-origin',
  });
  let data = {};
  try{ data = await res.json(); } catch(e){ /* empty body */ }
  if(!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
  return data;
}

const apiGet = (url) => apiCall('GET', url);
const apiPost = (url, body) => apiCall('POST', url, body);
const apiPatch = (url, body) => apiCall('PATCH', url, body);

function apiRegister(payload){
  return apiPost('/api/auth/register', payload).then(cacheAccount);
}
function apiLogin(email, password){
  return apiPost('/api/auth/login', { email, password }).then(cacheAccount);
}
function apiLogout(){
  return apiPost('/api/auth/logout').finally(clearAccount);
}
function apiUpdateAccount(patch){
  return apiPatch('/api/user/me', patch).then(cacheAccount);
}
function apiChangePassword(currentPassword, newPassword){
  return apiPost('/api/user/me/password', { current_password: currentPassword, new_password: newPassword });
}

// ---------------------------------------------------------------------
// GOV TOPBAR — text-size controls + screen reader note.
// Wired defensively: pages that don't include the topbar simply skip this.
// ---------------------------------------------------------------------
const CP_FONT_KEY = 'civicpulse_font_scale';
const FONT_SCALES = [93, 100, 106, 112, 118]; // percent, 106 is the site default
const FONT_DEFAULT_INDEX = 2;

function applyFontScale(percent){
  document.documentElement.style.fontSize = percent + '%';
}

function initGovTopbar(){
  const decBtn = document.getElementById('fontDec');
  const incBtn = document.getElementById('fontInc');
  const resetBtn = document.getElementById('fontReset');
  const srBtn = document.getElementById('screenReaderBtn');

  if(decBtn || incBtn || resetBtn){
    let idx = FONT_DEFAULT_INDEX;
    const saved = localStorage.getItem(CP_FONT_KEY);
    if(saved !== null){
      const savedIdx = FONT_SCALES.indexOf(parseInt(saved, 10));
      if(savedIdx !== -1){ idx = savedIdx; applyFontScale(FONT_SCALES[idx]); }
    }
    const persist = ()=> localStorage.setItem(CP_FONT_KEY, String(FONT_SCALES[idx]));
    decBtn?.addEventListener('click', ()=>{
      idx = Math.max(0, idx - 1);
      applyFontScale(FONT_SCALES[idx]); persist();
    });
    incBtn?.addEventListener('click', ()=>{
      idx = Math.min(FONT_SCALES.length - 1, idx + 1);
      applyFontScale(FONT_SCALES[idx]); persist();
    });
    resetBtn?.addEventListener('click', ()=>{
      idx = FONT_DEFAULT_INDEX;
      applyFontScale(FONT_SCALES[idx]); persist();
    });
  }

  srBtn?.addEventListener('click', ()=>{
    showToast('Built to WCAG 2.1 AA — screen-reader labels are on every control.');
  });
}

// ---------------------------------------------------------------------
// MOBILE NAV — hamburger toggle for the header's .nav-links.
// Defensive: pages without a .nav-toggle (e.g. login.html) just skip this.
// ---------------------------------------------------------------------
function initMobileNav(){
  const header = document.querySelector('header.nav');
  const toggle = document.getElementById('navToggle');
  if(!header || !toggle) return;

  function setOpen(open){
    header.classList.toggle('nav-open', open);
    toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
  }

  toggle.addEventListener('click', ()=> setOpen(!header.classList.contains('nav-open')));

  // close on nav-link tap, and on outside click
  header.querySelectorAll('.nav-links a').forEach(a=>{
    a.addEventListener('click', ()=> setOpen(false));
  });
  document.addEventListener('click', (e)=>{
    if(header.classList.contains('nav-open') && !header.contains(e.target)) setOpen(false);
  });
  // collapse back to the desktop layout if the viewport is resized wider
  window.addEventListener('resize', ()=>{
    if(window.innerWidth > 760) setOpen(false);
  });
}

document.addEventListener('DOMContentLoaded', ()=>{
  initNavUser();
  initGovTopbar();
  initMobileNav();
});

// ---------------------------------------------------------------------
// COMPLAINTS — real API wrappers (see complaints.py). Backed by Postgres.
// ---------------------------------------------------------------------
function apiCreateComplaint(payload){
  return apiPost('/api/complaints', payload);
}
function apiMyComplaints(){
  return apiGet('/api/complaints/mine');
}
function apiQueue(params){
  const qs = new URLSearchParams(params || {});
  return apiGet('/api/complaints' + (qs.toString() ? '?' + qs.toString() : ''));
}

// ---------------------------------------------------------------------
// POLICYGYAAN — shared policy dataset.
// Real data now — served by the Flask app (see policy_engine.py / app.py)
// and injected per-page via Jinja as `const CP_POLICIES = {{ ...|safe }}`
// (dashboard.html gets a personalised top-N from Gemini, track.html gets
// the full catalogue for its search box). scorePolicies() below is the
// same keyword-overlap ranking it always was — used as the fallback
// whenever Gemini isn't configured, and for in-page search filtering.
// ---------------------------------------------------------------------
function scorePolicies(query, policies){
  const list = policies || (typeof CP_POLICIES !== 'undefined' ? CP_POLICIES : []);
  const terms = [...new Set(
    (query || '').toLowerCase().split(/[^a-z0-9]+/).filter(t => t.length >= 3)
  )];
  return list.map(p=>{
    const text = `${p.title} ${p.summary} ${p.category}`.toLowerCase();
    let score = 0;
    terms.forEach(t=>{
      if(p.keywords.some(k => k.includes(t) || t.includes(k))) score += 3;
      else if(text.includes(t)) score += 1;
    });
    return { policy: p, score };
  }).sort((a,b)=> b.score - a.score);
}

// Toast notification. Usage: showToast('Complaint filed — docket #CP-2091')
function showToast(message, timeout=3200){
  let toast = document.getElementById('cp-toast');
  if(!toast){
    toast = document.createElement('div');
    toast.id = 'cp-toast';
    toast.className = 'toast';
    toast.setAttribute('role', 'status');
    toast.setAttribute('aria-live', 'polite');
    document.body.appendChild(toast);
  }
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(toast._t);
  toast._t = setTimeout(()=> toast.classList.remove('show'), timeout);
}

// Wires a .file-drop zone (with a nested <input type=file>) to a list container.
// Returns a function that returns the currently staged File[] array.
function initFileDrop(dropEl, inputEl, listEl, opts={}){
  const maxFiles = opts.maxFiles || 5;
  const accept = opts.accept || ['image/jpeg','image/png','video/mp4','audio/wav'];
  let files = [];

  function render(){
    listEl.innerHTML = '';
    files.forEach((f, i)=>{
      const row = document.createElement('div');
      row.className = 'file-item';
      const sizeKb = (f.size/1024).toFixed(0);
      row.innerHTML = `<span class="fname">${f.name} · ${sizeKb}KB</span>`;
      const btn = document.createElement('button');
      btn.className = 'fremove';
      btn.type = 'button';
      btn.setAttribute('aria-label', `Remove ${f.name}`);
      btn.textContent = '✕';
      btn.addEventListener('click', ()=>{
        files.splice(i,1);
        render();
      });
      row.appendChild(btn);
      listEl.appendChild(row);
    });
  }

  function addFiles(fileList){
    Array.from(fileList).forEach(f=>{
      if(files.length >= maxFiles){ showToast(`Max ${maxFiles} files as proof`); return; }
      if(accept.length && !accept.includes(f.type)){ showToast(`${f.name} isn't an accepted file type`); return; }
      files.push(f);
    });
    render();
  }

  dropEl.addEventListener('click', ()=> inputEl.click());
  dropEl.addEventListener('keydown', (e)=>{ if(e.key==='Enter' || e.key===' '){ e.preventDefault(); inputEl.click(); } });
  inputEl.addEventListener('change', (e)=> addFiles(e.target.files));

  ['dragenter','dragover'].forEach(evt=>{
    dropEl.addEventListener(evt, (e)=>{ e.preventDefault(); dropEl.classList.add('drag'); });
  });
  ['dragleave','drop'].forEach(evt=>{
    dropEl.addEventListener(evt, (e)=>{ e.preventDefault(); dropEl.classList.remove('drag'); });
  });
  dropEl.addEventListener('drop', (e)=>{
    if(e.dataTransfer && e.dataTransfer.files) addFiles(e.dataTransfer.files);
  });

  return ()=> files;
}
