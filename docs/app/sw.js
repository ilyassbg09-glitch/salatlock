// Cache hors ligne de l'app (les appels au serveur ne sont jamais mis en cache).
const CACHE = 'salatlock-web-1.1.0';
const FILES = ['./', 'index.html', 'style.css', 'app.js', 'manifest.webmanifest', 'fonts/inter-latin-400-normal.woff2', 'fonts/inter-latin-700-normal.woff2', 'fonts/inter-latin-800-normal.woff2', 'icons/icon-180.png', 'icons/icon-192.png',
  'vendor/adhan.min.js', 'vendor/tf-core.min.js', 'vendor/tf-backend-cpu.min.js', 'vendor/tf-tflite.min.js',
  'vendor/wasm/tflite_web_api_cc.js', 'vendor/wasm/tflite_web_api_cc.wasm', 'vendor/wasm/tflite_web_api_cc_simd.js',
  'vendor/wasm/tflite_web_api_cc_simd.wasm', 'model/sink_model.tflite', 'model/labels.txt'];
self.addEventListener('install', e => e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting())));
self.addEventListener('activate', e => e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())));
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url);
  if (u.origin !== location.origin || e.request.method !== 'GET') return;
  e.respondWith(caches.match(e.request, { ignoreSearch: true }).then(r => r || fetch(e.request).then(resp => {
    if (resp.ok && u.pathname.includes('/audio/')) { const cl = resp.clone(); caches.open(CACHE).then(c => c.put(e.request, cl)); }
    return resp;
  })));
});
