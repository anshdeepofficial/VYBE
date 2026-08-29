const root=document.documentElement;
const themeToggle=document.getElementById('themeToggle');
const menuButton=document.getElementById('menuButton');
const mobileNav=document.getElementById('mobileNav');
const topbar=document.querySelector('.topbar');
const fab=document.getElementById('backToTop');
const footer=document.querySelector('.footer');

const savedTheme=localStorage.getItem('vybe-theme');
root.dataset.theme=savedTheme||(matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light');
function updateThemeMeta(){
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content',root.dataset.theme==='dark'?'#1E1234':'#F7F2FF');
  const icon=themeToggle?.querySelector('[data-theme-icon]');
  if(icon) icon.innerHTML=root.dataset.theme==='dark'
    ?'<path d="M12 2.75V5m0 14v2.25M4.75 12H2.5m19 0h-2.25M5.6 5.6 4 4m16 16-1.6-1.6M18.4 5.6 20 4M4 20l1.6-1.6M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10Z"/>'
    :'<path d="M12.5 2.2a9.8 9.8 0 1 0 9.3 13 8.2 8.2 0 0 1-9.3-13Z"/>';
}
updateThemeMeta();
function ripple(el,e){const r=el.getBoundingClientRect(),s=document.createElement('span'),size=Math.max(r.width,r.height);s.className='ripple';s.style.width=s.style.height=`${size}px`;s.style.left=`${(e?.clientX||r.left+r.width/2)-r.left}px`;s.style.top=`${(e?.clientY||r.top+r.height/2)-r.top}px`;el.appendChild(s);setTimeout(()=>s.remove(),640)}
themeToggle?.addEventListener('click',e=>{ripple(e.currentTarget,e);const swap=()=>{root.dataset.theme=root.dataset.theme==='dark'?'light':'dark';localStorage.setItem('vybe-theme',root.dataset.theme);updateThemeMeta()};document.startViewTransition?document.startViewTransition(swap):swap()});
document.querySelectorAll('.button,.icon-button,.menu-button,.quick-link,.material-fab,.phone-button').forEach(el=>el.addEventListener('pointerdown',e=>ripple(el,e)));

menuButton?.addEventListener('click',()=>{const open=mobileNav?.classList.toggle('open');menuButton.setAttribute('aria-expanded',String(Boolean(open)))});
document.addEventListener('click',e=>{if(!mobileNav?.classList.contains('open'))return;if(mobileNav.contains(e.target)||menuButton?.contains(e.target))return;mobileNav.classList.remove('open');menuButton?.setAttribute('aria-expanded','false')});
const current=location.pathname.split('/').pop()||'index.html';
document.querySelectorAll('[data-nav]').forEach(a=>{if(a.getAttribute('href')===current||(current===''&&a.getAttribute('href')==='index.html'))a.classList.add('active')});

const observer=new IntersectionObserver(entries=>entries.forEach(entry=>{if(entry.isIntersecting){entry.target.classList.add('visible');observer.unobserve(entry.target)}}),{threshold:.08,rootMargin:'0px 0px -20px'});document.querySelectorAll('.reveal').forEach(el=>observer.observe(el));
function updateScrollUI(){topbar?.classList.toggle('scrolled',scrollY>18);fab?.classList.toggle('show',scrollY>620);if(footer&&fab){fab.classList.toggle('near-footer',footer.getBoundingClientRect().top<innerHeight-20)}}
addEventListener('scroll',updateScrollUI,{passive:true});updateScrollUI();fab?.addEventListener('click',()=>scrollTo({top:0,behavior:'smooth'}));

// Interactive 3-screen showcase.
const stage=document.querySelector('.hero-stage');
const phones=[...document.querySelectorAll('.phone-button[data-screen-id]')];
const screenTitle=document.getElementById('screenTitle');
const screenDescription=document.getElementById('screenDescription');
const screenEyebrow=document.getElementById('screenEyebrow');
const screenData={
  home:{eyebrow:'Personalized home',title:'Home',description:'Quick Picks, mixes, recent music and recommendations come together in one starting point built around your listening.'},
  search:{eyebrow:'Fast discovery',title:'Search',description:'Find songs, albums and artists quickly, then move straight into the catalog through a clean Material 3 discovery flow.'},
  player:{eyebrow:'Focused playback',title:'Player',description:'Artwork, playback controls, queue actions and synced lyrics stay together in a focused player designed for active listening.'}
};
let locked=null;
function showScreen(id,lock=false){
  if(!stage||!screenData[id])return;
  stage.classList.add('is-interacting');
  phones.forEach(p=>{const active=p.dataset.screenId===id;p.classList.toggle('active',active);p.setAttribute('aria-pressed',String(active))});
  if(screenEyebrow)screenEyebrow.textContent=screenData[id].eyebrow;
  if(screenTitle)screenTitle.textContent=screenData[id].title;
  if(screenDescription)screenDescription.textContent=screenData[id].description;
  if(lock)locked=id;
}
function clearScreen(){
  if(!stage)return;
  if(locked){showScreen(locked);return}
  stage.classList.remove('is-interacting');
  phones.forEach(p=>{p.classList.remove('active');p.setAttribute('aria-pressed','false')});
  if(screenEyebrow)screenEyebrow.textContent='Interactive preview';
  if(screenTitle)screenTitle.textContent='Explore the VYBE interface';
  if(screenDescription)screenDescription.textContent='Hover any screen to spread the preview, highlight that screen and see what the area is designed to do.';
}
phones.forEach(p=>{p.addEventListener('mouseenter',()=>showScreen(p.dataset.screenId));p.addEventListener('focus',()=>showScreen(p.dataset.screenId));p.addEventListener('click',()=>showScreen(p.dataset.screenId,true))});
stage?.addEventListener('mouseleave',clearScreen);clearScreen();

const api='https://api.github.com/repos/anshdeepofficial/VYBE/releases/latest',fallback='https://github.com/anshdeepofficial/VYBE/releases/latest';
const humanSize=b=>Number.isFinite(b)?`${(b/1024/1024).toFixed(1)} MB · ARM64 APK`:'ARM64 APK';
const formatDate=v=>v?new Intl.DateTimeFormat(undefined,{day:'numeric',month:'short',year:'numeric'}).format(new Date(v)):'Latest stable release';
async function hydrateRelease(){try{const response=await fetch(api,{headers:{Accept:'application/vnd.github+json'}});if(!response.ok)throw new Error(`GitHub API ${response.status}`);const release=await response.json(),version=release.tag_name||'Latest',assets=Array.isArray(release.assets)?release.assets:[],arm64=assets.find(a=>/arm64|arm64-v8a/i.test(a.name)&&/\.apk$/i.test(a.name))||assets.find(a=>/\.apk$/i.test(a.name)),url=arm64?.browser_download_url||release.html_url||fallback;document.querySelectorAll('[data-download]').forEach(a=>a.href=url);document.querySelectorAll('[data-version]').forEach(el=>el.textContent=version);const set=(id,t)=>{const el=document.getElementById(id);if(el)el.textContent=t};set('apkSize',humanSize(arm64?.size));set('releaseDate',formatDate(release.published_at));set('assetName',arm64?.name||'Latest APK');set('releaseNotes',release.body||'Latest stable VYBE release.')}catch(error){document.querySelectorAll('[data-download]').forEach(a=>a.href=fallback);console.info('Using release fallback',error.message)}}hydrateRelease();
const faqSearch=document.getElementById('faqSearch');faqSearch?.addEventListener('input',()=>{const q=faqSearch.value.trim().toLowerCase();document.querySelectorAll('.faq-item').forEach(item=>{const match=item.textContent.toLowerCase().includes(q);item.classList.toggle('hidden',!match);if(q&&match)item.open=true})});
