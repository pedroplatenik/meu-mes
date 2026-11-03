// Meu Mês: guarda o app no iPhone para abrir sem internet.
var CACHE = 'meumes-v2';
var ARQUIVOS = ['./', './index.html', './manifest.webmanifest', './icone-180.png', './icone-192.png', './icone-512.png'];
self.addEventListener('install', function (e) {
  e.waitUntil(caches.open(CACHE).then(function (c) { return c.addAll(ARQUIVOS); }));
  self.skipWaiting();
});
self.addEventListener('activate', function (e) {
  e.waitUntil(caches.keys().then(function (ks) {
    return Promise.all(ks.filter(function (k) { return k !== CACHE; }).map(function (k) { return caches.delete(k); }));
  }).then(function () { return self.clients.claim(); }));
});
// Primeiro tenta a internet (pega versões novas); sem internet, usa a cópia guardada.
self.addEventListener('fetch', function (e) {
  var req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return;
  e.respondWith(fetch(req).then(function (r) {
    if (r && r.ok) { var cp = r.clone(); caches.open(CACHE).then(function (c) { c.put(req, cp); }); }
    return r;
  }).catch(function () {
    return caches.match(req).then(function (r) { return r || caches.match('./index.html'); });
  }));
});
