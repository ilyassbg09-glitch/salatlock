/* Salat Lock — version iPhone (app web).
   Même serveur et même classement que l'app Android. */
'use strict';

const VERSION = 'web-2.0';
const SB_URL = 'https://efagehkrrahnjshhvdhp.supabase.co';
const SB_KEY = 'sb_publishable_zsoHntdsdW6lfKKK6HHsIw_mWlpSzgc'; // clé publique
const SITE = 'https://ilyassbg09-glitch.github.io/salatlock';
const APP_URL = SITE + '/app/';
const PRAYERS = [
  { k: 'FAJR', fr: 'Fajr', ar: 'الفجر' }, { k: 'DHUHR', fr: 'Dhuhr', ar: 'الظهر' }, { k: 'ASR', fr: 'Asr', ar: 'العصر' },
  { k: 'MAGHRIB', fr: 'Maghrib', ar: 'المغرب' }, { k: 'ISHA', fr: 'Isha', ar: 'العشاء' }];
const PR = Object.fromEntries(PRAYERS.map(p => [p.k, p]));
const CONTRACT = "Bismillah.\n\nJe prends l'intention sincère, pour Allah, de faire tout mon possible pour accomplir mes cinq prières à leur heure.\n\n" +
  "• Quand Salat Lock me bloque, je vais faire mes ablutions et je prie, au lieu de chercher à contourner l'application.\n" +
  "• Je n'utilise les pauses que pour de vraies urgences.\n" +
  "• Si je rate une prière, je ne me décourage pas : je la rattrape et je recommence.\n\n" +
  "Ceci est une intention (niyya) que je prends envers moi-même, pas un serment.\nQu'Allah me facilite et accepte mes prières.";
const DUA_AR = 'أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا اللهُ وَحْدَهُ لَا شَرِيكَ لَهُ، وَأَشْهَدُ أَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ';
const DUA_FR = "J'atteste qu'il n'y a de divinité qu'Allah, Seul, sans associé, et j'atteste que Muhammad est Son serviteur et Son messager. (Muslim)";
const QUOTES = [
  '« La prière demeure, pour les croyants, une prescription à des temps déterminés. » — An-Nisâ’ 4:103',
  '« Cherchez secours dans l’endurance et la prière. » — Al-Baqara 2:45',
  '« Accomplis la prière, car la prière préserve de la turpitude et du blâmable. » — Al-’Ankabût 29:45',
  '« L’action la plus aimée d’Allah est la prière accomplie à son heure. » — Al-Bukhârî et Muslim',
  '« Le premier acte sur lequel le serviteur sera jugé, c’est la prière. » — At-Tirmidhî'];
const CITIES = { 'Paris': [48.8566, 2.3522], 'Lyon': [45.764, 4.8357], 'Marseille': [43.2965, 5.3698], 'Lille': [50.6292, 3.0573],
  'Toulouse': [43.6047, 1.4442], 'Nice': [43.7102, 7.262], 'Strasbourg': [48.5734, 7.7521], 'Bordeaux': [44.8378, -0.5792],
  'Nantes': [47.2184, -1.5536], 'Bruxelles': [50.8503, 4.3517], 'Genève': [46.2044, 6.1432], 'Montréal': [45.5019, -73.5674],
  'Casablanca': [33.5731, -7.5898], 'Rabat': [34.0209, -6.8416], 'Marrakech': [31.6295, -7.9811], 'Fès': [34.0181, -5.0078],
  'Tanger': [35.7595, -5.834], 'Agadir': [30.4278, -9.5981], 'Oujda': [34.6814, -1.9086], 'Alger': [36.7538, 3.0588],
  'Oran': [35.6971, -0.6308], 'Tunis': [36.8065, 10.1815], 'Dakar': [14.7167, -17.4677], 'Istanbul': [41.0082, 28.9784] };
const METHODS = { MWL: 'Ligue islamique mondiale (18° / 17°)', FRANCE12: 'France – UOIF (12° / 12°)', FRANCE15: 'France – 15° / 15°',
  FRANCE18: 'France – Mosquée de Paris (18° / 18°)', MAROC: 'Maroc – Habous (19° / 17°)', ALGERIE: 'Algérie (18° / 17°)',
  TUNISIE: 'Tunisie (18° / 18°)', EGYPT: 'Égypte', UMM_AL_QURA: 'Umm al-Qura (La Mecque)', ISNA: 'Amérique du Nord – ISNA',
  KARACHI: 'Karachi', DUBAI: 'Dubaï', KUWAIT: 'Koweït', QATAR: 'Qatar', SINGAPORE: 'Singapour' };

// ---------------------------------------------------------------- stockage
const store = {
  get(k, d) { try { const v = localStorage.getItem('sl_' + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem('sl_' + k, JSON.stringify(v)); } catch {} },
  del(k) { try { localStorage.removeItem('sl_' + k); } catch {} },
};
const uuid = () => (crypto.randomUUID ? crypto.randomUUID() : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
  const r = Math.random() * 16 | 0; return (c === 'x' ? r : (r & 3 | 8)).toString(16); }));
const S = Object.assign({ method: 'MWL', hanafi: false, grace: 15, countdown: 10, sens: 50, adhan: true,
  enabled: { FAJR: true, DHUHR: true, ASR: true, MAGHRIB: true, ISHA: true } }, store.get('settings', {}));
const saveSettings = () => store.set('settings', S);
function token() { let t = store.get('token'); if (!t) { t = uuid(); store.set('token', t); } return t; }
function installId() { let t = store.get('install'); if (!t) { t = uuid(); store.set('install', t); } return t; }

// ---------------------------------------------------------------- outils
const $app = document.getElementById('app');
const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const pad = n => String(n).padStart(2, '0');
const hm = d => pad(d.getHours()) + ':' + pad(d.getMinutes());
const dayKey = d => d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
function toast(msg) { const t = document.getElementById('toast'); t.textContent = msg; t.classList.add('on'); clearTimeout(toast.h); toast.h = setTimeout(() => t.classList.remove('on'), 3200); }
let cleanup = [];
function render(html) { cleanup.forEach(f => { try { f(); } catch {} }); cleanup = []; document.body.dataset.s = ''; $app.innerHTML = html; window.scrollTo(0, 0); }
const on = (id, ev, fn) => { const el = document.getElementById(id); if (el) el.addEventListener(ev, fn); return el; };

