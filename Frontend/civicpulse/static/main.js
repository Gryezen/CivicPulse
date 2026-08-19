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
// POLICYGYAAN — shared policy dataset.
// Single source of truth for dashboard.html, track.html and policy.html,
// so a policy card always links to the same detail/roadmap everywhere.
// TODO(backend): replace with GET /api/policies/ (list) and
// GET /api/policies/<slug> (detail) — scorePolicies() below is a
// keyword-overlap stand-in for the real semantic-search ranking.
// ---------------------------------------------------------------------
const CP_POLICIES = [
  {
    slug: 'pm-awas-yojana',
    title: 'PM Awas Yojana — Urban Housing Scheme',
    source: 'PolicyGyaan',
    category: 'Housing',
    summary: 'Subsidised home loans for first-time urban homebuyers from low- and middle-income groups.',
    keywords: ['housing','home','rent','shelter','urban','homeless','house'],
    eligibility: 'First-time homebuyers in EWS/LIG/MIG income brackets, urban households without a pucca house.',
    roadmap: [
      { phase:'Check eligibility', detail:"Confirm your income bracket and that your household doesn't already own a pucca house.", status:'done' },
      { phase:'Apply', detail:'Submit via the PMAY-U portal or your nearest Common Service Centre with income and ID proof.', status:'current' },
      { phase:'Verification', detail:'Your Urban Local Body and lending bank verify documents and, often, the site — typically 4–6 weeks.', status:'upcoming' },
      { phase:'Subsidy disbursed', detail:'The interest subsidy is credited directly to your home loan account.', status:'upcoming' },
    ],
  },
  {
    slug: 'msw-grievance-redressal',
    title: 'Municipal Solid Waste Grievance Redressal',
    source: 'PolicyGyaan',
    category: 'Sanitation & Waste',
    summary: 'The timeline SLAs your municipal corporation is legally bound to for garbage-collection complaints.',
    keywords: ['garbage','waste','trash','sanitation','collection','dump','bin'],
    eligibility: 'Any resident within municipal corporation limits — no application needed, the SLA applies automatically.',
    roadmap: [
      { phase:'Complaint logged', detail:'Your report is timestamped the moment it reaches the ward office.', status:'done' },
      { phase:'24-hour acknowledgement', detail:'The ward is required to acknowledge and schedule a collection run within 24 hours.', status:'current' },
      { phase:'Collection within 72 hours', detail:'Backlog clearance is mandated within 3 working days of the complaint.', status:'upcoming' },
      { phase:'Repeat-offender escalation', detail:'Wards missing the SLA three times in a quarter are flagged for corporation-level review.', status:'upcoming' },
    ],
  },
  {
    slug: 'jal-jeevan-mission',
    title: 'Jal Jeevan Mission — Piped Water Supply',
    source: 'PolicyGyaan',
    category: 'Water Supply',
    summary: "Guarantees functional household tap water; leaks and outages have a mandated repair window under the mission.",
    keywords: ['water','pipeline','tap','supply','leak','shortage','drink'],
    eligibility: 'All households, with priority for areas lacking a functional household tap connection.',
    roadmap: [
      { phase:'Outage/leak reported', detail:'Logged against the local Public Health Engineering division.', status:'done' },
      { phase:'Site inspection', detail:'A field engineer assesses the leak or supply gap, usually within 48 hours.', status:'current' },
      { phase:'Repair or connection', detail:'Pipeline repair or new household connection is carried out under the mission\'s funded works.', status:'upcoming' },
      { phase:'Supply restored & logged', detail:'Restoration is logged against the village/ward\'s functional household tap connection count.', status:'upcoming' },
    ],
  },
  {
    slug: 'pmgsy-road-maintenance',
    title: 'PMGSY — Road Maintenance & Pothole SLA',
    source: 'PolicyGyaan',
    category: 'Roads & Potholes',
    summary: 'Defines how fast PWD must respond to potholes and road-safety hazards on notified roads.',
    keywords: ['pothole','road','highway','tar','accident','crack','footpath'],
    eligibility: 'Applies to any PWD-notified road; report the exact stretch and nearest landmark for fastest routing.',
    roadmap: [
      { phase:'Hazard reported', detail:'Location and severity logged against the nearest PWD division.', status:'done' },
      { phase:'Risk classification', detail:'Injury-risk and repeat-mention signals decide priority — accident-linked reports jump the queue.', status:'current' },
      { phase:'Inspection & patching', detail:'Field inspection and patching work, typically within 5 working days for high-priority reports.', status:'upcoming' },
      { phase:'Quality re-check', detail:'A follow-up inspection closes the case only once the patch has held through the next rain.', status:'upcoming' },
    ],
  },
  {
    slug: 'saubhagya-electrification',
    title: 'Saubhagya — Electrical Safety & Connections',
    source: 'PolicyGyaan',
    category: 'Electricity',
    summary: 'Covers free/subsidised household electrification and safety response for exposed wiring or transformer faults.',
    keywords: ['electricity','transformer','power','wire','spark','shock','outage'],
    eligibility: 'All households for safety response; free connections prioritised for below-poverty-line households.',
    roadmap: [
      { phase:'Fault reported', detail:'Sparking, exposed wiring, or outage logged against the local Electricity Board division.', status:'done' },
      { phase:'Emergency triage', detail:'Fire/shock-risk keywords fast-track a field engineer dispatch, often same-day.', status:'current' },
      { phase:'Repair', detail:'Faulty equipment is repaired or replaced and the line is re-certified safe.', status:'upcoming' },
      { phase:'Connection (if applicable)', detail:'Eligible households without a connection are enrolled for free electrification under the scheme.', status:'upcoming' },
    ],
  },
  {
    slug: 'smart-street-lighting',
    title: 'Smart Street Lighting Maintenance Scheme',
    source: 'PolicyGyaan',
    category: 'Street Lighting',
    summary: 'Ward-level SLA for streetlight repair, prioritised by pedestrian-safety and crime-report proximity.',
    keywords: ['streetlight','light','lamp','bulb','dark','night'],
    eligibility: 'Any public street within municipal limits.',
    roadmap: [
      { phase:'Outage reported', detail:'Pole number and stretch logged against the Ward Office.', status:'done' },
      { phase:'Cluster check', detail:'Nearby reports are merged into one work order so a whole dark stretch is fixed in one visit.', status:'current' },
      { phase:'Bulb/fixture replaced', detail:'Routine maintenance visits typically close these within 48 hours.', status:'upcoming' },
    ],
  },
];

function scorePolicies(query){
  const terms = [...new Set(
    (query || '').toLowerCase().split(/[^a-z0-9]+/).filter(t => t.length >= 3)
  )];
  return CP_POLICIES.map(p=>{
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
