const root = document.documentElement;
const themeToggle = document.getElementById('themeToggle');

const savedTheme = localStorage.getItem('vybe-theme');
const systemDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
const initialTheme = savedTheme || (systemDark ? 'dark' : 'light');
root.dataset.theme = initialTheme;

function updateThemeMeta() {
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', root.dataset.theme === 'dark' ? '#1E1234' : '#F7F2FF');
}
updateThemeMeta();

themeToggle?.addEventListener('click', () => {
  root.dataset.theme = root.dataset.theme === 'dark' ? 'light' : 'dark';
  localStorage.setItem('vybe-theme', root.dataset.theme);
  updateThemeMeta();
});

const observer = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.classList.add('visible');
      observer.unobserve(entry.target);
    }
  });
}, { threshold: 0.12, rootMargin: '0px 0px -30px 0px' });

document.querySelectorAll('.reveal').forEach((el) => observer.observe(el));

const api = 'https://api.github.com/repos/anshdeepofficial/VYBE/releases/latest';
const fallback = 'https://github.com/anshdeepofficial/VYBE/releases/latest';

function humanSize(bytes) {
  if (!Number.isFinite(bytes)) return 'ARM64 APK';
  const mib = bytes / 1024 / 1024;
  return `${mib.toFixed(1)} MB · ARM64 APK`;
}

function formatDate(value) {
  if (!value) return 'Latest stable release';
  const d = new Date(value);
  return new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' }).format(d);
}

async function hydrateRelease() {
  try {
    const response = await fetch(api, { headers: { Accept: 'application/vnd.github+json' } });
    if (!response.ok) throw new Error(`GitHub API ${response.status}`);
    const release = await response.json();
    const version = release.tag_name || 'Latest';
    const assets = Array.isArray(release.assets) ? release.assets : [];
    const arm64 = assets.find(a => /arm64|arm64-v8a/i.test(a.name) && /\.apk$/i.test(a.name)) || assets.find(a => /\.apk$/i.test(a.name));
    const url = arm64?.browser_download_url || release.html_url || fallback;

    document.querySelectorAll('[data-download]').forEach((a) => { a.href = url; });
    document.getElementById('versionHero').textContent = version;
    document.getElementById('versionSpecs').textContent = version;
    document.getElementById('versionButton').textContent = version;
    document.getElementById('apkSize').textContent = humanSize(arm64?.size);
    document.getElementById('releaseDate').textContent = formatDate(release.published_at);
  } catch (error) {
    document.querySelectorAll('[data-download]').forEach((a) => { a.href = fallback; });
    console.info('Using VYBE release fallback:', error.message);
  }
}

hydrateRelease();
