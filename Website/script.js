const root = document.documentElement;
const body = document.body;
const themeToggle = document.getElementById('themeToggle');
const menuButton = document.getElementById('menuButton');
const mobileNav = document.getElementById('mobileNav');
const topbar = document.querySelector('.topbar');
const fab = document.getElementById('backToTop');
const toast = document.getElementById('toast');

const savedTheme = localStorage.getItem('vybe-theme');
const systemDark = matchMedia('(prefers-color-scheme: dark)').matches;
root.dataset.theme = savedTheme || (systemDark ? 'dark' : 'light');

function updateThemeMeta(){
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', root.dataset.theme === 'dark' ? '#1E1234' : '#F7F2FF');
  const icon = themeToggle?.querySelector('[data-theme-icon]');
  if(icon){
    icon.innerHTML = root.dataset.theme === 'dark'
      ? '<path d="M12 2.75V5m0 14v2.25M4.75 12H2.5m19 0h-2.25M5.6 5.6 4 4m16 16-1.6-1.6M18.4 5.6 20 4M4 20l1.6-1.6M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10Z"/>'
      : '<path d="M12.5 2.2a9.8 9.8 0 1 0 9.3 13 8.2 8.2 0 0 1-9.3-13Z"/>';
  }
}
updateThemeMeta();

themeToggle?.addEventListener('click', e => {
  ripple(e.currentTarget, e);
  const swap = () => {
    root.dataset.theme = root.dataset.theme === 'dark' ? 'light' : 'dark';
    localStorage.setItem('vybe-theme', root.dataset.theme);
    updateThemeMeta();
  };
  document.startViewTransition ? document.startViewTransition(swap) : swap();
});

function ripple(el, event){
  const rect = el.getBoundingClientRect();
  const span = document.createElement('span');
  span.className = 'ripple';
  const size = Math.max(rect.width, rect.height);
  span.style.width = span.style.height = `${size}px`;
  span.style.left = `${(event?.clientX || rect.left + rect.width/2) - rect.left}px`;
  span.style.top = `${(event?.clientY || rect.top + rect.height/2) - rect.top}px`;
  el.appendChild(span);
  setTimeout(()=>span.remove(), 620);
}

document.querySelectorAll('.button,.icon-button,.menu-button,.quick-link,.material-fab').forEach(el => {
  el.addEventListener('pointerdown', e => ripple(el,e));
});

menuButton?.addEventListener('click', () => {
  const open = mobileNav?.classList.toggle('open');
  menuButton.setAttribute('aria-expanded', String(Boolean(open)));
});

document.addEventListener('click', e => {
  if(!mobileNav?.classList.contains('open')) return;
  if(mobileNav.contains(e.target) || menuButton?.contains(e.target)) return;
  mobileNav.classList.remove('open');
  menuButton?.setAttribute('aria-expanded','false');
});

const current = location.pathname.split('/').pop() || 'index.html';
document.querySelectorAll('[data-nav]').forEach(a => {
  const href = a.getAttribute('href');
  if(href === current || (current === '' && href === 'index.html')) a.classList.add('active');
});

const observer = new IntersectionObserver(entries => {
  entries.forEach(entry => {
    if(entry.isIntersecting){
      entry.target.classList.add('visible');
      observer.unobserve(entry.target);
    }
  });
},{threshold:.12,rootMargin:'0px 0px -34px'});
document.querySelectorAll('.reveal').forEach(el=>observer.observe(el));

window.addEventListener('scroll',()=>{
  const y = scrollY;
  topbar?.classList.toggle('scrolled', y > 20);
  fab?.classList.toggle('show', y > 650);
},{passive:true});
fab?.addEventListener('click',()=>scrollTo({top:0,behavior:'smooth'}));

