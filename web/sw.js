/* 설치(PWA)용 서비스 워커: 항상 인터넷에서 최신 화면을 받고(브라우저 임시 보관도 건너뜀), 연결이 안 될 때만 보관본을 쓴다.
   → 새로고침·프로그램 재시작·'업데이트' 버튼만으로 새 버전이 바로 적용된다. */
const C = 'nine-family-web-2';
self.addEventListener('install', e => { self.skipWaiting(); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== C).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  const r = e.request; if (r.method !== 'GET' || new URL(r.url).origin !== location.origin) return;
  e.respondWith(fetch(r.url, {cache: 'no-cache', credentials: 'same-origin'}).then(res => { if (res.ok) { const cp = res.clone(); caches.open(C).then(c => c.put(r, cp)); } return res; }).catch(() => caches.match(r).then(m => m || caches.match('index.html'))));
});