// ---------------------------------------------------------------- horaires
function params() {
  const CM = adhan.CalculationMethod;
  const custom = (f, i) => { const p = CM.Other(); p.fajrAngle = f; p.ishaAngle = i; return p; };
  const p = ({ FRANCE12: () => custom(12, 12), FRANCE15: () => custom(15, 15), FRANCE18: () => custom(18, 18), MAROC: () => custom(19, 17),
    ALGERIE: () => custom(18, 17), TUNISIE: () => custom(18, 18), EGYPT: CM.Egyptian, UMM_AL_QURA: CM.UmmAlQura, ISNA: CM.NorthAmerica,
    KARACHI: CM.Karachi, DUBAI: CM.Dubai, KUWAIT: CM.Kuwait, QATAR: CM.Qatar, SINGAPORE: CM.Singapore }[S.method] || CM.MuslimWorldLeague)();
  p.madhab = S.hanafi ? adhan.Madhab.Hanafi : adhan.Madhab.Shafi;
  return p;
}
function timesFor(date) {
  if (S.lat == null) return null;
  const t = new adhan.PrayerTimes(new adhan.Coordinates(S.lat, S.lng), date, params());
  return { FAJR: t.fajr, DHUHR: t.dhuhr, ASR: t.asr, MAGHRIB: t.maghrib, ISHA: t.isha, sunrise: t.sunrise };
}
/** Créneaux : début = adhan, fin = adhan suivant. */
function windows(fromDay, nDays) {
  const out = [];
  for (let k = 0; k < nDays; k++) {
    const d = addDays(fromDay, k), t = timesFor(d), next = timesFor(addDays(d, 1));
    if (!t) return [];
    PRAYERS.forEach((p, i) => out.push({ p: p.k, day: dayKey(d), start: t[p.k], end: i < 4 ? t[PRAYERS[i + 1].k] : next.FAJR }));
  }
  return out;
}
function currentWindow(now = new Date()) { return windows(addDays(now, -1), 2).find(w => now >= w.start && now < w.end) || null; }
function nextWindow(now = new Date()) { return windows(now, 2).find(w => w.start > now) || null; }
const isDone = (day, p) => !!store.get('done', {})[day + '|' + p];
function markDone(day, p) {
  const d = store.get('done', {}); if (d[day + '|' + p]) return; d[day + '|' + p] = 1;
  const cut = dayKey(addDays(new Date(), -400)); for (const k of Object.keys(d)) if (k < cut) delete d[k];
  store.set('done', d); store.set('total', store.get('total', 0) + 1);
  const q = store.get('queue', []); q.push([day, p]); store.set('queue', q);
}
const excused = () => Date.now() < store.get('excusedUntil', 0);
function shouldLock(now = new Date()) {
  const w = currentWindow(now);
  if (!w || !S.enabled[w.p] || excused() || isDone(w.day, w.p)) return null;
  return now.getTime() >= w.start.getTime() + S.grace * 60000 ? w : null;
}
function streak() {
  const all = d => PRAYERS.filter(p => S.enabled[p.k]).every(p => isDone(dayKey(d), p.k));
  let n = 0, d = new Date(); if (all(d)) n++; d = addDays(d, -1);
  for (let i = 0; i < 400 && all(d); i++) { n++; d = addDays(d, -1); }
  return n;
}
const qiblaDeg = () => S.lat == null ? null : adhan.Qibla(new adhan.Coordinates(S.lat, S.lng));

// ---------------------------------------------------------------- serveur
async function http(path, body, auth) {
  const h = { apikey: SB_KEY, 'Content-Type': 'application/json' };
  if (auth) h.Authorization = 'Bearer ' + auth;
  const r = await fetch(SB_URL + path, { method: 'POST', headers: h, body: JSON.stringify(body || {}) });
  const txt = await r.text(); let j = null; try { j = txt ? JSON.parse(txt) : null; } catch {}
  return { ok: r.ok, status: r.status, j };
}
async function session() {
  const s = store.get('session');
  if (s && Date.now() < s.exp) return s.access;
  if (s && s.refresh) {
    const r = await http('/auth/v1/token?grant_type=refresh_token', { refresh_token: s.refresh });
    if (r.ok) { saveSession(r.j); return r.j.access_token; }
  }
  const r = await http('/auth/v1/signup', { data: {} });
  if (!r.ok) throw new Error('Connexion au serveur impossible');
  saveSession(r.j); return r.j.access_token;
}
function saveSession(j) { store.set('session', { access: j.access_token, refresh: j.refresh_token, exp: Date.now() + (j.expires_in || 3600) * 1000 - 60000 }); }
async function rpc(fn, args, auth = true) {
  let tok = auth ? await session() : null;
  let r = await http('/rest/v1/rpc/' + fn, args, tok);
  if (r.status === 401 && auth) { store.del('session'); tok = await session(); r = await http('/rest/v1/rpc/' + fn, args, tok); }
  if (!r.ok) { const m = (r.j && r.j.message) || ('Erreur serveur ' + r.status); throw new Error(m.charAt(0).toUpperCase() + m.slice(1)); }
  return r.j;
}
const friendly = e => /Failed to fetch|NetworkError|Load failed/i.test(e.message) ? 'Pas de connexion internet' : e.message;

/** Envoie au serveur les créneaux des 7 prochains jours (pour le Raccourci) + compteur anonyme + file d'attente. */
async function sync() {
  if (S.lat == null) return;
  const ws = windows(addDays(new Date(), -1), 8).filter(w => S.enabled[w.p]).map(w => ({
    p: w.p, day: w.day, lock_at: new Date(w.start.getTime() + S.grace * 60000).toISOString(), end_at: w.end.toISOString(),
    cd: Math.max(3, S.countdown), sens: S.sens }));
  try {
    await rpc('ios_register', { p_token: token(), p_windows: ws, p_excused_until: excused() ? new Date(store.get('excusedUntil')).toISOString() : null });
    // les prières validées sur la page de blocage (Safari) remontent ici
    const st = await rpc('ios_state', { p_token: token() }, false);
    (st.done || []).forEach(x => { if (!isDone(x.day, x.p)) markDone(x.day, x.p); });
    const today = dayKey(new Date());
    if (store.get('lastPing') !== today) { await rpc('ping', { p_install: installId(), p_platform: 'ios', p_version: VERSION }, false); store.set('lastPing', today); }
    await flush();
  } catch (e) { console.warn('sync', e); }
}
async function flush() {
  const minDay = dayKey(addDays(new Date(), -2));
  let q = store.get('queue', []).filter(([d]) => d >= minDay);
  store.set('queue', q);
  if (!store.get('hasProfile')) return;
  for (const [d, p] of q.slice()) {
    await rpc('ios_done', { p_token: token(), p_day: d, p_prayer: p }).catch(() => rpc('record_prayer', { p_day: d, p_prayer: p }));
    q = store.get('queue', []).filter(x => !(x[0] === d && x[1] === p)); store.set('queue', q);
  }
  if (excused()) await rpc('record_excused', { p_day: dayKey(new Date()) }).catch(() => {});
}

