// CivicPulse — shared front-end utilities

// ---------------------------------------------------------------------
// ACCOUNT STORE
// TODO(backend): this whole block is a client-only stand-in for a real
// session. Once auth exists, replace with real calls: GET/PATCH /api/user/<userID>
// for profile fields, and a proper session/token for who's "logged in" —
// localStorage here is just so the demo persists across page loads.
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

function saveAccount(patch){
  const current = getAccount();
  const updated = { ...current, ...patch };
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

// Populates every `.nav-user` on the page (avatar initial + name) from the
// stored account, so the header reflects whoever "logged in" on this device.
function initNavUser(){
  const account = getAccount();
  document.querySelectorAll('.nav-user').forEach(el=>{
    const avatar = el.querySelector('.nav-avatar');
    const label = el.querySelector('span');
    if(avatar) avatar.textContent = accountInitial(account);
    if(label) label.textContent = accountDisplayName(account);
  });
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

document.addEventListener('DOMContentLoaded', ()=>{
  initNavUser();
  initGovTopbar();
});

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