// Lightweight Material-style parallax for the hero devices.
const visual = document.querySelector('[data-parallax]');
if(visual && !matchMedia('(prefers-reduced-motion: reduce)').matches){
  visual.addEventListener('pointermove', e => {
    const r = visual.getBoundingClientRect();
    const x = (e.clientX-r.left)/r.width-.5;
    const y = (e.clientY-r.top)/r.height-.5;
    visual.querySelectorAll('.phone').forEach((p,i)=>{
      const depth = (i+1)*4;
      p.style.marginLeft = `${x*depth}px`;
      p.style.marginTop = `${y*depth}px`;
    });
  });
  visual.addEventListener('pointerleave',()=>visual.querySelectorAll('.phone').forEach(p=>{p.style.marginLeft='';p.style.marginTop='';}));
}

// Cross-page Material motion with the View Transitions API where available.
document.querySelectorAll('a[data-transition]').forEach(a => {
  a.addEventListener('click', e => {
    if(e.metaKey||e.ctrlKey||e.shiftKey||e.altKey||a.target==='_blank') return;
    const url = new URL(a.href,location.href);
    if(url.origin!==location.origin) return;
    if(document.startViewTransition){
      e.preventDefault();
      document.startViewTransition(()=>{location.href=url.href});
    }
  });
});

const api = 'https://api.github.com/repos/anshdeepofficial/VYBE/releases/latest';
const fallback = 'https://github.com/anshdeepofficial/VYBE/releases/latest';
function humanSize(bytes){if(!Number.isFinite(bytes))return 'ARM64 APK';return `${(bytes/1024/1024).toFixed(1)} MB · ARM64 APK`;}
function formatDate(value){if(!value)return 'Latest stable release';return new Intl.DateTimeFormat(undefined,{day:'numeric',month:'short',year:'numeric'}).format(new Date(value));}
function safeText(id,text){const el=document.getElementById(id);if(el)el.textContent=text;}

async function hydrateRelease(){
  try{
    const response=await fetch(api,{headers:{Accept:'application/vnd.github+json'}});
    if(!response.ok)throw new Error(`GitHub API ${response.status}`);
    const release=await response.json();
    const version=release.tag_name||'Latest';
    const assets=Array.isArray(release.assets)?release.assets:[];
    const arm64=assets.find(a=>/arm64|arm64-v8a/i.test(a.name)&&/\.apk$/i.test(a.name))||assets.find(a=>/\.apk$/i.test(a.name));
    const url=arm64?.browser_download_url||release.html_url||fallback;
    document.querySelectorAll('[data-download]').forEach(a=>a.href=url);
    safeText('versionHero',version);safeText('versionSpecs',version);safeText('versionButton',version);safeText('versionDownload',version);
    safeText('apkSize',humanSize(arm64?.size));safeText('releaseDate',formatDate(release.published_at));safeText('assetName',arm64?.name||'Latest APK');
    const notes=document.getElementById('releaseNotes');if(notes)notes.textContent=release.body||'Latest stable VYBE release.';
  }catch(error){
    document.querySelectorAll('[data-download]').forEach(a=>a.href=fallback);
    console.info('Using VYBE release fallback:',error.message);
  }
}
hydrateRelease();

const faqSearch=document.getElementById('faqSearch');
faqSearch?.addEventListener('input',()=>{
  const q=faqSearch.value.trim().toLowerCase();
  document.querySelectorAll('.faq-item').forEach(item=>{
    const match=item.textContent.toLowerCase().includes(q);
    item.classList.toggle('hidden',!match);
    if(q && match)item.open=true;
  });
});

function showToast(message){
  if(!toast)return;
  toast.textContent=message;toast.classList.add('show');
  clearTimeout(showToast.t);showToast.t=setTimeout(()=>toast.classList.remove('show'),2200);
}
document.querySelectorAll('[data-copy]').forEach(btn=>btn.addEventListener('click',async()=>{
  try{await navigator.clipboard.writeText(btn.dataset.copy);showToast('Copied to clipboard');}catch{showToast('Copy unavailable');}
}));