// ---------------------------------------------------------------- adhan
const ADHANS = ['audio/adhan_1.m4a', 'audio/adhan_2.m4a', 'audio/adhan_3.m4a'];
let audio = null;
function playAdhan() {
  stopAdhan();
  const start = new Date(new Date().getFullYear(), 0, 0), doy = Math.floor((Date.now() - start) / 864e5);
  audio = new Audio(ADHANS[doy % ADHANS.length]); audio.play().catch(() => toast('Appuie encore une fois pour lancer le son'));
}
function stopAdhan() { if (audio) { audio.pause(); audio = null; } }

// ================================================================ ÉCRANS
// ---------- Contrat ----------
function screenContract(readOnly) {
  const c = store.get('contract');
  render(`
    <div class="center" style="font-size:52px">📜</div><h1>Mon engagement</h1><p class="ar" style="font-size:20px;color:var(--muted)">عهد مع نفسي</p>
    <div class="card" style="white-space:pre-line">${esc(CONTRACT)}</div>
    ${readOnly && c ? `<p class="muted center">Signé par ${esc(c.name || 'moi')} le ${esc(c.date)}</p><img src="${c.sig}" style="width:100%;background:#fff;border-radius:16px">
      <button class="btn" id="close">Fermer</button>` : `
    <input id="name" placeholder="Ton prénom (facultatif)" autocomplete="given-name">
    <p class="muted" style="margin:16px 0 0">✍️ Signe avec ton doigt dans le cadre :</p>
    <canvas class="sig" id="sig"></canvas><p class="muted small" style="text-align:right"><a href="#" id="clear" style="color:var(--gold)">Effacer</a></p>
    <label class="chk"><input type="checkbox" id="ok">Je prends cet engagement sincèrement</label>
    <button class="btn" id="sign">🤲 Je m'engage</button>`}`);
  if (readOnly) return on('close', 'click', back);
  const cv = document.getElementById('sig'), g = cv.getContext('2d');
  const fit = () => { const r = cv.getBoundingClientRect(); cv.width = r.width * 2; cv.height = r.height * 2; g.scale(2, 2); g.lineWidth = 3.2; g.lineCap = g.lineJoin = 'round'; g.strokeStyle = '#10291F'; };
  fit(); let ink = false, drawing = false;
  const pt = e => { const r = cv.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; };
  cv.addEventListener('pointerdown', e => { drawing = true; cv.setPointerCapture(e.pointerId); const [x, y] = pt(e); g.beginPath(); g.moveTo(x, y); });
  cv.addEventListener('pointermove', e => { if (!drawing) return; const [x, y] = pt(e); g.lineTo(x, y); g.stroke(); ink = true; });
  cv.addEventListener('pointerup', () => drawing = false);
  on('clear', 'click', e => { e.preventDefault(); g.clearRect(0, 0, cv.width, cv.height); ink = false; });
  on('sign', 'click', () => {
    if (!ink) return toast('Signe d’abord dans le cadre ✍️');
    if (!document.getElementById('ok').checked) return toast('Coche la case pour confirmer');
    const out = document.createElement('canvas'); out.width = cv.width; out.height = cv.height; const o = out.getContext('2d');
    o.fillStyle = '#fff'; o.fillRect(0, 0, out.width, out.height); o.drawImage(cv, 0, 0);
    const d = new Date();
    store.set('contract', { name: document.getElementById('name').value.trim(), date: pad(d.getDate()) + '/' + pad(d.getMonth() + 1) + '/' + d.getFullYear(), sig: out.toDataURL('image/jpeg', 0.7) });
    S.lat == null ? screenSettings(true) : home();
  });
}


// ---------- En-tête : titre + onglets ----------
let TAB = 0;
const TABS = [['🏠', 'Accueil'], ['🏆', 'Classement'], ['🧭', 'Qibla'], ['☰', 'Plus']];
function shell(tab, inner) {
  TAB = tab;
  render(`<div class="top"><div class="brand"><span style="font-size:22px">🕌</span><b>Salat Lock</b><small>${VERSION}</small></div>
    <div class="tabbar">${TABS.map(([i, n], k) => `<button class="${k === tab ? 'on' : ''}" data-tab="${k}"><span>${i}</span>${n}</button>`).join('')}</div></div>${inner}`);
  document.querySelectorAll('[data-tab]').forEach(b => b.addEventListener('click', () => openTab(+b.dataset.tab)));
}
function openTab(k) { [home, () => screenLeaderboard(0), screenQibla, screenMore][k](); }
const back = () => openTab(TAB);

// ---------- 🏠 Accueil ----------
function home() {
  if (!store.get('contract')) return screenContract(false);
  if (S.lat == null) return screenSettings(true);
  const cd = store.get('countdown'); if (cd && Date.now() < cd.end) return screenCountdown(cd);
  const lock = shouldLock(), t = timesFor(new Date()), today = dayKey(new Date()), now = new Date(), nx = nextWindow(), cur = currentWindow();
  const standalone = navigator.standalone || matchMedia('(display-mode: standalone)').matches;
  const left = () => { const s = Math.max(0, Math.round((nx.start - Date.now()) / 1000)); return s >= 3600 ? `dans ${Math.floor(s / 3600)} h ${pad(Math.floor(s % 3600 / 60))} min` : `dans ${pad(Math.floor(s / 60))}:${pad(s % 60)}`; };
  shell(0, `
    ${!standalone ? `<div class="banner">📲 <b>Installe l'app :</b> appuie sur <b>Partager</b> puis <b>« Sur l'écran d'accueil »</b>.</div>` : ''}
    ${lock ? `<div class="card lock" style="border:2px solid var(--gold)"><div class="ar">حان وقت الصلاة</div><b style="font-size:20px">C'est l'heure de ${PR[lock.p].fr}</b>
      <button class="btn" id="pray">🚰 Scanner un lavabo</button></div>` : ''}
    <div class="hero"><div class="muted">📍 ${esc(S.city || 'Ma position')}</div>
      ${nx ? `<div class="k">PROCHAINE PRIÈRE</div><div class="n"><b>${PR[nx.p].fr}</b><span>${PR[nx.p].ar}</span></div>
      <div class="w"><b>${hm(nx.start)}</b><span class="pill" id="left">${left()}</span></div>` : ''}
      ${cur && S.enabled[cur.p] && !isDone(cur.day, cur.p) ? `<div style="color:var(--gold);font-weight:700;margin-top:12px">⏳ ${PR[cur.p].fr} n'est pas encore faite</div>` : ''}</div>
    <div class="chips">${PRAYERS.map(p => { const done = isDone(today, p.k), isNext = nx && nx.p === p.k && nx.day === today;
      const c = !S.enabled[p.k] ? 'off' : done ? 'done' : isNext ? 'next' : '';
      return `<div class="${c}"><b>${!S.enabled[p.k] ? '–' : done ? '✓' : t[p.k] <= now ? '!' : '•'}</b><i>${p.fr}</i><small>${hm(t[p.k])}</small></div>`; }).join('')}</div>
    <p class="muted center small" style="margin:8px 0 0">Lever du soleil ${hm(t.sunrise)} · blocage ${S.grace} min après l'adhan</p>
    ${!lock ? `<button class="btn" id="pray">🚰 Je vais prier maintenant</button>` : ''}
    <div class="tiles2"><div><div class="v">🔥 ${streak()}</div><div class="muted small">jours d'affilée</div></div><div><div class="v">🤲 ${store.get('total', 0)}</div><div class="muted small">prières faites</div></div></div>
    ${excused() ? `<div class="card"><b style="color:var(--gold)">🌙 Mode dispense actif</b><div class="muted">Aucun blocage pendant cette période.</div></div>` : ''}
    ${!store.get('shortcutOk') ? `<div class="card" style="border:2px solid var(--gold)"><b>⚙️ Dernière étape : le Raccourci</b><p class="muted" style="margin:6px 0 0">C'est lui qui bloque TikTok, Instagram… à l'heure de la prière.</p><button class="btn" id="sc">Configurer le Raccourci (5 min)</button></div>` : ''}`);
  document.body.dataset.s = 'home';
  on('pray', 'click', () => { const w = lock || currentWindow(); if (!w) return toast('Aucune prière en cours'); if (isDone(w.day, w.p)) return toast(PR[w.p].fr + ' est déjà faite ✅'); screenScan(w, false); });
  on('sc', 'click', screenShortcut);
  const iv = setInterval(() => { const el = document.getElementById('left'); if (el && nx) { el.textContent = left(); if (nx.start - Date.now() <= 0) home(); } }, 1000);
  cleanup.push(() => clearInterval(iv));
}

// ---------- ☰ Plus ----------
function screenMore() {
  const c = store.get('contract');
  const row = (id, i, t, s) => `<div class="menu" id="${id}"><span class="i">${i}</span><span class="t"><b>${t}</b><small>${s}</small></span><span class="c">›</span></div>`;
  shell(3, `<p class="sec">Mon compte</p>
    ${row('eng', '📜', 'Mon engagement', c ? 'Signé le ' + esc(c.date) : '')}
    ${row('exc', '🌙', 'Mode dispense', excused() ? 'Actif' : 'Règles, maladie, voyage…')}
    <p class="sec">Application</p>
    ${row('sc2', '📲', 'Le Raccourci', store.get('shortcutOk') ? 'Configuré ✅' : 'À configurer pour bloquer les applis')}
    ${row('set', '⚙️', 'Réglages', 'Ville, horaires, blocage')}
    ${row('adh', '🔊', 'Écouter l’adhan', 'Un adhan différent chaque jour')}
    ${row('share', '📤', 'Partager l’app', 'Envoie Salat Lock à un proche')}
    <p class="muted center small" style="margin-top:22px">Salat Lock ${VERSION} · sans pub · aucune donnée personnelle</p>`);
  on('eng', 'click', () => screenContract(true)); on('exc', 'click', screenExcused); on('sc2', 'click', screenShortcut); on('set', 'click', () => screenSettings(false));
  on('adh', 'click', () => { if (audio) { stopAdhan(); toast('Adhan arrêté'); } else { playAdhan(); toast('🔊 Adhan… appuie encore pour arrêter'); } });
  on('share', 'click', () => { const d = { title: 'Salat Lock', text: "Le téléphone se bloque tant que tu n'as pas prié 🕌 Gratuit, sans pub :", url: SITE };
    navigator.share ? navigator.share(d).catch(() => {}) : (navigator.clipboard.writeText(SITE), toast('Lien copié')); });
}

// ---------- Scan du lavabo ----------
let model = null, labels = null;
function loadScript(src) { return new Promise((ok, ko) => { const s = document.createElement('script'); s.src = src; s.onload = ok; s.onerror = ko; document.head.appendChild(s); }); }
async function loadModel() {
  if (model) return;
  if (!window.tflite) { await loadScript('vendor/tf-core.min.js'); await loadScript('vendor/tf-backend-cpu.min.js'); await loadScript('vendor/tf-tflite.min.js'); }
  tflite.setWasmPath('vendor/wasm/');
  model = await tflite.loadTFLiteModel('model/sink_model.tflite');
  labels = (await (await fetch('model/labels.txt')).text()).split('\n');
}
function sinkScore(canvas) {
  const out = tf.tidy(() => model.predict(tf.expandDims(tf.browser.fromPixels(canvas), 0)));
  const raw = out.dataSync(); out.dispose();
  // sortie quantifiée (logits) -> probabilités
  const SC = 0.09889253973960876, ZP = 58; let mx = -1e9; const l = new Float32Array(raw.length);
  for (let i = 0; i < raw.length; i++) { l[i] = SC * (raw[i] - ZP); if (l[i] > mx) mx = l[i]; }
  let sum = 0; for (let i = 0; i < l.length; i++) { l[i] = Math.exp(l[i] - mx); sum += l[i]; }
  const p = i => i >= 0 ? l[i] / sum : 0; let best = 0; for (let i = 1; i < l.length; i++) if (l[i] > l[best]) best = i;
  const score = p(labels.indexOf('washbasin')) + 0.3 * (p(labels.indexOf('soap dispenser')) + p(labels.indexOf('tub')) + p(labels.indexOf('bathtub')));
  return { score: Math.min(1, score), top: labels[best], topP: p(best) };
}
async function screenScan(w, gate, sens = S.sens, cdMin = S.countdown) {
  stopAdhan();
  render(`<button class="back" id="back">‹ Retour</button><h1 style="font-size:24px">🚰 Montre un lavabo</h1>
    <p class="center muted" style="margin:4px 0">Prière de ${PR[w.p].fr} · ${PR[w.p].ar}</p>
    <div class="cam"><video id="v" playsinline muted autoplay></video><div class="frame" id="fr"></div></div>
    <p class="center" style="font-weight:800;font-size:18px;margin:12px 0 0" id="st">Chargement de la caméra…</p>
    <div class="bar"><i id="bar"></i></div><p class="center muted small" id="seen"></p>
    <p class="center muted small">Astuce : recule un peu pour avoir tout le lavabo dans le cadre, avec de la lumière.</p>`);
  on('back', 'click', () => gate ? gateScreen() : back());
  const v = document.getElementById('v'), st = document.getElementById('st');
  let stream = null, alive = true, progress = 0;
  cleanup.push(() => { alive = false; if (stream) stream.getTracks().forEach(t => t.stop()); });
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment', width: { ideal: 640 } }, audio: false });
    if (!alive) return stream.getTracks().forEach(t => t.stop());
    v.srcObject = stream; await v.play().catch(() => {});
    st.textContent = 'Chargement de la reconnaissance…'; await loadModel(); st.textContent = 'Je cherche un lavabo…';
  } catch (e) { st.textContent = '⚠️ ' + (e.name === 'NotAllowedError' ? 'Autorise la caméra dans Réglages › Safari › Caméra' : 'Caméra indisponible : ' + e.message); return; }
  const c = document.createElement('canvas'); c.width = c.height = 224; const g = c.getContext('2d', { willReadFrequently: true });
  const thr = 0.45 - sens * 0.003;
  const loop = () => {
    if (!alive) return;
    if (v.videoWidth) {
      const s = Math.min(v.videoWidth, v.videoHeight);
      g.drawImage(v, (v.videoWidth - s) / 2, (v.videoHeight - s) / 2, s, s, 0, 0, 224, 224);
      const r = sinkScore(c), found = r.score >= thr;
      progress = Math.max(0, Math.min(1, progress + (found ? 0.09 : -0.05)));
      document.getElementById('bar').style.width = (progress * 100) + '%';
      document.getElementById('fr').classList.toggle('ok', found);
      st.textContent = found ? '✅ Lavabo détecté… ne bouge pas' : progress > 0 ? 'Presque… garde le lavabo dans le cadre' : 'Je cherche un lavabo…';
      document.getElementById('seen').textContent = `Je vois : ${r.top} (${Math.round(r.topP * 100)} %) · lavabo ${Math.round(r.score * 100)} %`;
      if (progress >= 1) {
        alive = false; if (navigator.vibrate) navigator.vibrate(200);
        const cd = { end: Date.now() + Math.max(3, cdMin) * 60000, p: w.p, day: w.day, gate };
        store.set(gate ? 'gateCountdown' : 'countdown', cd);
        return setTimeout(() => screenCountdown(cd), 300);
      }
    }
    setTimeout(loop, 160);
  };
  loop();
}

// ---------- Compte à rebours ----------
function screenCountdown(cd) {
  const p = PR[cd.p];
  render(`<div class="lock"><div style="font-size:26px;font-weight:800;color:var(--gold);margin-top:10px">🤲 Prie maintenant</div>
    <div class="muted">${p.fr} · ${p.ar}</div><div class="big" id="t">--:--</div>
    <div class="muted">${cd.gate ? 'Tes applis se débloquent à la fin du compte à rebours.' : 'La prière est comptée à la fin du compte à rebours.'}</div>
    ${compassHTML()}<p class="muted" id="qtxt">Appuie sur la boussole pour l'activer</p>
    <div class="card" style="text-align:left"><b style="color:var(--gold);font-size:13px">Invocation après les ablutions</b>
      <p class="ar" style="font-size:20px;line-height:1.7;margin:8px 0">${DUA_AR}</p><p class="muted small" style="margin:0">${DUA_FR}</p></div></div>`);
  startCompass();
  const el = document.getElementById('t');
  const tick = async () => {
    const left = Math.max(0, Math.round((cd.end - Date.now()) / 1000));
    el.textContent = pad(Math.floor(left / 60)) + ':' + pad(left % 60);
    if (left > 0) return;
    clearInterval(iv); store.del(cd.gate ? 'gateCountdown' : 'countdown');
    if (cd.gate) {
      try { await rpc('ios_done', { p_token: cd.token, p_day: cd.day, p_prayer: cd.p }, false); } catch (e) { return gateFinish(false, friendly(e), cd); }
      return gateFinish(true, null, cd);
    }
    markDone(cd.day, cd.p);
    rpc('ios_done', { p_token: token(), p_day: cd.day, p_prayer: cd.p }).catch(() => {}).then(flush).catch(() => {});
    render(`<div class="lock"><div class="em">🤲</div><h1>تقبل الله</h1><p style="font-size:18px">Qu'Allah accepte ta prière</p>
      <div class="card stats"><div><div class="v">🔥 ${streak()}</div><div class="muted">jours d'affilée</div></div><div><div class="v">🤲 ${store.get('total', 0)}</div><div class="muted">prières</div></div></div>
      <button class="btn" id="h">Retour à l'accueil</button></div>`);
    on('h', 'click', home);
  };
  const iv = setInterval(tick, 1000); cleanup.push(() => clearInterval(iv)); tick();
}

// ---------- Boussole (Qibla) ----------
function compassHTML() {
  return `<div class="compass" id="cmp"><svg viewBox="0 0 240 240">
    <circle cx="120" cy="120" r="96" fill="#1A4D3D" stroke="#A9C2B7" stroke-width="2" id="cbg"/>
    <g id="rose"><text x="120" y="44" fill="#A9C2B7" font-size="16" text-anchor="middle" font-weight="700">N</text></g>
    <g id="arrow"><polygon points="120,30 106,92 134,92" fill="#E8C468"/><rect x="117" y="88" width="6" height="74" fill="#E8C468"/>
      <text x="120" y="22" font-size="22" text-anchor="middle">🕋</text></g>
    <rect x="118" y="20" width="4" height="18" fill="#fff"/><circle cx="120" cy="120" r="6" fill="#E8C468"/></svg></div>`;
}
function startCompass() {
  const q = qiblaDeg(), cmp = document.getElementById('cmp'); if (!cmp) return;
  const txt = document.getElementById('qtxt');
  let heading = null;
  const draw = () => {
    if (heading == null || q == null) return;
    document.getElementById('rose').setAttribute('transform', `rotate(${-heading} 120 120)`);
    document.getElementById('arrow').setAttribute('transform', `rotate(${q - heading} 120 120)`);
    let diff = Math.abs(q - heading) % 360; if (diff > 180) diff = 360 - diff;
    document.getElementById('cbg').setAttribute('fill', diff < 5 ? '#2E9E6B' : '#1A4D3D');
    if (txt) txt.textContent = diff < 5 ? '✅ Tu es face à la Qibla' : `Qibla : ${Math.round(q)}° · tourne-toi vers la flèche dorée`;
  };
  const onOri = e => {
    let h = e.webkitCompassHeading != null ? e.webkitCompassHeading : (e.absolute && e.alpha != null ? 360 - e.alpha : null);
    if (h == null) return;
    heading = heading == null ? h : heading + (((h - heading + 540) % 360) - 180) * 0.2;
    if (e.webkitCompassAccuracy > 25 && txt) txt.textContent = '🔄 Boussole imprécise : fais un 8 avec ton téléphone';
    else draw();
  };
  const startListen = () => { addEventListener('deviceorientation', onOri); addEventListener('deviceorientationabsolute', onOri); cleanup.push(() => { removeEventListener('deviceorientation', onOri); removeEventListener('deviceorientationabsolute', onOri); }); };
  if (q != null && txt) txt.textContent = `Qibla : ${Math.round(q)}° depuis le nord · appuie sur la boussole`;
  if (typeof DeviceOrientationEvent !== 'undefined' && DeviceOrientationEvent.requestPermission) {
    cmp.addEventListener('click', () => DeviceOrientationEvent.requestPermission().then(r => r === 'granted' ? startListen() : toast('Boussole refusée')).catch(() => {}), { once: true });
  } else startListen();
}
function screenQibla() {
  shell(2, `<h2 class="center" style="color:#fff;font-size:20px">Direction de la Qibla</h2>${compassHTML()}
    ${qiblaDeg() != null ? `<div class="center" style="font-size:40px;font-weight:800;color:var(--gold)">${Math.round(qiblaDeg())}°</div><p class="center muted" style="margin:0">depuis le nord · ${esc(S.city || 'ma position')}</p>` : ''}
    <p class="center" id="qtxt"></p>
    <div class="card"><b style="color:var(--gold)">Conseils</b><div class="muted" style="margin-top:6px">• Appuie sur la boussole pour l'activer (l'iPhone demande l'autorisation).<br>• Tiens le téléphone à plat.<br>• Éloigne-toi des aimants, enceintes et ordinateurs.<br>• Si la flèche bouge bizarrement : fais un 8 dans l'air avec ton téléphone.</div></div>`);
  startCompass();
}

// ---------- Classement ----------
async function screenLeaderboard(tab) {
  shell(1, `<div id="lbz"><p class="center muted">Chargement…</p></div>`);
  let prof = null, rows = [], err = null;
  try {
    await flush().catch(() => {});
    prof = await rpc('my_profile', {});
    store.set('hasProfile', !!prof);
    rows = await rpc(tab === 1 && prof ? 'friends_leaderboard' : 'global_leaderboard', tab === 1 && prof ? {} : { p_limit: 100 });
  } catch (e) { err = friendly(e); }
  const z = document.getElementById('lbz'); if (!z || TAB !== 1) return;
  const podium = tab === 0 && rows.length >= 3;
  const medal = r => ['🥇', '🥈', '🥉'][r.rank - 1] || r.rank;
  z.innerHTML = `${err ? `<p class="center" style="color:var(--red)">⚠️ ${esc(err)}</p>` : ''}
    ${!prof ? `<div class="card" style="border:2px solid var(--gold)"><b style="font-size:19px">Rejoins le classement 🔥</b><p class="muted">Choisis un pseudo. Pas d'e-mail, pas de numéro : seuls ton pseudo, ta série et ton nombre de prières sont envoyés.</p>
      <input id="ps" maxlength="20" placeholder="Pseudo (3 à 20 caractères)"><button class="btn" id="join">Rejoindre</button></div>` : `
      <div class="mecard"><div class="h"><div><b>${esc(prof.pseudo)}</b><div class="r">Rang #${prof.rank} sur ${prof.players}</div></div><span class="big2">#${prof.rank}</span></div>
      <div class="stats" style="margin-top:12px"><div><div class="v" style="font-size:20px">🔥 ${prof.streak}</div><div class="muted small">série</div></div><div><div class="v" style="font-size:20px">🏅 ${prof.best_streak}</div><div class="muted small">record</div></div><div><div class="v" style="font-size:20px">🤲 ${prof.total}</div><div class="muted small">prières</div></div></div>
      <div class="code"><span>Code ami : ${esc(prof.friend_code)}</span><button id="shc">📤 Partager</button></div></div>`}
    <div class="seg"><button class="${tab === 0 ? 'on' : ''}" id="t0">🌍 Mondial</button><button class="${tab === 1 ? 'on' : ''}" id="t1">👥 Amis</button></div>
    ${tab === 1 && prof ? `<div class="copy" style="margin-top:12px"><input id="fc" placeholder="Code ami (6 caractères)" style="text-transform:uppercase"><button id="add">Ajouter</button></div>` : ''}
    ${podium ? `<div class="podium">${[[rows[1], 2], [rows[0], 1], [rows[2], 3]].map(([r, k]) => `<div class="p${k} ${r.is_me ? 'me' : ''}"><div class="m">${['🥇', '🥈', '🥉'][k - 1]}</div><b>${esc(r.pseudo)}</b><div class="s">🔥 ${r.streak}</div><div class="bar2"></div></div>`).join('')}</div>` : ''}
    ${(podium ? rows.slice(3) : rows).length ? `<div class="card lb">${(podium ? rows.slice(3) : rows).map(r => `<div class="r ${r.is_me ? 'me' : ''}" data-p="${esc(r.pseudo)}"><span class="n">${medal(r)}</span><span class="p">${esc(r.pseudo)}${r.is_me ? ' (moi)' : ''}</span><span class="s">🔥 ${r.streak}</span><span class="c">${r.total}</span></div>`).join('')}</div>`
      : !rows.length ? `<p class="muted center">${tab === 0 ? 'Personne pour l’instant. Sois le premier ! 🥇' : 'Ajoute des amis avec leur code 👆'}</p>` : ''}
    <p class="muted small">La série 🔥 compte les jours d'affilée avec les 5 prières faites. Les jours de dispense ne la cassent pas.${tab === 1 ? ' Appui long sur un ami pour le retirer.' : ''}</p>
    ${prof ? `<p class="center"><a href="#" id="del" style="color:var(--red);font-size:13px">Supprimer mon profil</a></p>` : ''}`;
  on('t0', 'click', () => screenLeaderboard(0));
  on('t1', 'click', () => prof ? screenLeaderboard(1) : toast('Choisis d’abord un pseudo'));
  on('join', 'click', async () => {
    const s = document.getElementById('ps').value.trim();
    if (s.length < 3 || s.length > 20) return toast('3 à 20 caractères');
    if (!/^[\p{L}\p{N}_. -]+$/u.test(s)) return toast('Lettres, chiffres, espace, _ . - seulement');
    try {
      await rpc('set_pseudo', { p_pseudo: s }); store.set('hasProfile', true);
      const today = new Date(), q = store.get('queue', []);
      for (let k = 0; k < 3; k++) PRAYERS.forEach(p => { const d = dayKey(addDays(today, -k)); if (isDone(d, p.k)) q.push([d, p.k]); });
      store.set('queue', q); await sync(); screenLeaderboard(0);
    } catch (e) { toast(friendly(e)); }
  });
  on('add', 'click', async () => { try { const n = await rpc('add_friend', { p_code: document.getElementById('fc').value.trim() }); toast('✅ ' + n + ' ajouté !'); screenLeaderboard(1); } catch (e) { toast(friendly(e)); } });
  on('shc', 'click', () => { const t = `Ajoute-moi sur Salat Lock pour qu'on se motive à prier 🤲 Mon code ami : ${prof.friend_code}\n${SITE}`; navigator.share ? navigator.share({ text: t }).catch(() => {}) : (navigator.clipboard.writeText(t), toast('Copié')); });
  on('del', 'click', async e => { e.preventDefault(); if (!confirm('Supprimer ton pseudo, ta série et ton historique en ligne ?')) return;
    try { await rpc('delete_my_account', {}); store.del('session'); store.set('hasProfile', false); toast('Profil supprimé'); sync(); screenLeaderboard(0); } catch (er) { toast(friendly(er)); } });
  if (tab === 1) document.querySelectorAll('.lb .r:not(.me)').forEach(el => {
    let h; el.addEventListener('pointerdown', () => h = setTimeout(async () => { if (confirm('Retirer ' + el.dataset.p + ' de tes amis ?')) { await rpc('remove_friend', { p_pseudo: el.dataset.p }).catch(() => {}); screenLeaderboard(1); } }, 700));
    ['pointerup', 'pointerleave'].forEach(ev => el.addEventListener(ev, () => clearTimeout(h)));
  });
}

// ---------- Réglages ----------
function screenSettings(first) {
  render(`${first ? '' : '<button class="back" id="back">‹ Retour</button>'}<h1>⚙️ Réglages</h1>
    <h2>📍 Ma ville</h2><p class="muted" id="loc">${S.lat != null ? `Actuelle : ${esc(S.city)} (${S.lat.toFixed(3)}, ${S.lng.toFixed(3)})` : 'Aucune position enregistrée'}</p>
    <button class="btn" id="gps">📡 Utiliser ma position</button>
    <select id="city"><option value="">— Ou choisis une ville —</option>${Object.keys(CITIES).map(c => `<option>${c}</option>`).join('')}</select>
    <h2>🧮 Méthode de calcul</h2><select id="meth">${Object.entries(METHODS).map(([k, v]) => `<option value="${k}" ${k === S.method ? 'selected' : ''}>${v}</option>`).join('')}</select>
    <label class="chk"><input type="checkbox" id="han" ${S.hanafi ? 'checked' : ''}>Asr selon l'école hanafite</label>
    <h2>🔒 Blocage</h2>
    <p>Délai après l'adhan : <b id="gv">${S.grace}</b> min</p><input type="range" id="gr" min="0" max="60" value="${S.grace}">
    <p>Compte à rebours pour prier : <b id="cv">${S.countdown}</b> min</p><input type="range" id="cd" min="3" max="30" value="${S.countdown}">
    <p>Sensibilité du scan : <b id="sv">${S.sens}</b> %</p><input type="range" id="se" min="0" max="100" value="${S.sens}">
    <h2>🕌 Prières qui bloquent</h2>${PRAYERS.map(p => `<label class="chk"><input type="checkbox" data-p="${p.k}" ${S.enabled[p.k] ? 'checked' : ''}>${p.fr} · ${p.ar}</label>`).join('')}
    <button class="btn" id="save">${first ? 'Continuer' : 'Enregistrer'}</button>
    <h2>🎙️ Crédits des adhans</h2><p class="muted small">Enregistrements libres de droits (Wikimedia Commons) : « Beautiful adhan » — Adam-synagda — CC0 ; « Azan » — Andrewler — CC BY-SA 4.0 (coupé avant la dua) ; « The Adhan » — Atcovi (récitation d'Aaqib Azeez) — CC BY-SA 4.0. Versions modifiées sous CC BY-SA 4.0.</p>
    <h2>ℹ️ Confidentialité</h2><p class="muted small">Pour que le Raccourci sache quand bloquer, tes horaires de prière (calculés sur le téléphone) sont envoyés avec un code aléatoire, sans nom ni position exacte. Le classement n'envoie que ton pseudo, ta série et ton nombre de prières, et seulement si tu le rejoins. Un compteur anonyme d'installations est aussi envoyé.</p>`);
  if (!first) on('back', 'click', back);
  const setLoc = (lat, lng, city) => { S.lat = lat; S.lng = lng; S.city = city; document.getElementById('loc').textContent = `Actuelle : ${city} (${lat.toFixed(3)}, ${lng.toFixed(3)})`; toast('Position enregistrée : ' + city); };
  on('gps', 'click', () => navigator.geolocation ? navigator.geolocation.getCurrentPosition(p => setLoc(p.coords.latitude, p.coords.longitude, 'Ma position'),
    () => toast('Position refusée : choisis ta ville dans la liste'), { enableHighAccuracy: false, timeout: 15000 }) : toast('Position indisponible'));
  on('city', 'change', e => { const c = e.target.value; if (c) setLoc(CITIES[c][0], CITIES[c][1], c); });
  [['gr', 'gv'], ['cd', 'cv'], ['se', 'sv']].forEach(([i, o]) => on(i, 'input', e => document.getElementById(o).textContent = e.target.value));
  on('save', 'click', () => {
    if (S.lat == null) return toast('Choisis d’abord ta ville 📍');
    S.method = document.getElementById('meth').value; S.hanafi = document.getElementById('han').checked;
    S.grace = +document.getElementById('gr').value; S.countdown = Math.max(3, +document.getElementById('cd').value); S.sens = +document.getElementById('se').value;
    document.querySelectorAll('[data-p]').forEach(c => S.enabled[c.dataset.p] = c.checked);
    saveSettings(); sync(); toast('Enregistré ✅'); first ? home() : back();
  });
}

function screenExcused() {
  render(`<button class="back" id="back">‹ Retour</button><h1>🌙 Mode dispense</h1><p class="muted center">Règles, maladie, voyage… Aucun blocage pendant la période choisie.</p>
    ${[1, 3, 5, 7, 10].map(n => `<button class="btn alt" data-n="${n}">${n} jour${n > 1 ? 's' : ''}</button>`).join('')}<button class="btn red" data-n="0">Désactiver le mode dispense</button>`);
  on('back', 'click', back);
  document.querySelectorAll('[data-n]').forEach(b => b.addEventListener('click', () => {
    const n = +b.dataset.n; store.set('excusedUntil', n ? Date.now() + n * 864e5 : 0); sync(); toast(n ? `Dispense pour ${n} jour(s)` : 'Mode dispense désactivé'); home(); }));
}

// ---------- Guide du Raccourci ----------
function gateUrl() { return `${SB_URL}/rest/v1/rpc/ios_gate?p_token=${token()}&apikey=${SB_KEY}`; }
function lockUrl() { return `${APP_URL}?t=${token()}`; }
function screenShortcut() {
  sync();
  const box = (id, val) => `<div class="copy"><input readonly id="${id}" value="${esc(val)}"><button data-c="${id}">Copier</button></div>`;
  render(`<button class="back" id="back">‹ Retour</button><h1>📲 Le Raccourci</h1>
    <p class="muted center">C'est lui qui t'envoie sur Salat Lock quand tu ouvres TikTok, Instagram… à l'heure de la prière. À faire une seule fois (5 min).</p>
    <div class="card"><b>Partie 1 — Créer le raccourci</b>
      <div class="step"><span class="k">1</span><span>Ouvre l'app <b>Raccourcis</b> (icône bleue et rose) → onglet <b>Raccourcis</b> → <b>+</b> en haut à droite.</span></div>
      <div class="step"><span class="k">2</span><span>Appuie sur <b>Ajouter une action</b>, cherche <b>« Obtenir le contenu de l'URL »</b> et ajoute-la. Remplace « URL » par le <b>lien n°1</b> :</span></div>
      ${box('u1', gateUrl())}
      <div class="step"><span class="k">3</span><span>Ajoute l'action <b>« Si »</b>. Règle-la sur : <b>Contenu de l'URL</b> · <b>contient</b> · <b>true</b>.</span></div>
      <div class="step"><span class="k">4</span><span>Entre « Si » et « Sinon », ajoute l'action <b>« Ouvrir les URL »</b> et mets le <b>lien n°2</b> :</span></div>
      ${box('u2', lockUrl())}
      <div class="step"><span class="k">5</span><span>En haut, nomme le raccourci <b>Salat Lock</b> puis appuie sur <b>OK</b>.</span></div></div>
    <div class="card"><b>Partie 2 — L'automatisation</b>
      <div class="step"><span class="k">1</span><span>Toujours dans Raccourcis : onglet <b>Automatisation</b> → <b>+</b> → <b>App</b>.</span></div>
      <div class="step"><span class="k">2</span><span><b>Choisir</b> : coche TikTok, Instagram, Snapchat, YouTube, tes jeux… → <b>Terminé</b>.</span></div>
      <div class="step"><span class="k">3</span><span>Coche <b>Est ouverte</b> et choisis <b>Exécuter immédiatement</b> → <b>Suivant</b>.</span></div>
      <div class="step"><span class="k">4</span><span>Choisis le raccourci <b>Salat Lock</b>. C'est fini ✅</span></div></div>
    <button class="btn" id="test">🧪 Tester (bloque 2 minutes)</button>
    <p class="muted small">Après « Tester », ouvre une des applis cochées : Salat Lock doit s'ouvrir. Le test s'arrête tout seul après 2 minutes.</p>
    <button class="btn alt" id="done">✅ C'est configuré</button>
    <p class="muted small">⚠️ Garde ces liens pour toi : ils contiennent ton code personnel.</p>`);
  on('back', 'click', back);
  document.querySelectorAll('[data-c]').forEach(b => b.addEventListener('click', () => { const i = document.getElementById(b.dataset.c); i.select();
    (navigator.clipboard ? navigator.clipboard.writeText(i.value) : Promise.reject()).then(() => toast('Lien copié ✅')).catch(() => { document.execCommand('copy'); toast('Lien copié ✅'); }); }));
  on('test', 'click', async () => {
    const now = Date.now();
    try { await rpc('ios_register', { p_token: token(), p_windows: [{ p: 'DHUHR', day: '2000-01-01', test: true, lock_at: new Date(now - 1000).toISOString(), end_at: new Date(now + 120000).toISOString() }], p_excused_until: null });
      toast('Test lancé ! Ouvre maintenant une des applis cochées'); setTimeout(sync, 125000);
    } catch (e) { toast(friendly(e)); }
  });
  on('done', 'click', () => { store.set('shortcutOk', true); toast('Parfait 👍'); home(); });
}

// ================================================================ PAGE DE BLOCAGE (ouverte par le Raccourci)
let gateToken = null;
async function gateScreen() {
  const run = store.get('gateCountdown');
  if (run && run.token === gateToken && Date.now() < run.end) return screenCountdown(run);
  render('<p class="center muted" style="margin-top:40vh">Vérification…</p>');
  let st;
  try { st = await rpc('ios_state', { p_token: gateToken }, false); } catch (e) {
    return render(`<div class="lock"><div class="em">📡</div><h1>Pas de connexion</h1><p class="muted">${esc(friendly(e))}</p><button class="btn" id="r">Réessayer</button></div>`), on('r', 'click', gateScreen);
  }
  const w = st.current;
  if (!w) return render(`<div class="lock"><div class="em">✅</div><h1>C'est bon !</h1><p style="font-size:18px">Aucune prière en attente.<br>Tu peux retourner sur ton application.</p><p class="muted">Qu'Allah accepte tes prières 🤲</p></div>`);
  if (w.test) return render(`<div class="lock"><div class="em">🎉</div><h1>Le Raccourci marche !</h1><p style="font-size:18px">À l'heure de la prière, c'est cet écran qui s'ouvrira.</p><p class="muted">Le test s'arrête tout seul dans 2 minutes.</p></div>`);
  const p = PR[w.p], q = QUOTES[new Date().getDate() % QUOTES.length];
  render(`<div class="lock"><div class="em">🕌</div><div class="ar" style="font-size:32px;font-weight:700">حان وقت الصلاة</div>
    <h1 style="color:#fff;font-size:24px;margin-top:6px">C'est l'heure de ${p.fr} · ${p.ar}</h1>
    <div class="card" style="text-align:left">${esc(q)}</div>
    <button class="btn" id="scan">🚰 Scanner un lavabo</button>
    <p class="muted">Fais tes ablutions, montre un lavabo à la caméra, puis prie pendant le compte à rebours.</p>
    <button class="btn alt" id="adh">🔊 Écouter l'adhan</button>
    <a class="btn alt" href="tel:112">📞 Urgence (112)</a></div>`);
  on('adh', 'click', () => audio ? stopAdhan() : playAdhan());
  on('scan', 'click', () => screenScan({ p: w.p, day: w.day }, true, w.sens ?? 50, w.cd ?? 10));
}
function gateFinish(ok, err, cd) {
  if (!ok) { render(`<div class="lock"><div class="em">📡</div><h1>Presque…</h1><p class="muted">${esc(err)}</p><button class="btn" id="r">Réessayer</button></div>`);
    return on('r', 'click', async () => { try { await rpc('ios_done', { p_token: cd.token, p_day: cd.day, p_prayer: cd.p }, false); gateFinish(true, null, cd); } catch (e) { toast(friendly(e)); } }); }
  render(`<div class="lock"><div class="em">🤲</div><h1>تقبل الله</h1><p style="font-size:18px">Qu'Allah accepte ta prière.<br><b>Tes applis sont débloquées.</b></p>
    <p class="muted">Tu peux fermer cette page et retourner sur ton application.</p></div>`);
}

// ================================================================ DÉMARRAGE
(function start() {
  const t = new URLSearchParams(location.search).get('t');
  if (t && /^[0-9a-f-]{36}$/i.test(t)) {
    gateToken = t;
    // tout compte à rebours lancé depuis cette page appartient à ce code
    const origSet = store.set; store.set = (k, v) => { if (k === 'gateCountdown' && v && !v.token) v.token = gateToken; origSet(k, v); };
    return gateScreen();
  }
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
  home(); sync();
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') { sync(); if (document.body.dataset.s === 'home') home(); } });
})();
