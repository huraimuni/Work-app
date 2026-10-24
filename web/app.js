'use strict';
/* 나인패밀리 웹앱 — 앱과 같은 Firebase(nine-family)를 쓴다. 문서는 {j: JSON문자열, u: 시각} 형식. */
const CFG = {apiKey: 'AIzaSyCsA-M6QGGGk0owyDXzziv1X79TqF5NptI', authDomain: 'nine-family.firebaseapp.com', projectId: 'nine-family',
  storageBucket: 'nine-family.firebasestorage.app', messagingSenderId: '48232341344', appId: '1:48232341344:web:19be016c9c1c1de5a34805'};
const VERSION = '웹 0.0.7';
const DEMO = location.hash === '#demo';
const COLLS = ['users', 'tasks', 'notices', 'projects', 'events', 'merchants', 'rooms', 'msgs', 'profiles', 'mlogs', 'config', 'stock', 'contracts', 'deposits', 'costs'];
const TYPES = ['신규 설치', 'AS', '영업 방문', '서류 접수', '카드사 서류', '기타'];
const KINDS = ['방문A/S', '원격A/S', '전화응대', '기타'];
const logKind = k => (k === 'AS' || k === '방문' || k === '방문 A/S') ? '방문A/S' : k === '원격 A/S' ? '원격A/S' : k === '전화 응대' ? '전화응대' : k;
const logReq = g => (g.req !== undefined || g.res !== undefined) ? (g.req || '') : (g.text || '');
const logRes = g => g.res || '';
const logAll = g => (g.mname || '') + ' ' + logReq(g) + ' ' + logRes(g) + ' ' + (g.text || '');
const ST = {'지시됨': '미확인', '확인함': '진행중', '진행중': '진행중', '보류': '보류', '완료': '완료'};
const $ = (s, r) => (r || document).querySelector(s);
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));

let auth, fs;
const S = {logLimit: 30, lmq: '', me: null, uid: 0, phone: '', tab: 'task', sub: '', data: {}, pushed: {}, taskFilter: 0, showAll: false, projFilter: 0,
  room: 0, dmDraft: null, calY: 0, calM: 0, calSel: '', mq: '', mlimit: 50, allQ: '', chatQ: ''};
COLLS.forEach(c => S.data[c] = new Map());

// ---------- 작은 도구 ----------
function toast(s) {
  const t = $('#toast');
  t.textContent = s; t.classList.add('on');
  clearTimeout(toast.h); toast.h = setTimeout(() => t.classList.remove('on'), 2400);
}
const pad = n => String(n).padStart(2, '0');
function nowStr() { const d = new Date(); return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()); }
const todayStr = () => nowStr().slice(0, 10);
function e164(s) { const d = String(s || '').replace(/[^0-9+]/g, ''); if (d.startsWith('+')) return d; if (d.startsWith('0')) return '+82' + d.slice(1); if (d.startsWith('82')) return '+' + d; return '+82' + d; }
const CHO = 'ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';
function cho(s) { let o = ''; for (const ch of s) { const c = ch.charCodeAt(0); o += c >= 0xAC00 && c <= 0xD7A3 ? CHO[Math.floor((c - 0xAC00) / 588)] : ch; } return o; }
// 한글을 자판 한 번 누르는 단위(자모)로 푼다. '삼영' → 'ㅅㅏㅁㅇㅕㅇ', '과' → 'ㄱㅗㅏ', '닭' → 'ㄷㅏㄹㄱ'
// 이렇게 비교하면 치는 중인 글자('사', '삼여', '삼영ㅍ')도 '삼영푸드'에 걸린다.
const JUNG = ['ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅗㅏ', 'ㅗㅐ', 'ㅗㅣ', 'ㅛ', 'ㅜ', 'ㅜㅓ', 'ㅜㅔ', 'ㅜㅣ', 'ㅠ', 'ㅡ', 'ㅡㅣ', 'ㅣ'];
const JONG = ['', 'ㄱ', 'ㄲ', 'ㄱㅅ', 'ㄴ', 'ㄴㅈ', 'ㄴㅎ', 'ㄷ', 'ㄹ', 'ㄹㄱ', 'ㄹㅁ', 'ㄹㅂ', 'ㄹㅅ', 'ㄹㅌ', 'ㄹㅍ', 'ㄹㅎ', 'ㅁ', 'ㅂ', 'ㅂㅅ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'];
const JAMO2 = {'ㄳ': 'ㄱㅅ', 'ㄵ': 'ㄴㅈ', 'ㄶ': 'ㄴㅎ', 'ㄺ': 'ㄹㄱ', 'ㄻ': 'ㄹㅁ', 'ㄼ': 'ㄹㅂ', 'ㄽ': 'ㄹㅅ', 'ㄾ': 'ㄹㅌ', 'ㄿ': 'ㄹㅍ', 'ㅀ': 'ㄹㅎ', 'ㅄ': 'ㅂㅅ', 'ㅘ': 'ㅗㅏ', 'ㅙ': 'ㅗㅐ', 'ㅚ': 'ㅗㅣ', 'ㅝ': 'ㅜㅓ', 'ㅞ': 'ㅜㅔ', 'ㅟ': 'ㅜㅣ', 'ㅢ': 'ㅡㅣ'};
const jamoCache = new Map();
function jamo(s) {
  let o = jamoCache.get(s); if (o !== undefined) return o; o = '';
  for (const ch of s) {
    const c = ch.charCodeAt(0) - 0xAC00;
    if (c >= 0 && c <= 11171) o += CHO[Math.floor(c / 588)] + JUNG[Math.floor(c % 588 / 28)] + JONG[c % 28];
    else o += JAMO2[ch] || ch;
  }
  if (jamoCache.size > 20000) jamoCache.clear();
  jamoCache.set(s, o); return o;
}
function kmatch(text, q) {
  q = (q || '').trim().toLowerCase(); if (!q) return true;
  const t = String(text || '').toLowerCase();
  if (t.includes(q)) return true;
  if (/^[ㄱ-ㅎ\s]+$/.test(q)) return cho(t).includes(q.replace(/\s+/g, '')) || cho(t).includes(q); // 초성만: 'ㅅㅇㅍ'
  if (/^[0-9\-\s]+$/.test(q)) { const d = q.replace(/\D/g, ''); return !!d && t.split(/\s+/).some(w => w.replace(/\D/g, '').includes(d)); } // 번호는 '-' 없이도
  return /[가-힣ㄱ-ㅣ]/.test(q) && jamo(t).includes(jamo(q));
}
const list = c => [...S.data[c].values()].filter(o => !o.deleted);
const byId = (c, id) => S.data[c].get(Number(id));
const user = id => byId('users', id);
const uname = id => { const u = user(id); return u ? u.name : (id ? '직원' + id : ''); };
const can = p => !!(S.me && (S.me.perms || []).includes(p));
function nextId() { for (;;) { const n = 1 + Math.floor(Math.random() * 2e9); if (!COLLS.some(c => S.data[c].has(n))) return n; } }
function wonFmt(n) { return Number(n || 0).toLocaleString('ko-KR') + '원'; }

// ---------- 서버 저장 ----------
function ser(o) { return JSON.stringify(o).replace(/\//g, '\\/'); } // 앱(org.json)과 같은 글자 모양
function put(coll, o) {
  S.data[coll].set(o.id, o);
  if (DEMO) return;
  const j = ser(o); S.pushed[coll + '/' + o.id] = j;
  const m = {j, u: Date.now()}; let target = coll, priv = false;
  if ((coll === 'rooms' || coll === 'msgs') && o.mem && o.mem.length) { m.mem = o.mem; target = coll === 'rooms' ? 'prooms' : 'pmsgs'; priv = true; }
  if (coll === 'profiles') m.who = o.id;
  if (coll === 'contracts') { m.who = o.who; m.date = o.date || ''; }
  S.pend = (S.pend || 0) + 1; syncBadge();
  fs.collection(target).doc(String(o.id)).set(m).then(() => { S.pend--; syncBadge(); }).catch(e => { S.pend--; syncBadge(); toast('저장하지 못했어요: ' + (e.code || e.message)); });
  if (!(priv || coll === 'msgs' || coll === 'profiles')) fs.collection('audit').add({c: coll, id: o.id, by: S.uid, u: Date.now(), j}).catch(() => {});
}
// 화면 위쪽 작은 띠: 저장 중 / 인터넷 끊김(저장 대기) — 서버에 다 들어가면 사라진다
function syncBadge() {
  let b = $('#syncb'); if (!b) { b = document.createElement('div'); b.id = 'syncb'; document.body.appendChild(b); }
  const off = !navigator.onLine, n = S.pend || 0;
  b.className = off ? 'off' : n ? 'on' : '';
  b.textContent = off ? '📴 인터넷 끊김' + (n ? ' · 저장 대기 ' + n + '건 (연결되면 자동으로 저장돼요)' : ' · 기기에 받아 둔 자료로 보는 중') : n ? '⏳ 서버에 저장 중… ' + n + '건' : '';
}
window.addEventListener('online', syncBadge); window.addEventListener('offline', syncBadge);
function create(coll, extra) { const o = Object.assign({id: nextId(), created: nowStr()}, extra || {}); return o; }

// ---------- 서버 읽기 ----------
let unsubs = [], renderT = 0;
function later() { clearTimeout(renderT); renderT = setTimeout(render, 200); }
function attach() {
  const srvs = COLLS.map(c => [c, c]);
  srvs.push(['rooms', 'prooms'], ['msgs', 'pmsgs']);
  srvs.forEach(([coll, srv]) => {
    const priv = srv === 'prooms' || srv === 'pmsgs';
    if (coll === 'costs' && !canPerf()) return; // 단가는 실적 권한자만
    let q = fs.collection(srv);
    if (priv) q = q.where('mem', 'array-contains', S.phone);
    if (coll === 'contracts' && !canPerf()) q = q.where('who', '==', S.uid); // 실적 권한이 없으면 내 계약만
    if (srv === 'msgs') q = q.orderBy('u', 'desc').limit(400);
    let first = true;
    unsubs.push(q.onSnapshot(snap => {
      let ch = false;
      snap.docChanges().forEach(dc => {
        if (dc.type === 'removed') return;
        const j = dc.doc.get('j'); if (!j) return;
        try {
          const o = JSON.parse(j);
          if (S.pushed[coll + '/' + o.id] === j) return;
          const prev = S.data[coll].get(o.id);
          S.pushed[coll + '/' + o.id] = j; S.data[coll].set(o.id, o); ch = true;
          if (!first) watchNew(coll, o, prev);
        } catch (e) {}
      });
      first = false;
      if (coll === 'users' && !snap.metadata.fromCache) { refreshMe(); if (!S.me) return noProfile(); }
      if (ch) { S.ready = true; if (S.me === null || coll === 'users') refreshMe(); later(); }
      if (coll === 'merchants' && !snap.metadata.fromCache) { clearTimeout(S.bkT); S.bkT = setTimeout(autoBackup, 15000); }
    }, err => console.warn(srv, err.code)));
  });
}
function refreshMe() { if (S.uid) { const u = user(S.uid); if (u) S.me = u; } }


// ---------- 알림 (웹앱을 열어 둔 동안) ----------
// 화면이 꺼져 있거나 웹앱을 닫았을 때 오는 진짜 푸시 알림은 Blaze 전환 뒤 서버(FCM)가 있어야 한다.
// 그 전까지: 열어 둔 동안 새 업무·채팅·공지가 오면 소리·진동·화면 안내, PC는 다른 창을 보고 있어도 브라우저 알림, 탭 제목에 숫자.
const soundOn = () => LS('snd_' + S.uid) !== '0';
let actx = null;
function beep() {
  if (!soundOn()) return;
  try { actx = actx || new (window.AudioContext || window.webkitAudioContext)(); const o = actx.createOscillator(), g = actx.createGain(); o.frequency.value = 880; g.gain.setValueAtTime(0.15, actx.currentTime); g.gain.exponentialRampToValueAtTime(0.001, actx.currentTime + 0.35); o.connect(g); g.connect(actx.destination); o.start(); o.stop(actx.currentTime + 0.35); } catch (e) {}
  if (navigator.vibrate) navigator.vibrate(120);
}
function notify(title, body, go) {
  beep();
  if (document.hidden && 'Notification' in window && Notification.permission === 'granted') {
    try { const n = new Notification(title, {body, icon: 'icon-192.png', tag: title}); n.onclick = () => { window.focus(); go(); n.close(); }; return; } catch (e) {}
  }
  toast(title + ' — ' + body);
}
function watchNew(coll, o, prev) {
  if (!S.me || o.deleted) return;
  if (coll === 'msgs' && !prev && o.who !== S.uid && o.ts > Date.now() - 5 * 60000 && !(S.room === o.room && !document.hidden)) {
    const r = byId('rooms', o.room); notify('💬 ' + (r ? roomTitle(r) : '채팅'), uname(o.who) + ': ' + msgText(o), () => { S.tab = 'chat'; S.sub = ''; S.room = o.room; render(); });
  }
  if (coll === 'tasks' && o.assignee === S.uid && o.status === '지시됨' && o.creator !== S.uid && (!prev || prev.assignee !== S.uid)) {
    notify('📋 새 업무', o.title + ' (' + uname(o.creator) + ')', () => { S.tab = 'task'; S.sub = ''; render(); ACT.task(o.id); });
  }
  if (coll === 'notices' && !prev && o.author !== S.uid) notify('📢 새 공지', o.title, () => { S.tab = 'all'; S.sub = 'notice'; render(); ACT.notice(o.id); });
}
function syncTitle(n) { document.title = (n ? '(' + n + ') ' : '') + '나인패밀리'; }

// ---------- 뒤로가기 (폰 뒤로가기 · 브라우저 뒤로 버튼) ----------
// 안드로이드 앱처럼 한 단계씩 닫는다: 카메라 → 팝업 → 대화방 → 전체 메뉴 → 하위 화면 → 첫 화면. 첫 화면에서 한 번 더 누르면 나간다.
let navArmed = false;
function armBack() { if (navArmed || S.exitWarn || DEMO && !S.me) return; try { history.pushState({nine: 1}, ''); navArmed = true; } catch (e) {} }
function stepBack() {
  if (window.scanStop) { window.scanStop(); return true; }
  if ($('#modal')) { closeModal(); return true; }
  if (S.room) { ACT.leave(); return true; }
  if (S.menu) { S.menu = false; render(); return true; }
  if (S.sub) { if (isWide()) ACT.home(); else { S.sub = ''; render(); } return true; }
  if (S.tab !== 'task') { ACT.home(); return true; }
  return false;
}
window.addEventListener('popstate', () => {
  navArmed = false; if (!S.me) return;
  if (stepBack()) armBack(); else { S.exitWarn = true; toast('한 번 더 누르면 나가요.'); }
});

// ---------- 로그인 ----------
let confirmation = null, verifier = null;
function loginView(step, msg) {
  const r = $('#root');
  r.innerHTML = '<div class="login"><h1>오늘도 반갑습니다.</h1><div class="sub">등록된 휴대폰 번호로 문자 인증을 해 주세요.<br>(한 번 로그인하면 계속 유지됩니다)</div>' +
    (step === 'code'
      ? '<input id="code" inputmode="numeric" maxlength="6" placeholder="문자로 받은 인증번호 6자리" autocomplete="one-time-code"><button class="btn gold full" id="go">확인</button><button class="btn full" id="again" disabled>인증번호 다시 받기</button><button class="btn full" id="back">번호 다시 입력</button>'
      : '<input id="ph" type="tel" inputmode="tel" placeholder="휴대폰 번호 (예: 01012345678)" value="' + esc(S.pendingPhone || '') + '"><button class="btn gold full" id="go">인증번호 받기</button>') +
    '<div class="sub" id="msg" style="color:#b3261e;margin-top:12px">' + esc(msg || '') + '</div><div class="sub" style="margin-top:30px">나인패밀리 ' + VERSION + '</div></div>';
  if (step === 'code') {
    const code = $('#code'); code.focus();
    code.oninput = () => { if (code.value.length === 6) $('#go').click(); };
    $('#go').onclick = async () => {
      const c = code.value.trim(); if (c.length < 6) return toast('인증번호 6자리를 입력해 주세요.');
      $('#go').disabled = true; $('#go').textContent = '확인하는 중…';
      try { const res = await confirmation.confirm(c); await afterLogin(res.user); } catch (e) {
        loginView('code', e.code === 'auth/invalid-verification-code' || e.code === 'auth/code-expired' ? '인증번호가 맞지 않아요. 다시 확인해 주세요.' : authMsg(e));
      }
    };
    $('#back').onclick = () => loginView('phone');
    let n = 60; const ag = $('#again');
    const tm = setInterval(() => { if (!ag.isConnected) return clearInterval(tm); n--; if (n <= 0) { clearInterval(tm); ag.disabled = false; ag.textContent = '인증번호 다시 받기'; } else ag.textContent = '인증번호 다시 받기 (' + n + '초 뒤)'; }, 1000);
    ag.textContent = '인증번호 다시 받기 (60초 뒤)';
    ag.onclick = () => sendCode(S.pendingPhone);
  } else {
    $('#go').onclick = () => {
      const raw = $('#ph').value.trim();
      if (raw.replace(/\D/g, '').length < 10) return toast('휴대폰 번호를 정확히 입력해 주세요.');
      S.pendingPhone = raw; $('#go').disabled = true; $('#go').textContent = '문자 보내는 중…'; sendCode(raw);
    };
  }
}
function authMsg(e) {
  const c = e.code || '';
  if (c.includes('too-many')) return '인증 요청이 너무 많아 잠시 막혔어요. 30분쯤 뒤에 다시 시도해 주세요.';
  if (c.includes('invalid-phone')) return '휴대폰 번호 형식이 맞지 않아요.';
  if (c.includes('network')) return '인터넷 연결이 불안정해요. 다시 시도해 주세요.';
  return '인증에 실패했어요. 잠시 뒤 다시 시도해 주세요. (' + (c || e.message) + ')';
}
async function sendCode(raw) {
  try {
    if (verifier) { try { verifier.clear(); } catch (e) {} }
    $('#recaptcha').innerHTML = '';
    verifier = new firebase.auth.RecaptchaVerifier('recaptcha', {size: 'invisible'});
    confirmation = await auth.signInWithPhoneNumber(e164(raw), verifier);
    loginView('code');
  } catch (e) { loginView('phone', authMsg(e)); }
}
async function afterLogin(fu) {
  const phone = fu.phoneNumber; S.phone = phone;
  $('#root').innerHTML = '<div class="login"><h1>불러오는 중…</h1><div class="sub">서버에서 내 정보를 가져오고 있어요.</div></div>';
  try {
    const pd = await fs.collection('phones').doc(phone).get();
    if (!pd.exists || pd.get('active') !== true) { await auth.signOut(); return loginView('phone', '등록되지 않은 번호입니다. 관리자에게 등록을 요청해 주세요.'); }
    S.uid = Number(pd.get('uid')); S.caps = {perf: !!pd.get('perf'), admin: !!pd.get('admin')};
    attach(); render();
    setTimeout(() => { if (!S.me && !S.noProfile) { $('#root').innerHTML = '<div class="login"><h1>불러오는 중…</h1><div class="sub">인터넷이 느려서 시간이 걸리고 있어요. 잠시만 기다려 주세요.</div><button class="btn full" id="rl">다시 불러오기</button></div>'; $('#rl').onclick = () => location.reload(); } }, 12000);
  } catch (e) { toast('내 정보를 불러오지 못했어요.'); loginView('phone', '내 정보를 불러오지 못했어요. 다시 시도해 주세요.'); }
}
function noProfile() { // 서버에서 받은 직원 목록에 내가 없을 때만 (느린 인터넷을 '처음 왔다'로 오해하지 않게)
  S.noProfile = true;
  $('#root').innerHTML = '<div class="login"><h1>처음 오셨나요?</h1><div class="sub">직원 정보가 아직 없어요. 앱에서 한 번 로그인한 뒤 다시 열어 주세요.</div><button class="btn full" id="lo">로그아웃</button></div>'; $('#lo').onclick = logout;
}
async function logout() {
  unsubs.forEach(u => u()); unsubs = [];
  COLLS.forEach(c => S.data[c].clear()); S.pushed = {}; S.me = null; S.uid = 0;
  try { await auth.signOut(); } catch (e) {}
  loginView('phone');
}

// ---------- 화면 틀 ----------
// 휴대폰(아이폰)은 안드로이드 앱과 같은 화면: 아래 탭 5개(업무·프로젝트·채팅·일정·전체), 나머지 기능은 '전체' 탭 안에 있다.
// PC(넓은 화면)는 토스포스 방식: 위쪽 가로줄에 주 메뉴, 왼쪽 위 ☰ 를 누르면 영역별 전체 메뉴가 펼쳐진다.
const NAVPHONE = [['task', '업무', 'task', ''], ['project', '프로젝트', 'project', ''], ['chat', '채팅', 'chat', ''], ['calendar', '일정', 'calendar', ''], ['all', '전체', 'all', '']];
const NAVPC = [['task', '업무', 'task', ''], ['project', '프로젝트', 'project', ''], ['chat', '채팅', 'chat', ''], ['calendar', '일정', 'calendar', ''], ['merchants', '가맹점', 'all', 'merchants'], ['logs', '응대 기록', 'all', 'logs']];
const isWide = () => window.innerWidth >= 900;
function newTaskCount() { return list('tasks').filter(t => t.assignee === S.uid && t.status === '지시됨').length; }
function bodyHTML() {
  const k = S.sub || S.tab, wide = isWide();
  if (wide && S.menu) return '<div class="scroll"><div class="col">' + viewMenu(true) + '</div></div>';
  if (S.tab === 'chat' && !S.sub) return viewChat();
  return '<div class="scroll"><div class="col">' + (!wide && S.sub ? '<button class="back" data-act="back">‹ 전체</button>' : '') + viewBody(k) + '</div></div>';
}
function render() {
  if (S.comp) { later(); return; } // 글자 조합 중에는 화면을 갈아엎지 않는다
  if (!S.me) { refreshMe(); if (!S.me) return; }
  if (S.me.active === false) { toast('사용이 중지된 계정입니다.'); return logout(); }
  const root = $('#root'); const wide = isWide(); if (!wide) S.menu = false;
  const keepScroll = $('.scroll') ? $('.scroll').scrollTop : 0;
  const act = document.activeElement; const focusId = act && act.id; const selPos = act && act.selectionStart; const focusVal = act && act.value;
  const badges = {task: newTaskCount(), all: unreadNotices(), chat: unreadTotal()};
  syncTitle(badges.task + badges.all + badges.chat); armBack();
  const k = S.sub || S.tab;
  const on = key => wide ? !S.menu && k === key : (key === 'all' ? S.tab === 'all' : k === key);
  const btn = ([key, lb, tab, sub]) => { const n = badges[key]; return '<button data-nav="' + tab + '" data-sub="' + sub + '" class="' + (on(key) ? 'on' : '') + '"><span class="t">' + lb + '</span>' + (n ? '<span class="badge">' + n + '</span>' : '') + '</button>'; };
  const body = bodyHTML();
  if (wide) {
    const top = '<header class="pctop"><button class="hamb" data-act="menu" aria-label="' + (S.menu ? '전체 메뉴 닫기' : '전체 메뉴 열기') + '">' + (S.menu ? '✕' : '☰') + (!S.menu && badges.all ? '<span class="badge">' + badges.all + '</span>' : '') + '</button>' +
      '<div class="logo" data-act="home" title="홈으로"><img src="icon-192.png" alt="">나인패밀리</div><nav class="pcnav">' + NAVPC.map(btn).join('') + '</nav>' +
      '<button data-act="installApp" class="inst pcinst" style="display:none">PC에 설치하기</button><div class="me">' + esc(S.me.name) + ' · ' + (can('staff_manage') ? '관리자' : '직원') + '</div></header>';
    root.innerHTML = '<div class="app pc">' + top + '<div class="main">' + body + '</div></div>' + (S.menu ? '' : fab());
  } else {
    root.innerHTML = '<div class="app"><div class="main">' + body + '</div><nav class="nav">' + NAVPHONE.map(btn).join('') + '</nav></div>' + fab();
  }
  syncInstall();
  bind(root);
  const sc = $('.scroll'); if (sc) sc.scrollTop = keepScroll;
  const ms = $('#msgbox'); if (ms) { ms.scrollTop = ms.scrollHeight; loadImages(); }
  if (focusId && $('#' + focusId) && $('#' + focusId).closest('#root')) { const el = $('#' + focusId); if (focusVal != null && el.tagName !== 'SELECT') el.value = focusVal; el.focus(); try { el.setSelectionRange(selPos, selPos); } catch (e) {} }
}
// 검색칸은 그대로 두고 결과(.qres)만 바꾼다 → 한글을 치는 중(조합 중)에도 바로 걸러지고 입력이 끊기지 않는다
function refreshResults() {
  const tmp = document.createElement('div'); tmp.innerHTML = bodyHTML();
  document.querySelectorAll('#root .qres').forEach(el => { const n = tmp.querySelector('.qres[data-for="' + el.dataset.for + '"]'); if (n) el.innerHTML = n.innerHTML; });
}
function viewBody(k) {
  switch (k) {
    case 'task': return isWide() ? viewHome() : viewTasks();
    case 'project': return viewProjects();
    case 'calendar': return viewCalendar();
    case 'all': return viewMenu(false);
    case 'merchants': return viewMerchants();
    case 'notice': return viewNotices();
    case 'logs': return viewAllLogs();
    case 'stock': return viewStock();
    case 'contracts': return viewContracts();
    case 'deposit': return viewDeposits();
    case 'manual': return viewManual();
    case 'about': return viewAbout();
    default: return viewTasks();
  }
}
function fab() {
  const k = S.sub || S.tab;
  const map = {task: 'newTask', project: 'newProject', merchants: canEditMerchant() ? 'newMerchant' : '', calendar: can('staff_manage') ? 'newEvent' : '', notice: can('notice_write') ? 'newNotice' : '', stock: 'newStock', contracts: 'newContract', deposit: 'newDeposit'};
  return map[k] ? '<button class="fab" data-act="' + map[k] + '">+</button>' : '';
}
function bind(root) {
  root.onclick = ev => {
    S.exitWarn = false;
    const bb = ev.target.closest('.bub[data-mid]');
    if (bb && !ev.target.closest('a,[data-act]')) { clearTimeout(S.tapT); if (S.tapId === bb.dataset.mid) { S.tapId = 0; return ACT.react(bb.dataset.mid + '|❤️'); } S.tapId = bb.dataset.mid; S.tapT = setTimeout(() => { S.tapId = 0; ACT.msgMenu(bb.dataset.mid); }, 280); return; }
    const n = ev.target.closest('[data-nav]'); if (n) { S.tab = n.dataset.nav; S.sub = n.dataset.sub || ''; S.room = 0; S.menu = false; return render(); }
    const a = ev.target.closest('[data-act]'); if (a) { ACT[a.dataset.act] && ACT[a.dataset.act](a.dataset.arg, a, ev); }
  };
  // 한글은 글자를 조합하는 동안 입력칸을 다시 그리면 'ㅌㅗㅅㅡ' 처럼 쪼개진다 → 화면 전체는 조합이 끝난 뒤에만 다시 그린다
  if (root.__bound) return; root.__bound = true; // 이벤트 위임은 한 번만 건다 (render 때마다 다시 걸면 중복됨)
  const apply = t => { if (t && t.dataset && t.dataset.q) { S[t.dataset.q] = t.value; if (t.dataset.q === 'mq') S.mlimit = 50; refreshResults(); } };
  root.addEventListener('input', ev => apply(ev.target));
  root.addEventListener('compositionstart', () => { S.comp = true; });
  root.addEventListener('compositionend', ev => { S.comp = false; apply(ev.target); });
}
const ACT = {};
ACT.notifyOn = () => {
  if (!('Notification' in window)) { toast('이 기기는 브라우저 알림을 못 써요. 웹앱을 열어 둔 동안 소리·진동으로 알려 드려요.'); return; }
  Notification.requestPermission().then(r => { toast(r === 'granted' ? '알림을 켰어요. 웹앱을 열어 둔 동안 새 업무·채팅·공지를 알려 드려요.' : '알림이 꺼져 있어요. 브라우저 설정에서 이 사이트 알림을 허용해 주세요.'); render(); });
};
ACT.soundToggle = () => { LS('snd_' + S.uid, soundOn() ? '0' : '1'); toast(soundOn() ? '알림 소리를 켰어요.' : '알림 소리를 껐어요.'); render(); };

// ---------- 업무 ----------
function stColor(s) { return {'지시됨': ['#fff1db', '#8a5200'], '진행중': ['#f7efd9', '#8f6b14'], '보류': ['#ffe9e5', '#b3261e'], '완료': ['#e3f6ee', '#0b7a55']}[s] || ['#eef0f4', '#4a4f5e']; }
function viewTasks(noHero) {
  const all = list('tasks'); const today = todayStr();
  let open = 0, done = 0, fresh = 0, doing = 0, held = 0;
  all.forEach(t => { if (t.assignee !== S.uid) return; if (t.status === '완료') { if ((t.doneAt || '').startsWith(today)) done++; } else { open++; if (t.status === '지시됨') fresh++; if (t.status === '진행중' || t.status === '확인함') doing++; if (t.status === '보류') held++; } });
  const ratio = open + done ? Math.round(100 * done / (open + done)) : 0;
  const scope = all.filter(t => (S.showAll && can('task_view_all')) || t.assignee === S.uid);
  const F = ['미완료', '미확인', '진행중', '보류', '완료'], FS = ['', '지시됨', '진행중', '보류', '완료'];
  const norm = t => t.status === '확인함' ? '진행중' : t.status;
  const cnt = F.map((f, i) => scope.filter(t => i === 0 ? norm(t) !== '완료' : norm(t) === FS[i]).length);
  const rows = scope.filter(t => S.taskFilter === 0 ? norm(t) !== '완료' : norm(t) === FS[S.taskFilter]).sort((a, b) => (b.created || '').localeCompare(a.created || ''));
  const exn = expiringContracts().length;
  const expCard = exn ? '<button class="expcard" data-nav="all" data-sub="contracts">⏰ 만료가 3개월 안으로 남은 계약 ' + exn + '건 <span>계약 관리 ›</span></button>' : '';
  return expCard + (noHero ? '' : '<div class="hero"><div class="row"><div class="grow">남은 업무<div class="n">' + open + '건</div></div><div style="text-align:right;font-size:12px">미확인 ' + fresh + ' · 진행 ' + doing + ' · 보류 ' + held + '<br>오늘 완료 ' + done + '</div></div><div class="bar"><i style="width:' + ratio + '%"></i></div></div>') +
    (can('task_view_all') ? '<div class="pills"><button class="pill ' + (!S.showAll ? 'on' : '') + '" data-act="scope" data-arg="0">내 업무</button><button class="pill ' + (S.showAll ? 'on' : '') + '" data-act="scope" data-arg="1">전체 업무</button></div>' : '') +
    '<div class="sub" style="margin-top:' + (noHero ? 4 : 14) + 'px;font-weight:700">상태별로 보기</div><div class="seg">' + F.map((f, i) => '<button class="' + (S.taskFilter === i ? 'on' : '') + '" data-act="tfilter" data-arg="' + i + '">' + f + '<b>' + cnt[i] + '</b></button>').join('') + '</div>' +
    (rows.length ? rows.map(taskCard).join('') : '<div class="empty">' + (S.taskFilter === 0 ? '진행 중인 업무가 없습니다.' : '\'' + F[S.taskFilter] + '\' 업무가 없습니다.') + '</div>');
}
function taskCard(t) {
  const s = ST[t.status] || t.status; const c = stColor(t.status === '확인함' ? '진행중' : t.status);
  const ty = (t.type || '기타').replace('신규 ', '').replace('영업 방문', '영업').replace('서류 접수', '서류').replace('카드사 서류', '카드').slice(0, 2);
  return '<div class="card" data-act="task" data-arg="' + t.id + '"><div class="row"><div class="tile">' + esc(ty) + '</div><div class="grow"><div class="t1">' + esc(t.title) + '</div><div class="t2">' + esc((t.client ? t.client + ' · ' : '') + uname(t.assignee) + (t.due ? ' · ' + t.due : '')) + '</div></div><span class="chip" style="background:' + c[0] + ';color:' + c[1] + '">' + s + '</span></div></div>';
}
ACT.scope = a => { S.showAll = a === '1'; render(); };
ACT.tfilter = a => { S.taskFilter = Number(a); render(); };
function tlog(t, text) { (t.history = t.history || []).push({t: nowStr(), who: S.uid, text}); }
ACT.task = id => {
  const t = byId('tasks', id); if (!t) return;
  if (t.status === '지시됨' && t.assignee === S.uid && !t.seenAt) { t.seenAt = nowStr(); tlog(t, '담당자가 열어봄'); put('tasks', t); }
  const own = t.assignee === S.uid || can('staff_manage'); // 상태 변경: 담당자 본인 또는 최고관리자 (앱과 같음)
  const ln = (k, v) => v ? '<div class="line"><b>' + k + '</b><div>' + esc(v) + '</div></div>' : '';
  const ph = (t.phone || '').replace(/[^0-9+*#]/g, '');
  modal('<h3>' + esc(t.title) + '</h3><div class="sub" style="font-weight:700">' + esc(t.type) + ' · ' + (ST[t.status] || t.status) + '</div>' +
    ln('거래처', t.client) + (ph ? '<div class="line"><b>전화</b><a href="tel:' + ph + '">' + esc(t.phone) + '</a></div>' : '') + ln('주소', t.addr) + ln('기한', t.due) + ln('담당', uname(t.assignee)) + ln('지시한 사람', uname(t.creator)) + ln('열어본 시각', t.seenAt) + ln('메모', t.memo) + ln('완료 메시지', t.doneMsg) +
    '<h2>처리 이력</h2>' + (t.history || []).map(h => '<div class="hist">' + esc(h.t + '  ' + uname(h.who) + '\n' + h.text) + '</div>').join('') +
    (own && t.status !== '완료' ? '<div class="btns" style="margin-top:12px">' + (t.status !== '진행중' && t.status !== '확인함' ? '<button class="btn tint" data-act="tstatus" data-arg="' + id + '|진행중">진행 시작</button>' : '') + '<button class="btn" data-act="thold" data-arg="' + id + '">보류·연기</button><button class="btn gold" data-act="tdone" data-arg="' + id + '">완료</button></div>' : '') +
    (own && t.status === '완료' ? '<button class="btn full" data-act="tstatus" data-arg="' + id + '|진행중">진행중으로 되돌리기</button>' : '') +
    (own && t.status === '보류' ? '<button class="btn tint full" data-act="tresched" data-arg="' + id + '">일정 다시 잡기</button>' : '') +
    '<div class="btns">' + (t.creator === S.uid || can('staff_manage') ? '<button class="btn red" data-act="tdel" data-arg="' + id + '">삭제</button>' : '') + '<button class="btn" data-act="close">닫기</button></div>');
};
ACT.tdel = id => { const t = byId('tasks', id); if (!t) return; askConfirm('업무를 삭제할까요?', t.title + '\n목록과 달력에서 사라집니다.', '삭제', () => { t.deleted = true; put('tasks', t); render(); toast('삭제했어요.'); }, true); };
ACT.tstatus = a => {
  const [id, st] = a.split('|'); const t = byId('tasks', id); if (!t || t.status === st) return;
  t.status = st; tlog(t, '상태 → ' + st); put('tasks', t); closeModal(); render(); toast('바꿨어요.');
};
ACT.thold = id => formModal('보류·연기', '<div class="sub">다시 할 날짜를 정하면 일정 달력도 같이 바뀌어요. (정하지 않아도 돼요)</div><label class="fl" for="f_d">다시 할 날짜 (선택)</label><input type="date" id="f_d"><label class="fl" for="f_t">시간 (선택)</label><input type="time" id="f_t">', () => {
  const t = byId('tasks', id); const d = $('#f_d').value, tm = $('#f_t').value;
  t.status = '보류'; tlog(t, '상태 → 보류');
  if (d) { const nd = d + (tm ? ' ' + tm : ''); tlog(t, '일정 변경 ' + (t.due || '없음') + ' → ' + nd); t.due = nd; }
  put('tasks', t); render(); toast('보류로 바꿨어요.'); return true;
}, '보류하기');
ACT.tresched = id => formModal('일정 다시 잡기', '<label class="fl" for="f_d">날짜 (필수)</label><input type="date" id="f_d"><label class="fl" for="f_t">시간 (선택)</label><input type="time" id="f_t">', () => {
  const d = $('#f_d').value, tm = $('#f_t').value; if (!d) return warn('날짜를 골라 주세요.');
  const t = byId('tasks', id); const nd = d + (tm ? ' ' + tm : ''); tlog(t, '일정 변경 ' + (t.due || '없음') + ' → ' + nd); t.due = nd; put('tasks', t); render(); toast('일정을 바꿨어요.'); return true;
}, '변경');
ACT.tdone = id => formModal('완료 처리', '<div class="sub">처리 결과를 한 줄 남겨 주세요.</div><textarea id="f_m" placeholder="완료 메시지 (필수)"></textarea>', () => {
  const m = $('#f_m').value.trim(); if (!m) return warn('완료 메시지를 입력해 주세요.');
  const t = byId('tasks', id); t.status = '완료'; t.doneMsg = m; t.doneAt = nowStr(); tlog(t, '상태 → 완료 : ' + m); put('tasks', t); render(); toast('완료 처리했어요.'); return true;
}, '완료 처리');
ACT.newTask = () => {
  const staff = list('users').filter(u => u.active !== false && (can('task_assign') || u.id === S.uid));
  formModal('업무 지시', '<label class="fl" for="f_t1">업무 제목 (필수)</label><input id="f_t1" placeholder="예: A마트 포스 설치"><label class="fl" for="f_ty">종류</label><select id="f_ty">' + TYPES.map(x => '<option>' + x + '</option>').join('') + '</select>' + MP_FILL +
    '<label class="fl" for="f_c">거래처 (상호)</label><input id="f_c" placeholder="가맹점을 고르면 자동으로 채워져요"><label class="fl" for="f_p">매장 전화</label><input id="f_p" type="tel" placeholder="예: 062-123-4567"><label class="fl" for="f_a">매장 주소</label><input id="f_a" placeholder="예: 광주 동구 …">' +
    '<label class="fl" for="f_d">기한 날짜 (선택 — 고르면 일정 달력에도 표시돼요)</label><input id="f_d" type="date"><label class="fl" for="f_tm">기한 시간 (선택)</label><input id="f_tm" type="time"><label class="fl" for="f_m">메모</label><textarea id="f_m" placeholder="메모"></textarea><label class="fl" for="f_w">담당 직원</label><select id="f_w">' + staff.map(u => '<option value="' + u.id + '"' + (u.id === S.uid ? ' selected' : '') + '>' + esc(u.name) + '</option>').join('') + '</select>', () => {
    const ti = $('#f_t1').value.trim(); if (!ti) return warn('업무 제목을 입력해 주세요.');
    const d = $('#f_d').value, tm = $('#f_tm').value; const as = Number($('#f_w').value);
    const t = create('tasks', {title: ti, type: $('#f_ty').value, client: $('#f_c').value.trim(), phone: $('#f_p').value.trim(), addr: $('#f_a').value.trim(), due: d ? d + (tm ? ' ' + tm : '') : '', memo: $('#f_m').value.trim(), assignee: as, creator: S.uid, status: '지시됨', history: []});
    tlog(t, '업무 지시 → ' + uname(as)); put('tasks', t);
    if (t.type.includes('서류')) postToRoom('서류접수', '📄 서류 업무 · ' + ti + (t.client ? ' · ' + t.client : '') + '\n담당 ' + uname(as) + ' · 지시 ' + uname(S.uid) + (t.due ? ' · 기한 ' + t.due : '')); // 앱과 같은 자동 알림
    if (d) { const p = d.split('-'); S.calY = +p[0]; S.calM = +p[1]; S.calSel = d; }
    render(); toast(uname(as) + '님에게 업무를 지시했습니다.'); return true;
  }, '지시하기');
  wireMerchantPicker(m => { const n = mNums(m); $('#f_c').value = m.name || ''; $('#f_p').value = n.phone || n.mobile || ''; $('#f_a').value = m.addr || ''; });
};

// ---------- 프로젝트 ----------
function itemCnt(p) { const it = p.items || []; return [it.filter(x => x.done).length, it.length]; }
function pctOf(p) { const [d, n] = itemCnt(p); return n ? Math.round(100 * d / n) : (p.pct || 0); }
function viewProjects() {
  const ps = list('projects'); const F = ['전체', '진행중', '완료(100%)'];
  const rows = ps.filter(p => S.projFilter === 0 || (S.projFilter === 1 ? pctOf(p) < 100 : pctOf(p) >= 100)).sort((a, b) => (b.created || '').localeCompare(a.created || ''));
  return '<h1>프로젝트</h1><div class="seg">' + F.map((f, i) => '<button class="' + (S.projFilter === i ? 'on' : '') + '" data-act="pfilter" data-arg="' + i + '">' + f + '</button>').join('') + '</div>' +
    (rows.length ? rows.map(p => {
      const [d, n] = itemCnt(p); const pc = pctOf(p);
      return '<div class="card" data-act="project" data-arg="' + p.id + '"><div class="row"><div class="grow"><div class="t1">' + esc(p.title) + '</div><div class="t2">' + esc(uname(p.owner) + (p.due ? ' · 마감 ' + p.due : '') + (n ? ' · 체크리스트 ' + d + ' / ' + n : '')) + '</div></div><span class="chip">' + (pc >= 100 ? '✔ 완료' : pc + '%') + '</span></div><div class="bar" style="background:#e3e5ea"><i style="width:' + pc + '%;background:var(--gold)"></i></div></div>';
    }).join('') : '<div class="empty">프로젝트가 없습니다. 오른쪽 아래 + 로 만들어 보세요.</div>');
}
ACT.pfilter = a => { S.projFilter = Number(a); render(); };
ACT.project = id => {
  const p = byId('projects', id); if (!p) return;
  const [d, n] = itemCnt(p);
  modal('<h3>' + esc(p.title) + '</h3><div class="sub">' + esc(uname(p.owner) + (p.due ? ' · 마감 ' + p.due : '')) + '</div><div style="font-size:26px;font-weight:800;color:var(--deep);margin:8px 0">' + pctOf(p) + '%</div><div class="sub">' + (n ? '체크리스트 ' + d + ' / ' + n + ' 완료' : '체크리스트가 없어요') + '</div>' +
    (p.memo ? '<div class="sub" style="color:#14161a">' + esc(p.memo) + '</div>' : '') +
    (p.items || []).map((x, i) => '<label class="chk ' + (x.done ? 'done' : '') + '"><input type="checkbox" ' + (x.done ? 'checked' : '') + ' data-act="ptoggle" data-arg="' + id + '|' + i + '"><span class="grow">' + esc(x.t) + '</span></label>').join('') +
    '<input id="f_i" placeholder="+ 체크리스트 항목 추가"><button class="btn tint full" data-act="padd" data-arg="' + id + '">항목 추가</button>' +
    (n && pctOf(p) >= 100 ? '<button class="btn full" data-act="prevert" data-arg="' + id + '">완료 되돌리기</button>' : '') + '<button class="btn full" data-act="close">닫기</button>');
};
function psave(p) { p.pct = pctOf(p); p.check = p.pct >= 100; put('projects', p); }
ACT.ptoggle = a => { const [id, i] = a.split('|'); const p = byId('projects', id); p.items[i].done = !p.items[i].done; psave(p); ACT.project(id); render(); };
ACT.padd = id => { const v = $('#f_i').value.trim(); if (!v) return; const p = byId('projects', id); (p.items = p.items || []).push({t: v, done: false}); psave(p); ACT.project(id); render(); };
ACT.prevert = id => { const p = byId('projects', id); const it = p.items || []; for (let i = it.length - 1; i >= 0; i--) if (it[i].done) { it[i].done = false; break; } psave(p); ACT.project(id); render(); toast('진행중으로 되돌렸어요.'); };
ACT.newProject = () => {
  const staff = list('users').filter(u => u.active !== false);
  formModal('프로젝트 만들기', '<label class="fl" for="f_t1">프로젝트 이름 (필수)</label><input id="f_t1" placeholder="예: 10월 신규 가맹점 20곳"><label class="fl" for="f_d">마감일 (선택)</label><input id="f_d" type="date"><label class="fl" for="f_m">메모</label><textarea id="f_m" placeholder="메모"></textarea><label class="fl" for="f_i">체크리스트 (한 줄에 항목 하나, 선택)</label><textarea id="f_i" placeholder="예: 리스트 정리&#10;방문 10곳"></textarea><label class="fl" for="f_w">담당자</label><select id="f_w">' + staff.map(u => '<option value="' + u.id + '"' + (u.id === S.uid ? ' selected' : '') + '>' + esc(u.name) + '</option>').join('') + '</select>', () => {
    const ti = $('#f_t1').value.trim(); if (!ti) return warn('프로젝트 이름을 입력해 주세요.');
    const items = $('#f_i').value.split('\n').map(s => s.trim()).filter(Boolean).map(t => ({t, done: false}));
    const p = create('projects', {title: ti, due: $('#f_d').value, memo: $('#f_m').value.trim(), owner: Number($('#f_w').value), pct: 0, check: false, notes: []});
    if (items.length) p.items = items; put('projects', p); render(); toast('프로젝트를 만들었어요.'); return true;
  }, '만들기');
};

// ---------- 일정 ----------
// 공휴일: 앱의 Holidays.java 와 같은 목록 (2026~2028)
const HOL = {};
'2026-1-1 신정;2026-2-16 설날 연휴;2026-2-17 설날;2026-2-18 설날 연휴;2026-3-1 삼일절;2026-3-2 대체공휴일;2026-5-5 어린이날;2026-5-24 부처님오신날;2026-5-25 대체공휴일;2026-6-3 지방선거;2026-6-6 현충일;2026-8-15 광복절;2026-8-17 대체공휴일;2026-9-24 추석 연휴;2026-9-25 추석;2026-9-26 추석 연휴;2026-10-3 개천절;2026-10-5 대체공휴일;2026-10-9 한글날;2026-12-25 성탄절;2027-1-1 신정;2027-2-5 설날 연휴;2027-2-6 설날;2027-2-7 설날 연휴;2027-2-8 대체공휴일;2027-3-1 삼일절;2027-5-5 어린이날;2027-5-13 부처님오신날;2027-6-6 현충일;2027-8-15 광복절;2027-8-16 대체공휴일;2027-9-14 추석 연휴;2027-9-15 추석;2027-9-16 추석 연휴;2027-10-3 개천절;2027-10-4 대체공휴일;2027-10-9 한글날;2027-10-11 대체공휴일;2027-12-25 성탄절;2027-12-27 대체공휴일;2028-1-1 신정;2028-1-25 설날 연휴;2028-1-26 설날;2028-1-27 설날 연휴;2028-3-1 삼일절;2028-4-12 국회의원선거;2028-5-2 부처님오신날;2028-5-5 어린이날;2028-6-6 현충일;2028-8-15 광복절;2028-10-2 추석 연휴;2028-10-3 추석·개천절;2028-10-4 추석 연휴;2028-10-5 대체공휴일;2028-10-9 한글날;2028-12-25 성탄절'.split(';').forEach(x => { const [d, ...n] = x.split(' '); const [y, m, dd] = d.split('-'); HOL[y + '-' + pad(+m) + '-' + pad(+dd)] = n.join(' '); });
function viewCalendar() {
  const now = new Date();
  if (!S.calY) { S.calY = now.getFullYear(); S.calM = now.getMonth() + 1; S.calSel = todayStr(); }
  const ev = {};
  const add = (d, o) => { if (d) (ev[d] = ev[d] || []).push(o); };
  list('events').forEach(e => add(e.date, {kind: 'e', t: e.title, time: e.time, sub: e.place, who: e.who, id: e.id}));
  list('tasks').forEach(t => { if (t.due && t.status !== '완료' && (S.showAll || t.assignee === S.uid || can('task_view_all'))) add(t.due.slice(0, 10), {kind: 't', t: t.title, time: t.due.length > 10 ? t.due.slice(11, 16) : '', sub: uname(t.assignee) + ' · ' + (ST[t.status] || t.status), id: t.id}); });
  const first = new Date(S.calY, S.calM - 1, 1).getDay(); const days = new Date(S.calY, S.calM, 0).getDate();
  let cells = ''; for (let i = 0; i < first; i++) cells += '<td></td>';
  for (let d = 1; d <= days; d++) {
    const k = S.calY + '-' + pad(S.calM) + '-' + pad(d); const dow = (first + d - 1) % 7;
    cells += '<td class="' + (k === S.calSel ? 'sel ' : '') + (k === todayStr() ? 'today ' : '') + (dow === 0 || HOL[k] ? 'sun' : dow === 6 ? 'sat' : '') + '" data-act="calsel" data-arg="' + k + '"' + (HOL[k] ? ' title="' + HOL[k] + '"' : '') + '><div>' + d + (ev[k] ? '<i></i>' : '') + '</div></td>';
    if (dow === 6 && d < days) cells += '</tr><tr>';
  }
  const sel = (ev[S.calSel] || []).sort((a, b) => (a.time || '').localeCompare(b.time || ''));
  return '<div class="row" style="margin-top:18px"><button class="btn" data-act="calmove" data-arg="-1" style="margin:0" aria-label="이전 달">‹</button><div class="grow t1" style="text-align:center;font-size:19px">' + S.calY + '년 ' + S.calM + '월</div><button class="btn" data-act="caltoday" style="margin:0">오늘</button><button class="btn" data-act="calmove" data-arg="1" style="margin:0" aria-label="다음 달">›</button></div>' +
    '<table class="cal"><tr>' + ['일', '월', '화', '수', '목', '금', '토'].map(x => '<th>' + x + '</th>').join('') + '</tr><tr>' + cells + '</tr></table>' +
    (can('staff_manage') ? '' : '<div class="sub" style="margin-top:6px">일정 추가는 관리자만 할 수 있어요. (업무에 기한 날짜를 넣으면 달력에 표시돼요)</div>') +
    '<h2>' + esc(S.calSel) + (HOL[S.calSel] ? ' <span style="color:var(--red)">' + esc(HOL[S.calSel]) + '</span>' : '') + '</h2>' + (sel.length ? sel.map(o => '<div class="card" data-act="' + (o.kind === 't' ? 'task' : 'event') + '" data-arg="' + o.id + '"><div class="row"><div class="tile">' + (o.kind === 't' ? '업무' : '일정') + '</div><div class="grow"><div class="t1">' + esc(o.t) + '</div><div class="t2">' + esc((o.time ? o.time + ' · ' : '') + (o.sub || '')) + '</div></div></div></div>').join('') : '<div class="empty">이 날은 일정이 없어요.</div>') + upcoming(ev);
}
function upcoming(ev) { // 오늘부터 앞으로의 일정과 업무 기한을 날짜 순으로 (앱의 '다가오는 일정')
  const today = todayStr(); const days = Object.keys(ev).filter(d => d >= today).sort().slice(0, 14);
  const rows = []; days.forEach(d => (ev[d] || []).slice().sort((a, b) => (a.time || '').localeCompare(b.time || '')).forEach(o => rows.push([d, o])));
  return '<h2>다가오는 일정</h2>' + (rows.length ? rows.slice(0, 20).map(([d, o]) => '<div class="evl" data-act="' + (o.kind === 't' ? 'task' : 'event') + '" data-arg="' + o.id + '"><b style="width:74px">' + d.slice(5).replace('-', '/') + (o.time ? ' ' + esc(o.time) : '') + '</b><span>' + (o.kind === 't' ? '📋 ' : '📅 ') + esc(o.t) + (o.sub ? '<small>' + esc(o.sub) + '</small>' : '') + '</span></div>').join('') : '<div class="empty" style="padding:16px 0">앞으로 잡힌 일정이 없어요.</div>');
}
ACT.event = id => {
  const e = byId('events', id); if (!e) return; const m = e.merchant ? byId('merchants', e.merchant) : null;
  const ln = (k, v) => v ? '<div class="line"><b>' + k + '</b><div>' + esc(v) + '</div></div>' : '';
  modal('<h3>' + esc(e.title) + '</h3>' + ln('날짜', e.date + (e.time ? ' ' + e.time : '')) + ln('장소', e.place) + ln('가맹점', m && m.name) + ln('남긴 사람', uname(e.who)) +
    '<div class="btns" style="margin-top:12px">' + (m ? '<button class="btn tint" data-act="merchant" data-arg="' + m.id + '">가맹점 정보 보기</button>' : '') + (can('staff_manage') ? '<button class="btn red" data-act="eventDel" data-arg="' + e.id + '">삭제</button>' : '') + '<button class="btn" data-act="close">닫기</button></div>');
};
ACT.eventDel = id => { const e = byId('events', id); if (!e) return; askConfirm('일정을 삭제할까요?', e.title, '삭제', () => { e.deleted = true; put('events', e); render(); toast('삭제했어요.'); }, true); };
ACT.calsel = d => { S.calSel = d; render(); };
ACT.caltoday = () => { const t = todayStr().split('-'); S.calY = +t[0]; S.calM = +t[1]; S.calSel = todayStr(); render(); };
ACT.calmove = a => { S.calM += Number(a); if (S.calM < 1) { S.calM = 12; S.calY--; } if (S.calM > 12) { S.calM = 1; S.calY++; } render(); };
ACT.newEvent = () => {
  let mer = null;
  formModal('일정 추가', '<label class="fl" for="f_t1">일정 제목 (필수)</label><input id="f_t1" placeholder="예: 본사 회의"><label class="fl" for="f_d">날짜 (필수)</label><input id="f_d" type="date" value="' + (S.calSel || todayStr()) + '"><label class="fl" for="f_tm">시간 (선택)</label><input id="f_tm" type="time">' + MP_FILL.replace('고르면 거래처·전화·주소가 자동으로 채워져요', '고르면 장소에 가맹점 주소가 들어가요') + '<label class="fl" for="f_p">장소·주소</label><input id="f_p" placeholder="예: 광주지사">', () => {
    const ti = $('#f_t1').value.trim(), d = $('#f_d').value; if (!ti || !d) return warn('제목과 날짜를 확인해 주세요.');
    const e = create('events', {date: d, title: ti, who: S.uid, time: $('#f_tm').value, place: $('#f_p').value.trim()}); if (mer) e.merchant = mer.id;
    put('events', e); S.calSel = d; render(); toast('일정을 남겼어요.'); return true;
  }, '추가');
  wireMerchantPicker(m => { mer = m; if (m.addr) $('#f_p').value = m.addr; });
};

// ---------- 채팅 ----------
const LS = (k, v) => { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) {} return null; };
// 읽은 위치: 이 기기(localStorage)와 내 프로필 문서(rd: {방: 시각}) 중 늦은 쪽 → PC에서 읽으면 아이폰 웹앱에서도 읽음으로 바뀐다
const myProf = () => byId('profiles', S.uid);
const readAt = r => {
  const v = Number(LS('rd_' + S.uid + '_' + r) || 0); const pr = myProf(); const sv = pr && pr.rd ? Number(pr.rd[r] || 0) : 0;
  if (v || sv) return Math.max(v, sv);
  let i = LS('rdinit_' + S.uid); if (!i) { i = Date.now(); LS('rdinit_' + S.uid, String(i)); } return Number(i);
};
let rdQ = {}, rdT = 0;
function markRead(r) {
  if (!r) return; const was = readAt(r); let mx = was; list('msgs').forEach(m => { if (m.room === r && m.ts > mx) mx = m.ts; });
  LS('rd_' + S.uid + '_' + r, String(mx)); if (mx > was) { rdQ[r] = mx; clearTimeout(rdT); rdT = setTimeout(flushRead, 4000); }
}
function flushRead() { // 서버에는 모아서 가끔만 쓴다
  clearTimeout(rdT); const ks = Object.keys(rdQ); if (!ks.length || DEMO || !S.uid) return;
  const pr = myProf() || {id: S.uid}; pr.rd = Object.assign({}, pr.rd || {}); ks.forEach(k => { if (!(pr.rd[k] >= rdQ[k])) pr.rd[k] = rdQ[k]; }); rdQ = {}; put('profiles', pr);
}
document.addEventListener('visibilitychange', () => { if (document.hidden) flushRead(); });
function unreadMap() { const o = {}, since = {}; list('rooms').forEach(r => since[r.id] = readAt(r.id)); list('msgs').forEach(m => { if (since[m.room] != null && m.ts > since[m.room] && m.who !== S.uid) o[m.room] = (o[m.room] || 0) + 1; }); return o; }
function unreadTotal() { if (!S.me) return 0; return Object.values(unreadMap()).reduce((a, b) => a + b, 0); }
function unreadNotices() { return list('notices').filter(n => !(n.readBy || []).includes(S.uid)).length; }
function dmOther(r) { return (r.members || []).find(x => x !== S.uid) || 0; }
function roomTitle(r) { return r.dm ? (dmOther(r) ? uname(dmOther(r)) : '나와의 대화') : (r.name || ''); }
function lastMsg(rid) { let b = null; list('msgs').forEach(m => { if (m.room === rid && (!b || m.ts > b.ts)) b = m; }); return b; }
function msgText(m) { if (m.kind === 'img') return '📷 사진'; if (m.kind === 'stk') return '스티커 (앱에서 보여요)'; if (m.kind === 'file') return '📎 ' + (m.fname || '파일'); return m.text || ''; }
function viewChat() {
  const rooms = list('rooms').map(r => ({r, last: lastMsg(r.id)})).filter(x => x.last || x.r.id === S.room).sort((a, b) => (b.last ? b.last.ts : 0) - (a.last ? a.last.ts : 0));
  const un = unreadMap();
  const staff = list('users').filter(u => u.id !== S.uid && u.active !== false).sort((a, b) => a.name.localeCompare(b.name));
  const lst = '<div class="chatlist"><h1>채팅</h1><button class="btn gold full" data-act="newGroup">+ 새 그룹 채팅방</button>' +
    '<div class="sub" style="margin-top:12px;font-weight:700">직원 총 ' + (staff.length + 1) + '명 · 눌러서 1:1 대화</div><div class="avs">' + staff.map(u => '<div class="av" data-act="dm" data-arg="' + u.id + '"><b>' + esc(u.name.slice(0, 1)) + '</b>' + esc(u.name) + '</div>').join('') + '</div>' +
    '<input id="chq" data-q="chatQ" placeholder="🔍 대화방 검색 (초성 가능)" value="' + esc(S.chatQ) + '">' +
    '<div class="qres" data-for="chatQ">' + rooms.filter(x => kmatch(roomTitle(x.r), S.chatQ)).map(x => {
      const l = x.last; const c = un[x.r.id] || 0;
      return '<div class="card" data-act="room" data-arg="' + x.r.id + '"><div class="row"><div class="tile">' + esc((roomTitle(x.r) || '?').slice(0, 1)) + '</div><div class="grow"><div class="t1">' + esc(roomTitle(x.r)) + '</div><div class="t2">' + esc(l ? (l.who === S.uid ? '나' : uname(l.who)) + ': ' + msgText(l) : '아직 대화가 없어요') + '</div></div><div style="text-align:right;font-size:11px;color:var(--gray)">' + (l ? new Date(l.ts).toLocaleString('ko-KR', {month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false}) : '') + (c ? '<br><span class="badge">' + c + '</span>' : '') + '</div></div></div>';
    }).join('') + '</div>' + (rooms.length ? '' : '<div class="empty">아직 채팅방이 없어요.</div>') + '<div style="height:90px"></div></div>';
  const wide = isWide();
  if (!S.room) return wide ? '<div class="chatwrap">' + lst + '<div class="chatroom"><div class="empty" style="margin:auto">대화방을 골라 주세요.</div></div></div>' : lst;
  const room = byId('rooms', S.room) || (S.dmDraft && S.dmDraft.id === S.room ? S.dmDraft : null);
  if (!room) { S.room = 0; return lst; }
  markRead(S.room);
  const ms = list('msgs').filter(m => m.room === S.room).sort((a, b) => a.ts - b.ts);
  let lastDay = '', html = '';
  ms.forEach(m => {
    const d = new Date(m.ts); const day = d.getFullYear() + '년 ' + (d.getMonth() + 1) + '월 ' + d.getDate() + '일';
    if (day !== lastDay) { lastDay = day; html += '<div class="day"><span>' + day + '</span></div>'; }
    const me = m.who === S.uid;
    html += '<div class="m ' + (me ? 'me' : '') + '"><div>' + (me || room.dm ? '' : '<div class="who">' + esc(uname(m.who)) + '</div>') + '<div class="bub' + (m.kind === 'img' ? ' pic' : m.kind === 'stk' ? ' pic' : '') + '" data-mid="' + m.id + '">' + (m.rto ? '<div class="rto">' + esc((uname(m.rto.who)) + ': ' + (m.rto.text || '')) + '</div>' : '') + msgBody(m) + '</div>' + reactLine(m) + '<div class="who" style="' + (me ? 'text-align:right' : '') + '">' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + '</div></div></div>';
  });
  const roomView = '<div class="chatroom"><div class="hdr"><button data-act="leave">‹</button><div class="grow t1">' + esc(roomTitle(room)) + (room.grp ? ' <span class="sub">(' + (room.members || []).length + '명)</span>' : '') + '</div></div><div class="msgs" id="msgbox">' + (html || '<div class="empty">첫 메시지를 남겨 보세요.</div>') + '</div>' + (S.replyTo ? '<div class="replybar"><span>↩ ' + esc(uname(S.replyTo.who)) + '에게 답장: ' + esc(S.replyTo.text) + '</span><button data-act="replyOff" aria-label="답장 취소">✕</button></div>' : '') + stkPanelHTML() + '<div class="send"><button class="plus" data-act="attach" aria-label="사진·파일 보내기">+</button><input id="msgin" placeholder="메시지 입력" autocomplete="off"><button class="plus" data-act="stkPanel" aria-label="이모지·스티커">☺</button><button data-act="send">보내기</button></div><input type="file" id="fpick" hidden></div>';
  return wide ? '<div class="chatwrap">' + lst + roomView + '</div>' : roomView;
}
const FRIENDS = ['나인이', '복이', '호야', '깍이', '도리'];
const STK_CAPS = [["신규계약 성공!", "축하축하~!", "대단해 대단해", "아 빡쳐!!", "정신 차려!", "힘내요 우리!", "POS 설치 중", "여보세요~ 나인입니다", "현장 AS 출동!", "공부하는 대표님", "굿샷~ 접대골프", "토스단말기 어떠세요", "회의 또 회의", "사장님 수다 삼매경", "수수료 입금 완료", "OK 접수 완료"], ["복 터졌다 계약!", "복 받으세요~", "캬 대단하다", "복이도 화나!", "정신 줄 잡아요", "힘내면 복와요", "랜선 공사 중", "전화 응대 중", "단말기 도색 중", "독서 삼매경", "차에서 꿀잠", "단말기 수리 중", "서류 접수했습니다", "돈복 터졌다", "점심 뭐 먹지", "복 감사합니다"], ["어흥! 계약 따냈다", "축하 어흥~", "대단하다 어흥", "어흥 빡쳤다!!", "정신 차려 어흥", "힘내라 어흥!", "AS 출동 어흥", "랜선 깔자 어흥", "테이블오더 설치", "호야 홀인원!", "토스 영업 어흥", "서류 작성 중", "보고서 쓰는 중", "퇴근하고 싶다", "죄송합니다...", "알겠습니다 어흥"], ["깍깍! 계약 성공", "까치가 물어왔다", "깍! 대단해", "깍깍 빡쳐!!", "깍! 정신 차려", "힘내 깍깍", "깍깍 전화 중", "상담원 연결 중", "블로그 작업 중", "보고서 쓰는 중", "깍깍 회의 중", "사장님 깍깍 수다", "서류 접수 완료", "엉엉 깍깍", "감사합니다 깍", "점심은 뭐 쪼지"], ["도리 계약 땄다!", "도토리 축하~", "도리도리 대단해", "도리 빡쳐!!", "정신 차려 도리", "힘내 도리도리", "쇼핑몰 작업 중", "서류 작성 중", "공부하는 도리", "POS 설치 중", "차에서 농땡이", "도리 골프 연습", "토스 영업 중", "월요일 싫어", "도토리값 입금", "도리 반성중"]];
const REACTS = ['👍', '❤️', '😂', '😮', '😢', '🙏', '🔥', '👏'];
const EMOJIS = '😀 😁 😂 🤣 😊 😍 😘 😎 🤔 😅 😭 😡 👍 👏 🙏 💪 🔥 ❤️ 🎉 ✅ ⭕ ❌ 📞 📄 💰 🚗 ☕ 🍚'.split(' ');
const stkSrc = k => 'stk/stk_' + (String(k).startsWith('c') ? k : pad(Number(k) || 1)) + '.png';
function linkify(t) { // 글 속 웹 주소는 눌러서 열리게
  return esc(t).replace(/(https?:\/\/[^\s<]+|www\.[^\s<]+|[a-z0-9-]+\.(?:com|net|kr|co\.kr|org|io)(?:\/[^\s<]*)?)/gi, u => '<a href="' + (/^https?:/i.test(u) ? u : 'https://' + u) + '" target="_blank" rel="noopener">' + u + '</a>');
}
function msgBody(m) {
  if (m.kind === 'stk') return '<img class="stk" src="' + stkSrc(m.blob) + '" alt="' + esc(m.text || '스티커') + '">';
  if (m.kind === 'img') return '<img class="cimg" data-blob="' + esc(m.blob) + '" data-n="' + (m.chunks || 0) + '" data-name="' + esc(m.fname || '사진.jpg') + '" alt="사진" data-act="viewImg">';
  if (m.kind === 'file') return '<button class="fbtn" data-act="openFile" data-arg="' + esc(m.blob + '|' + (m.chunks || 0) + '|' + (m.fname || '파일')) + '">📎 ' + esc(m.fname || '파일') + (m.size ? ' <small>' + Math.max(1, Math.round(m.size / 1024)) + 'KB · 눌러서 받기</small>' : '') + '</button>';
  return linkify(msgText(m));
}
function reactLine(m) {
  const r = m.react || {}; const cnt = {}; Object.values(r).forEach(e => cnt[e] = (cnt[e] || 0) + 1);
  const mine = r[String(S.uid)];
  return Object.keys(cnt).length ? '<div class="reacts">' + Object.keys(cnt).map(e => '<button class="' + (e === mine ? 'on' : '') + '" data-act="react" data-arg="' + m.id + '|' + e + '">' + e + (cnt[e] > 1 ? ' ' + cnt[e] : '') + '</button>').join('') + '</div>' : '';
}
ACT.react = a => { const [id, e] = a.split('|'); const m = byId('msgs', id); if (!m) return; m.react = Object.assign({}, m.react || {}); const k = String(S.uid); if (m.react[k] === e) delete m.react[k]; else m.react[k] = e; put('msgs', m); closeModal(); render(); };
ACT.msgMenu = id => { // 말풍선을 누르면: 반응 · 답장 · 복사 · 삭제(내 것)
  const m = byId('msgs', id); if (!m) return; const mine = m.who === S.uid;
  modal('<div class="reactbar">' + REACTS.map(e => '<button data-act="react" data-arg="' + m.id + '|' + e + '">' + e + '</button>').join('') + '</div>' +
    '<button class="mi" data-act="reply" data-arg="' + m.id + '"><span>↩ 답장</span></button>' + (m.kind ? '' : '<button class="mi" data-act="copyMsg" data-arg="' + m.id + '"><span>📋 복사</span></button>') +
    (mine ? '<button class="mi" data-act="delMsg" data-arg="' + m.id + '"><span style="color:var(--red)">🗑 삭제</span></button>' : '') + '<button class="btn full" data-act="close">닫기</button>');
};
const snippet = m => m.kind === 'img' ? '사진' : m.kind === 'stk' ? '스티커' : m.kind === 'file' ? '파일: ' + (m.fname || '') : ((m.text || '').replace(/\n/g, ' ').slice(0, 60) + ((m.text || '').length > 60 ? '…' : ''));
ACT.reply = id => { const m = byId('msgs', id); if (!m) return; S.replyTo = {id: m.id, who: m.who, text: snippet(m)}; closeModal(); render(); setTimeout(() => { const i = $('#msgin'); if (i) i.focus(); }, 0); };
ACT.replyOff = () => { S.replyTo = null; render(); };
ACT.copyMsg = id => { const m = byId('msgs', id); closeModal(); try { navigator.clipboard.writeText(m.text || ''); toast('복사했어요.'); } catch (e) { toast('복사하지 못했어요.'); } };
ACT.delMsg = id => { const m = byId('msgs', id); if (!m) return; askConfirm('이 메시지를 지울까요?', '대화에서 사라지고, 서버에는 기록이 남아요.', '삭제', () => { m.deleted = true; put('msgs', m); render(); }, true); };
ACT.stkPanel = () => { S.stkOpen = !S.stkOpen; render(); };
ACT.stkTab = i => { S.stkTab = Number(i); render(); };
ACT.emoji = e => { const i = $('#msgin'); if (!i) return; i.value += e; i.focus(); };
ACT.sendStk = a => {
  const [key, cap] = a.split('|'); const room = ensureRoom(); if (!room) return;
  const m = create('msgs', {room: room.id, who: S.uid, text: cap, ts: Date.now(), kind: 'stk', blob: key, fname: '', size: 0});
  if (room.mem && room.mem.length) m.mem = room.mem.slice(); put('msgs', m); S.stkOpen = false; render();
};
function stkPanelHTML() {
  if (!S.stkOpen) return ''; const t = S.stkTab || 0;
  const tabs = '<div class="stktabs">' + ['이모지'].concat(FRIENDS).map((n, i) => '<button class="' + (i === t ? 'on' : '') + '" data-act="stkTab" data-arg="' + i + '">' + n + '</button>').join('') + '</div>';
  const body = t === 0 ? '<div class="emgrid">' + EMOJIS.map(e => '<button data-act="emoji" data-arg="' + e + '">' + e + '</button>').join('') + '</div>'
    : '<div class="stkgrid">' + STK_CAPS[t - 1].map((c, i) => { const k = 'c' + t + '_' + pad(i + 1); return '<button data-act="sendStk" data-arg="' + k + '|' + esc(c) + '" title="' + esc(c) + '"><img src="' + stkSrc(k) + '" alt="' + esc(c) + '" loading="lazy"></button>'; }).join('') + '</div>';
  return '<div class="stkpanel">' + tabs + body + '</div>';
}
// 사진·파일은 서버 보관함(blobs)에 600KB 조각(id_0, id_1 …)으로 들어 있다 — 앱과 같은 형식
const blobCache = new Map();
function fetchBlob(id, chunks) {
  if (blobCache.has(id)) return blobCache.get(id);
  const n = Math.max(1, chunks || 0); const ids = chunks > 0 ? [...Array(n).keys()].map(i => id + '_' + i) : [id];
  const pr = Promise.all(ids.map(d => fs.collection('blobs').doc(d).get())).then(docs => {
    let mime = 'application/octet-stream'; const parts = docs.map(dc => { if (!dc.exists) throw new Error('없음'); mime = dc.get('mime') || mime; const b = atob(dc.get('d')); const u = new Uint8Array(b.length); for (let i = 0; i < b.length; i++) u[i] = b.charCodeAt(i); return u; });
    return URL.createObjectURL(new Blob(parts, {type: mime}));
  });
  pr.catch(() => blobCache.delete(id)); blobCache.set(id, pr); return pr;
}
function loadImages() {
  if (DEMO) return;
  document.querySelectorAll('img.cimg:not([src])').forEach(im => { im.src = 'data:image/gif;base64,R0lGODlhAQABAAAAACw='; fetchBlob(im.dataset.blob, Number(im.dataset.n)).then(u => { im.src = u; im.classList.add('ok'); }).catch(() => { im.alt = '사진을 불러오지 못했어요'; im.classList.add('bad'); }); });
}
ACT.viewImg = (a, el) => { if (!el.classList.contains('ok')) return; modal('<img src="' + el.src + '" alt="사진" style="width:100%;border-radius:14px"><div class="btns" style="margin-top:10px"><a class="btn tint" style="text-align:center;text-decoration:none" href="' + el.src + '" download="' + esc(el.dataset.name) + '">저장</a><button class="btn" data-act="close">닫기</button></div>'); };
ACT.openFile = a => {
  if (DEMO) return toast('데모에서는 파일을 받을 수 없어요.');
  const [id, ch, name] = a.split('|'); toast('받는 중…');
  fetchBlob(id, Number(ch)).then(u => { const l = document.createElement('a'); l.href = u; l.download = name; document.body.appendChild(l); l.click(); l.remove(); }).catch(() => toast('파일을 받지 못했어요. 인터넷을 확인해 주세요.'));
};
function ensureRoom() {
  let room = byId('rooms', S.room);
  if (!room && S.dmDraft && S.dmDraft.id === S.room) { room = S.dmDraft; put('rooms', room); S.dmDraft = null; }
  return room;
}
ACT.attach = () => { const f = $('#fpick'); f.value = ''; f.onchange = () => { if (f.files[0]) sendFile(f.files[0]); }; f.click(); };
function b64of(u8) { let s = ''; for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000)); return btoa(s); }
function shrinkImage(file) { // 앱과 같이 긴 쪽 1280px, 600KB 이하 JPEG 으로 줄인다
  return new Promise((ok, no) => {
    const img = new Image(); img.onload = () => {
      const k = Math.min(1, 1280 / Math.max(img.width, img.height)); const c = document.createElement('canvas'); c.width = Math.round(img.width * k); c.height = Math.round(img.height * k);
      c.getContext('2d').drawImage(img, 0, 0, c.width, c.height); URL.revokeObjectURL(img.src);
      const go = q => c.toBlob(b => { if (!b) return no(new Error('jpeg')); if (b.size > 600 * 1024 && q > 0.4) return go(q - 0.1); b.arrayBuffer().then(x => ok(new Uint8Array(x))); }, 'image/jpeg', q);
      go(0.85);
    }; img.onerror = () => no(new Error('img')); img.src = URL.createObjectURL(file);
  });
}
async function sendFile(file) {
  if (DEMO) return toast('데모에서는 사진·파일을 보낼 수 없어요.');
  const isImg = /^image\//.test(file.type) && !/gif|svg/.test(file.type);
  if (!isImg && file.size > 3 * 1024 * 1024) return toast('파일은 3MB까지 보낼 수 있어요.');
  const room = ensureRoom(); if (!room) return;
  toast('보내는 중…');
  try {
    const data = isImg ? await shrinkImage(file) : new Uint8Array(await file.arrayBuffer());
    const d = new Date(), name = isImg ? '사진_' + pad(d.getMonth() + 1) + pad(d.getDate()) + '_' + pad(d.getHours()) + pad(d.getMinutes()) + pad(d.getSeconds()) + '.jpg' : file.name;
    const mime = isImg ? 'image/jpeg' : (file.type || 'application/octet-stream');
    const id = String(nextId()), CH = 600 * 1024, n = Math.max(1, Math.ceil(data.length / CH));
    await Promise.all([...Array(n).keys()].map(i => fs.collection('blobs').doc(id + '_' + i).set({d: b64of(data.subarray(i * CH, (i + 1) * CH)), mime, name, i, n, u: Date.now(), by: S.uid})));
    const m = create('msgs', {room: room.id, who: S.uid, text: isImg ? '사진' : name, ts: Date.now(), kind: isImg ? 'img' : 'file', blob: id, fname: name, size: data.length, chunks: n});
    if (room.mem && room.mem.length) m.mem = room.mem.slice();
    put('msgs', m); render();
  } catch (e) { toast('보내지 못했어요. 인터넷을 확인해 주세요.'); }
}
ACT.room = id => { S.room = Number(id); S.dmDraft = null; render(); };
ACT.leave = () => { markRead(S.room); S.room = 0; S.dmDraft = null; render(); };
ACT.dm = uid => {
  uid = Number(uid); const lo = Math.min(uid, S.uid), hi = Math.max(uid, S.uid);
  const ex = list('rooms').find(r => r.dm && r.members && Math.min(...r.members) === lo && Math.max(...r.members) === hi);
  if (ex) { S.room = ex.id; S.dmDraft = null; return render(); }
  const other = user(uid); let id = 1500000000 + Number((BigInt(lo) * 1000003n + BigInt(hi)) % 400000000n);
  if (S.data.rooms.has(id)) id = nextId();
  const a = e164(S.phone), b = e164(other.phone || '');
  S.dmDraft = {id, created: nowStr(), name: '', dm: true, members: [lo, hi], mem: lo === S.uid ? [a, b] : [b, a]};
  S.room = id; render();
};
ACT.send = () => {
  const inp = $('#msgin'); const text = inp.value.trim(); if (!text) return;
  const room = ensureRoom(); if (!room) return;
  const m = create('msgs', {room: room.id, who: S.uid, text, ts: Date.now()});
  if (room.mem && room.mem.length) m.mem = room.mem.slice();
  if (S.replyTo) { m.rto = S.replyTo; S.replyTo = null; }
  put('msgs', m); inp.value = ''; render(); setTimeout(() => { const i = $('#msgin'); if (i) i.focus(); }, 0);
};
document.addEventListener('keydown', e => { if (e.key === 'Enter' && !e.isComposing && e.target.id === 'msgin') { e.preventDefault(); ACT.send(); } });
ACT.newGroup = () => {
  const staff = list('users').filter(u => u.id !== S.uid && u.active !== false && u.phone).sort((a, b) => a.name.localeCompare(b.name));
  formModal('그룹 채팅방 만들기', '<input id="f_n" placeholder="그룹 이름 (필수)"><div class="sub" style="margin-top:10px">초대할 직원</div>' + staff.map(u => '<label class="chk"><input type="checkbox" class="gm" value="' + u.id + '"><span>' + esc(u.name) + '</span></label>').join(''), () => {
    const n = $('#f_n').value.trim(); if (!n) return warn('그룹 이름을 입력해 주세요.');
    const ids = [...document.querySelectorAll('.gm:checked')].map(c => Number(c.value)); if (!ids.length) return warn('초대할 직원을 골라 주세요.');
    const mem = [e164(S.phone)]; const members = [S.uid];
    ids.forEach(i => { members.push(i); mem.push(e164(user(i).phone)); });
    const r = create('rooms', {name: n, by: S.uid, grp: true, members, mem}); put('rooms', r); S.room = r.id; render(); toast('그룹 채팅방을 만들었어요.'); return true;
  }, '만들기');
};

// ---------- 가맹점 ----------
// 예전 앱은 연락처가 한 칸(phone)이었다 → 휴대폰 번호(010…)만 들어 있으면 휴대폰번호로 보여 준다 (수정해서 저장하면 그 칸으로 옮겨진다)
function mNums(m) { const p = m.phone || ''; return !m.mobile && /^01[016789]/.test(p.replace(/\D/g, '')) ? {phone: '', mobile: p} : {phone: p, mobile: m.mobile || ''}; }
function mHay(m) { const b = m.bizno || ''; return [m.name, b, b.replace(/-/g, ''), m.phone, m.mobile, m.addr, m.van].filter(Boolean).join(' '); }
function termsShort(m) { return m.ctype === '임대' ? '임대' + (m.camt ? ' · 월 ' + won(m.camt) : '') : m.ctype === '판매' ? '판매' + (m.camt ? ' · ' + won(m.camt) : '') : ''; }
function viewMerchants() {
  const all = list('merchants').sort((a, b) => (a.name || '').localeCompare(b.name || ''));
  const hits = all.filter(m => kmatch(mHay(m), S.mq));
  const shown = hits.slice(0, S.mlimit);
  const rows = !shown.length ? '' : isWide()
    ? '<div class="tbl"><table><thead><tr><th>상호</th><th>사업자번호</th><th>전화 / 휴대폰</th><th>VAN사</th><th>계약</th><th>주소</th></tr></thead><tbody>' + shown.map(m => {
      const n = mNums(m), t = termsShort(m);
      return '<tr data-act="merchant" data-arg="' + m.id + '"><td class="b">' + esc(m.name) + '</td><td>' + esc(m.bizno || '') + '</td><td>' + esc([n.phone, n.mobile].filter(Boolean).join(' / ')) + '</td><td>' + esc(m.van || '') + '</td><td>' + (t ? '<span class="chip">' + esc(t) + '</span>' : '') + '</td><td class="g">' + esc(m.addr || '') + '</td></tr>';
    }).join('') + '</tbody></table></div>'
    : shown.map(m => {
      const n = mNums(m), t = termsShort(m);
      return '<div class="card" data-act="merchant" data-arg="' + m.id + '"><div class="row"><div class="grow"><div class="t1">' + esc(m.name) + '</div><div class="t2">' + esc([m.bizno, n.phone || n.mobile, m.van].filter(Boolean).join(' · ') || '연락처 없음') + '</div>' + (m.addr ? '<div class="t2">' + esc(m.addr) + '</div>' : '') + '</div>' + (t ? '<span class="chip">' + esc(t) + '</span>' : '') + '</div></div>';
    }).join('');
  return '<h1>가맹점</h1><div class="sub">눌러서 보고' + (canEditMerchant() ? ', 오른쪽 아래 + 로 새로 등록해요.' : '요.') + '</div><input id="mq" data-q="mq" placeholder="🔍 상호·초성·사업자번호·전화" value="' + esc(S.mq) + '">' +
    '<div class="qres" data-for="mq"><div class="sub" style="margin-top:8px;font-weight:700">' + (S.mq.trim() ? '검색 결과 ' : '전체 ') + hits.length + '곳</div>' + rows +
    (hits.length > shown.length ? '<button class="btn full" data-act="mmore">더 보기 (' + (hits.length - shown.length) + '곳 남음)</button>' : '') +
    (!hits.length ? '<div class="empty">' + (S.mq.trim() ? '찾는 가맹점이 없어요.' : '등록된 가맹점이 없습니다.') + '</div>' : '') + '</div>';
}
ACT.mmore = () => { S.mlimit += 100; render(); };
ACT.merchant = id => {
  const m = byId('merchants', id); if (!m) return;
  if (S.logMid !== m.id) { S.logMid = m.id; S.logLimit = 30; }
  if (S.logMidF !== m.id) { S.logMidF = m.id; S.logKindF = ''; S.logDateF = ''; }
  const logs = list('mlogs').filter(g => g.merchant === m.id && (!S.logKindF || logKind(g.kind) === S.logKindF) && (!S.logDateF || g.date === S.logDateF)).sort((a, b) => (b.date || '').localeCompare(a.date || '') || (b.created || '').localeCompare(a.created || ''));
  const ln = (k, v) => v ? '<div class="line"><b>' + k + '</b><div>' + esc(v) + '</div></div>' : '';
  const nm = mNums(m); const ph = nm.phone.replace(/[^0-9+*#]/g, ''); const mp = nm.mobile.replace(/[^0-9+*#]/g, '');
  const callBtn = (num, raw, lb) => num ? '<a class="btn tint full" style="display:block;text-align:center;text-decoration:none;margin-top:6px" href="tel:' + num + '">📞 ' + lb + ' ' + esc(raw) + ' 전화 걸기</a>' : '';
  modal('<h3>' + esc(m.name) + '</h3>' + ln('사업자번호', m.bizno) + ln('전화번호', nm.phone) + ln('휴대폰번호', nm.mobile) + ln('이메일', m.email) + ln('주소', m.addr) + ln('VAN사', m.van) + ln('계약조건', termsText(m)) + ln('등급', gradeLabel(m.grade)) + ln('메모', m.memo) +
    callBtn(ph, nm.phone, '전화') + callBtn(mp, nm.mobile, '휴대폰') +
    '<h2>📝 응대 기록 (' + logs.length + '건)</h2><button class="btn gold full" data-act="newLog" data-arg="' + m.id + '">+ 응대 기록 남기기</button>' +
    '<div class="pills">' + [''].concat(KINDS).map(k => '<button class="pill ' + (S.logKindF === k ? 'on' : '') + '" data-act="logKindF" data-arg="' + m.id + '|' + k + '">' + (k || '전체') + '</button>').join('') + '</div>' +
    '<div class="row" style="gap:8px"><input type="date" id="logDateF" value="' + esc(S.logDateF || '') + '" data-mid="' + m.id + '" aria-label="날짜로 찾기">' + (S.logDateF ? '<button class="btn" style="flex:none" data-act="logDateOff" data-arg="' + m.id + '">날짜 해제</button>' : '') + '</div>' +
    logs.slice(0, S.logLimit).map(g => logCard(g, 'editLog|' + g.id + '|' + m.id)).join('') + (logs.length > S.logLimit ? '<button class="btn full" data-act="logmore" data-arg="' + m.id + '">더 보기 (' + (logs.length - S.logLimit) + '건 남음)</button>' : '') +
    '<div class="btns" style="margin-top:10px">' + (canEditMerchant() ? '<button class="btn" data-act="editMerchant" data-arg="' + m.id + '">정보 수정</button><button class="btn red" data-act="delMerchant" data-arg="' + m.id + '">삭제</button>' : '') + '<button class="btn" data-act="close">닫기</button></div>');
};
function logCard(g, act) {
  const [a, id, mid] = act.split('|');
  return '<div class="card" data-act="' + a + '" data-arg="' + id + '|' + mid + '"><span class="chip">' + esc(logKind(g.kind)) + '</span> <b style="font-size:13px">' + esc(g.date) + '</b>' + (logReq(g) ? '<div class="lb">요청사항</div><div style="white-space:pre-wrap">' + esc(logReq(g)) + '</div>' : '') + (logRes(g) ? '<div class="lb g">처리결과</div><div style="white-space:pre-wrap">' + esc(logRes(g)) + '</div>' : '') + '<div class="t2">' + esc(uname(g.who) + (g.editedBy ? ' · 수정 ' + uname(g.editedBy) + ' ' + (g.editedAt || '') : '')) + '</div></div>';
}
ACT.logKindF = a => { const [mid, k] = a.split('|'); S.logKindF = k; ACT.merchant(mid); };
ACT.logDateOff = mid => { S.logDateF = ''; ACT.merchant(mid); };
document.addEventListener('change', e => { if (e.target && e.target.id === 'logDateF') { S.logDateF = e.target.value; ACT.merchant(e.target.dataset.mid); } });
ACT.logmore = mid => { S.logLimit += 50; ACT.merchant(mid); };
ACT.editLog = a => {
  const [id, mid] = a.split('|'); const g = byId('mlogs', id); if (!g) return;
  const mine = g.who === S.uid || can('staff_manage');
  formModal('응대 기록 수정', '<div class="sub">' + esc(g.mname) + ' · 작성 ' + esc(uname(g.who)) + (g.editedBy ? ' · 마지막 수정 ' + esc(uname(g.editedBy)) + ' ' + esc(g.editedAt || '') : '') + '</div><input id="f_d" type="date" value="' + esc(g.date) + '"><select id="f_k">' + KINDS.map(k => '<option' + (k === logKind(g.kind) ? ' selected' : '') + '>' + k + '</option>').join('') + '</select><div class="lb">요청사항</div><textarea id="f_m" placeholder="고객이 요청한 내용">' + esc(logReq(g)) + '</textarea><div class="lb g">처리결과</div><textarea id="f_r" placeholder="어떻게 처리했는지">' + esc(logRes(g)) + '</textarea>' + (mine ? '<button class="btn red full" id="f_del">이 기록 지우기</button>' : ''), () => {
    const d = $('#f_d').value, t = $('#f_m').value.trim(), r = $('#f_r').value.trim(); if (!d || (!t && !r)) return warn('날짜와 요청사항(또는 처리결과)을 적어 주세요.');
    if (t !== logReq(g) || r !== logRes(g)) { g.editedBy = S.uid; g.editedAt = nowStr(); }
    g.date = d; g.kind = $('#f_k').value; g.req = t; g.res = r; g.text = t + (r ? (t ? '\n' : '') + '→ 처리: ' + r : ''); put('mlogs', g); render(); if (mid && byId('merchants', mid)) ACT.merchant(mid); toast('고쳤어요.'); return true;
  }, '저장');
  const del = $('#f_del'); if (del) del.onclick = () => askConfirm('이 기록을 지울까요?', '', '지우기', () => { g.deleted = true; put('mlogs', g); render(); if (mid && byId('merchants', mid)) ACT.merchant(mid); }, true);
};
ACT.newLog = mid => formModal('응대 기록', '<input id="f_d" type="date" value="' + todayStr() + '"><select id="f_k">' + KINDS.map(k => '<option>' + k + '</option>').join('') + '</select><div class="lb">요청사항</div><textarea id="f_m" placeholder="고객이 요청한 내용"></textarea><div class="lb g">처리결과</div><textarea id="f_r" placeholder="어떻게 처리했는지"></textarea>', () => {
  const d = $('#f_d').value, t = $('#f_m').value.trim(), r = $('#f_r').value.trim(); if (!d || (!t && !r)) return warn('날짜와 요청사항(또는 처리결과)을 적어 주세요.');
  const m = byId('merchants', mid); put('mlogs', create('mlogs', {merchant: m.id, mname: m.name, date: d, kind: $('#f_k').value, req: t, res: r, text: t + (r ? (t ? '\n' : '') + '→ 처리: ' + r : ''), who: S.uid})); render(); ACT.merchant(mid); toast('기록을 남겼어요.'); return true;
}, '저장');
const VANS = ['KICC(한국정보통신)', '다우데이타', 'NICE정보통신', 'KIS정보통신', '스마트로', 'KOCES', 'FDIK', '기타'];
const won = v => { const n = String(v || '').replace(/[^0-9]/g, ''); return n ? Number(n).toLocaleString('ko-KR') + '원' : ''; };
const amtLabel = ct => ct === '임대' ? '월 CMS 금액 (원)' : ct === '판매' ? '판매 금액 (원)' : '금액 (원, 계약구분을 먼저 골라요)';
function termsText(m) {
  const p = [];
  if (m.ctype === '임대') p.push('임대' + (m.camt ? ' · 월 CMS ' + won(m.camt) : ''));
  else if (m.ctype === '판매') p.push('판매' + (m.camt ? ' · 판매 ' + won(m.camt) : ''));
  if (m.terms) p.push(m.terms);
  return p.join(' · ');
}
const mfield = (id, label, ph, v, t) => '<label class="fl" for="' + id + '">' + label + '</label><input id="' + id + '" placeholder="' + ph + '" value="' + esc(v || '') + '"' + (t ? ' type="' + t + '"' : '') + '>';
const GRADES = ['S · 5만원 이상', 'A · 3만원 이상', 'B · 1만원 이상', 'C · 1만원 미만', '해지가맹점', '신규가맹점', '', '협력사'];
const gradeLabel = g => (g == null || g === '' || !GRADES[g]) ? '' : g + '코드 · ' + GRADES[g];
function merchantForm(m) {
  m = m || {}; const nm = mNums(m);
  return mfield('f_n', '상호 (필수)', '예: 토스단말기 전문', m.name) + mfield('f_b', '사업자번호', '예: 123-45-67890', m.bizno) +
    mfield('f_p', '전화번호', '예: 062-123-4567', nm.phone, 'tel') + mfield('f_mo', '휴대폰번호', '예: 010-1234-5678', nm.mobile, 'tel') +
    mfield('f_e', '이메일', '예: name@example.com', m.email, 'email') + mfield('f_a', '주소', '예: 광주 동구 …', m.addr) +
    '<label class="fl" for="f_v">VAN사</label><input id="f_v" list="vanlist" placeholder="목록에서 고르거나 직접 입력" value="' + esc(m.van || '') + '"><datalist id="vanlist">' + VANS.map(v => '<option value="' + esc(v) + '">').join('') + '</datalist>' +
    '<label class="fl" for="f_ct">계약구분</label><select id="f_ct">' + ['', '임대', '판매'].map(v => '<option value="' + v + '"' + ((m.ctype || '') === v ? ' selected' : '') + '>' + (v || '(선택 안 함)') + '</option>').join('') + '</select>' +
    '<label class="fl" for="f_ca" id="f_cal">' + amtLabel(m.ctype) + '</label><input id="f_ca" inputmode="numeric" placeholder="숫자만 (예: 30000)" value="' + esc(m.camt || '') + '">' +
    mfield('f_t', '계약조건 (추가 내용)', '예: 36개월 약정, 위약금 없음', m.terms) +
    '<label class="fl" for="f_g">등급 코드 (밴피 기준)</label><select id="f_g">' + GRADES.map((g, i) => g ? '<option value="' + i + '"' + (Number(m.grade == null ? 5 : m.grade) === i ? ' selected' : '') + '>' + gradeLabel(i) + '</option>' : '').join('') + '</select>' +
    '<label class="fl" for="f_m">메모</label><textarea id="f_m" placeholder="메모">' + esc(m.memo || '') + '</textarea>';
}
function wireMerchantForm() { const ct = $('#f_ct'); if (ct) ct.onchange = () => { $('#f_cal').textContent = amtLabel(ct.value); }; }
function readMerchant(o) {
  o.name = $('#f_n').value.trim(); o.bizno = $('#f_b').value.trim(); o.phone = $('#f_p').value.trim(); o.mobile = $('#f_mo').value.trim(); o.email = $('#f_e').value.trim();
  o.addr = $('#f_a').value.trim(); o.van = $('#f_v').value.trim(); o.ctype = $('#f_ct').value; o.camt = $('#f_ca').value.replace(/[^0-9]/g, ''); o.terms = $('#f_t').value.trim(); o.grade = Number($('#f_g').value); o.memo = $('#f_m').value.trim(); return o;
}
// ---------- 가맹점 수정 암호 (화면 잠금) ----------
const LOCK_MS = 5 * 60 * 1000; let unlockedAt = 0;
const lockDoc = () => list('config').filter(c => c.key === 'merchant_lock' && c.hash).sort((a, b) => (b.created || '').localeCompare(a.created || ''))[0];
const canEditMerchant = () => can('merchant_edit') || can('staff_manage');
async function sha(t) { const b = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(t)); return [...new Uint8Array(b)].map(x => x.toString(16).padStart(2, '0')).join(''); }
function needLock(then) {
  if (!canEditMerchant()) { toast('가맹점 수정 권한이 없어요. 관리자에게 요청해 주세요.'); return; }
  const L = lockDoc(); if (!L || Date.now() - unlockedAt < LOCK_MS) return then();
  formModal('암호 확인', '<div class="sub">가맹점 정보를 고치려면 암호가 필요해요.</div><input id="f_pw" type="password" autocomplete="off" placeholder="암호">', () => {
    const v = $('#f_pw').value; sha(L.salt + v).then(h => { if (h === L.hash) { unlockedAt = Date.now(); closeModal(); then(); } else { toast('암호가 맞지 않아요.'); } }); return false;
  }, '확인');
}
ACT.mlockSet = () => {
  if (!can('staff_manage')) return toast('관리자만 설정할 수 있어요.');
  const L = lockDoc();
  formModal('가맹점 수정 암호', '<div class="sub">' + (L ? '암호가 설정돼 있어요. 바꾸려면 현재 암호도 입력해 주세요.' : '아직 암호가 없어서 누구나(권한이 있으면) 수정할 수 있어요. 암호를 정하면 수정할 때마다 물어봐요.') + '</div>' +
    (L ? '<input id="f_old" type="password" autocomplete="off" placeholder="현재 암호">' : '') + '<input id="f_new" type="password" autocomplete="off" placeholder="새 암호 (4자 이상)"><input id="f_new2" type="password" autocomplete="off" placeholder="새 암호 한 번 더">', () => {
    const nw = $('#f_new').value; if (nw.length < 4) return warn('암호는 4자 이상으로 해 주세요.'); if (nw !== $('#f_new2').value) return warn('새 암호가 서로 달라요.');
    const done = () => { const salt = Math.random().toString(36).slice(2) + Date.now().toString(36); sha(salt + nw).then(h => { const o = L || create('config', {key: 'merchant_lock'}); o.salt = salt; o.hash = h; o.changedBy = S.uid; o.changedAt = nowStr(); put('config', o); unlockedAt = Date.now(); closeModal(); render(); toast('암호를 저장했어요.'); }); };
    if (L) sha(L.salt + $('#f_old').value).then(h => { if (h === L.hash) done(); else toast('현재 암호가 맞지 않아요.'); }); else done();
    return false;
  }, '저장');
};
ACT.mlockOff = () => {
  if (!can('staff_manage')) return; const L = lockDoc(); if (!L) return;
  formModal('암호 끄기', '<div class="sub">현재 암호를 입력하면 잠금을 꺼요. (이후 권한이 있는 직원은 암호 없이 수정해요)</div><input id="f_old" type="password" autocomplete="off" placeholder="현재 암호">', () => {
    sha(L.salt + $('#f_old').value).then(h => { if (h === L.hash) { L.salt = ''; L.hash = ''; L.key = 'merchant_lock_off'; put('config', L); closeModal(); render(); toast('암호를 껐어요.'); } else toast('현재 암호가 맞지 않아요.'); }); return false;
  }, '끄기');
};
ACT.newMerchant = () => needLock(() => { let dupOk = false; formModal('가맹점 등록', merchantForm(null), () => {
  if (!$('#f_n').value.trim()) return warn('상호를 입력해 주세요.');
  const bd = $('#f_b').value.replace(/\D/g, ''); const dup = bd.length >= 10 && list('merchants').find(m => (m.bizno || '').replace(/\D/g, '') === bd);
  if (dup && !dupOk) { dupOk = true; return warn('같은 사업자번호의 가맹점 ‘' + dup.name + '’이(가) 이미 있어요. 그래도 등록하려면 저장을 한 번 더 누르세요.'); }
  const o = readMerchant(create('merchants', {grade: 5})); put('merchants', o); render(); toast('저장했어요.'); return true;
}, '저장'); wireMerchantForm(); });
ACT.delMerchant = id => needLock(() => {
  const m = byId('merchants', id); if (!m) return;
  const c = list('mlogs').filter(g => g.merchant === m.id).length;
  askConfirm('‘' + m.name + '’ 가맹점을 삭제할까요?', '목록에서만 지워지고, 응대 기록 ' + c + '건과 서버의 기록은 그대로 남아요. (앱의 가맹점 보호·복원에서 되살릴 수 있어요)', '삭제', () => { m.deleted = true; put('merchants', m); render(); toast('삭제했어요.'); }, true);
});
const MF = {grade: '등급', name: '상호', bizno: '사업자번호', phone: '전화번호', mobile: '휴대폰번호', email: '이메일', addr: '주소', van: 'VAN사', ctype: '계약구분', camt: '금액', terms: '계약조건', memo: '메모', deleted: '삭제'};
ACT.editMerchant = id => needLock(() => {
  const key = 'merchants/' + Number(id), base = S.pushed[key], before = JSON.stringify(byId('merchants', id)); let forced = false;
  formModal('가맹점 정보 수정', merchantForm(byId('merchants', id)) + '<div id="f_conf"></div>', () => {
    if (!$('#f_n').value.trim()) return warn('상호를 입력해 주세요.');
    const now = byId('merchants', id);
    if (!forced && (S.pushed[key] !== base || JSON.stringify(now) !== before)) { // 창을 연 사이에 다른 곳(앱·다른 PC)에서 저장됨
      const old = JSON.parse(before || '{}'); const ch = Object.keys(MF).filter(k => String(old[k] || '') !== String(now[k] || '')).map(k => MF[k]);
      $('#f_conf').innerHTML = '<div class="conf">⚠ 이 창을 연 뒤 다른 직원이 이 가맹점을 고쳤어요' + (ch.length ? ' (' + ch.join(', ') + ')' : '') + '.<br>지금 저장하면 그 내용을 내 입력으로 덮어써요. 그래도 저장하려면 <b>저장</b>을 한 번 더 누르고, 아니면 취소 후 다시 열어 최신 내용을 확인하세요.</div>';
      forced = true; return warn('다른 직원이 방금 고쳤어요. 아래 안내를 확인해 주세요.');
    }
    const o = readMerchant(now); put('merchants', o); render(); toast('저장했어요.'); return true;
  }, '저장'); wireMerchantForm();
});
function viewAllLogs() {
  const mq = (S.lmq || '').trim(); const q = S.lq || '';
  let found = '';
  if (mq) {
    const hits = list('merchants').filter(m => { const b = m.bizno || ''; return kmatch((m.name || '') + ' ' + b + ' ' + b.replace(/-/g, ''), mq); }).sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    found = hits.slice(0, 20).map(m => { const c = list('mlogs').filter(g => g.merchant === m.id).length; return '<div class="card" data-act="merchant" data-arg="' + m.id + '"><div class="row"><div class="grow"><div class="t1">' + esc(m.name) + '</div><div class="t2">' + esc((m.bizno || '사업자번호 없음') + ' · 응대 기록 ' + c + '건') + '</div></div><span style="color:var(--deep);font-size:22px">›</span></div></div>'; }).join('') +
      (hits.length > 20 ? '<div class="sub">더 있어요. 검색어를 더 적어 주세요.</div>' : '') + (hits.length ? '' : '<div class="empty">찾는 가맹점이 없어요. (상호·초성·사업자번호로 찾아요)</div>');
  }
  const ls = list('mlogs').filter(g => kmatch(logAll(g), q)).sort((a, b) => (b.date || '').localeCompare(a.date || '') || (b.created || '').localeCompare(a.created || ''));
  return '<h1>가맹점 응대 기록</h1><div class="sub">상호·초성·사업자번호로 가맹점을 찾아 눌러서 기록을 보고·추가·수정해요.</div><input id="lmq" data-q="lmq" placeholder="🔍 상호·초성·사업자번호" value="' + esc(S.lmq) + '"><div class="qres" data-for="lmq">' + found + '</div>' +
    '<h2>최근 기록</h2><input id="lq" data-q="lq" placeholder="가맹점·내용 검색 (초성 가능)" value="' + esc(q) + '"><div class="qres" data-for="lq"><div class="sub" style="margin-top:8px;font-weight:700">기록 ' + ls.length + '건 · 눌러서 수정</div>' +
    ls.slice(0, 100).map(g => logCard(g, 'editLog|' + g.id + '|' + g.merchant).replace('<span class="chip">' + esc(logKind(g.kind)) + '</span>', '<span class="chip">' + esc(logKind(g.kind)) + '</span> <b>' + esc(g.mname) + '</b>')).join('') + (ls.length > 100 ? '<div class="sub">최근 100건만 보여요.</div>' : '') + (ls.length ? '' : '<div class="empty">기록이 없어요.</div>') + '</div>';
}

// ---------- 바코드 스캔 ----------
// PC: USB·블루투스 바코드 스캐너는 키보드처럼 번호를 치고 Enter 를 누른다 → 입력칸에 커서를 두고 찍으면 된다.
// 휴대폰(아이폰): 카메라로 찍는다. 브라우저 기본 기능(BarcodeDetector)이 없으면 zxing(web/vendor)을 그때 불러온다.
let zxingP = null;
function loadZxing() {
  return zxingP || (zxingP = new Promise((ok, no) => {
    const s = document.createElement('script'); s.src = 'vendor/zxing-0.21.3.min.js';
    s.onload = () => ok(window.ZXing); s.onerror = () => { zxingP = null; no(new Error('zxing')); }; document.head.appendChild(s);
  }));
}
async function nativeDetector() {
  try { if (!('BarcodeDetector' in window)) return null; const f = await BarcodeDetector.getSupportedFormats(); return f && f.length ? new BarcodeDetector({formats: f}) : null; } catch (e) { return null; }
}
function scanCamera(onCode) {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) return toast('이 브라우저는 카메라를 쓸 수 없어요. 번호를 직접 입력해 주세요.');
  const ov = document.createElement('div'); ov.className = 'scanov';
  ov.innerHTML = '<video playsinline muted autoplay></video><div class="scanbox"></div><div class="scanmsg">바코드를 네모 안에 맞춰 주세요</div><button class="btn">닫기</button>';
  document.body.appendChild(ov);
  const video = ov.querySelector('video'); let stream = null, timer = 0, reader = null, done = false;
  const stop = () => { done = true; clearInterval(timer); if (reader) { try { reader.reset(); } catch (e) {} } if (stream) stream.getTracks().forEach(t => t.stop()); ov.remove(); };
  const hit = code => { if (done || !code) return; stop(); if (navigator.vibrate) navigator.vibrate(80); onCode(String(code).trim()); };
  const fail = e => { if (done) return; stop(); toast(e && e.name === 'NotAllowedError' ? '카메라 권한이 필요해요. 설정에서 이 사이트의 카메라를 허용해 주세요.' : '카메라를 열지 못했어요. 번호를 직접 입력해 주세요.'); };
  ov.querySelector('button').onclick = stop;
  nativeDetector().then(det => {
    if (done) return;
    if (det) {
      return navigator.mediaDevices.getUserMedia({video: {facingMode: 'environment'}, audio: false}).then(s => {
        stream = s; if (done) return stop(); video.srcObject = s; video.play().catch(() => {});
        timer = setInterval(() => { if (video.readyState >= 2) det.detect(video).then(r => { if (r && r[0]) hit(r[0].rawValue); }).catch(() => {}); }, 250);
      });
    }
    return loadZxing().then(Z => { if (done) return; reader = new Z.BrowserMultiFormatReader(); return reader.decodeFromConstraints({video: {facingMode: 'environment'}}, video, r => { if (r) hit(r.getText()); }); });
  }).catch(fail);
}
ACT.scanInto = id => scanCamera(code => { const el = $('#' + id); if (!el) return; if (el.tagName === 'TEXTAREA') el.value = (el.value.trim() ? el.value.trim() + '\n' : '') + code; else el.value = code; el.dispatchEvent(new Event('input', {bubbles: true})); toast('읽었어요: ' + code); });

// ---------- 재고 ----------
const canPerf = () => !!(S.caps && (S.caps.perf || S.caps.admin)); // 단가(입고·출고)는 실적 권한자만 (서버 규칙과 같음)
const costOf = sid => list('costs').find(c => c.stock === sid);
function costDoc(sid) { let c = costOf(sid); if (!c) { c = create('costs', {stock: sid, buy: 0, sell: 0}); } return c; }
const numOf = v => Number(String(v || '').replace(/[^0-9]/g, '')) || 0;
function viewStock() {
  let asset = '';
  if (canPerf()) {
    let sum = 0, noPrice = 0;
    list('stock').forEach(r => { if (r.status !== '재고') return; const c = costOf(r.id); if (!c || !c.buy) noPrice += r.qty || 1; else sum += c.buy * (r.qty || 1); });
    asset = '<div class="card flat"><div class="sub" style="margin:0">보유 재고 자산 (입고 단가 합계)</div><div class="t1" style="font-size:22px;margin-top:2px">' + wonFmt(sum) + '</div>' + (noPrice ? '<div class="sub" style="color:var(--amber)">단가 미입력 ' + noPrice + '대는 빠져 있어요.</div>' : '') + '</div>';
  }
  return '<h1>재고</h1><div class="sub">장비를 직접 입력해 관리해요. 계약에서 장비를 고르면 ‘출고중’이 되고, 설치 완료하면 재고에서 빠집니다. 검색칸에 바코드 스캐너로 찍으면 그 장비가 바로 열려요.</div>' + asset +
    '<div class="row" style="gap:8px"><input id="sq" data-q="sq" placeholder="🔍 모델명·시리얼 (초성)" value="' + esc(S.sq || '') + '" autocomplete="off"><button class="btn tint" data-act="scanInto" data-arg="sq" style="flex:none" aria-label="카메라로 바코드 찍기">📷 바코드</button></div>' +
    '<div class="qres" data-for="sq">' + stockGroups() + '</div>';
}
function stockGroups() {
  const g = new Map();
  list('stock').filter(r => r.status !== '설치완료' && kmatch((r.model || '') + ' ' + (r.serial || ''), S.sq || '')).sort((a, b) => (a.model || '').localeCompare(b.model || '') || (a.serial || '').localeCompare(b.serial || ''))
    .forEach(r => { if (!g.has(r.model)) g.set(r.model, []); g.get(r.model).push(r); });
  if (!g.size) return '<div class="empty">' + ((S.sq || '').trim() ? '찾는 장비가 없어요.' : '등록된 재고가 없습니다. 오른쪽 아래 + 로 추가해 주세요.') + '</div>';
  return [...g.entries()].map(([model, rows]) => {
    let have = 0, out = 0, safe = 0;
    rows.forEach(r => { if (r.status === '출고중') out += r.qty || 1; else have += r.qty || 1; safe = Math.max(safe, r.safe || 0); });
    const low = safe > 0 && have < safe;
    return '<div class="card flat"><div class="row"><div class="grow t1">' + esc(model) + '</div><span class="chip"' + (low ? ' style="background:#fdecea;color:var(--red)"' : '') + '>재고 ' + have + (out ? ' · 출고중 ' + out : '') + '</span></div>' +
      (safe ? '<div class="sub" style="' + (low ? 'color:var(--red);font-weight:700' : '') + '">안전재고 ' + safe + '대' + (low ? ' — 부족해요!' : '') + '</div>' : '') +
      rows.map(r => '<button class="srow' + (r.status === '출고중' ? ' out' : '') + '" data-act="editStock" data-arg="' + r.id + '">' + esc(r.serial ? r.serial : '시리얼 없음 · ' + (r.qty || 1) + '개') + (r.status === '출고중' ? '<i>출고중</i>' : '') + '</button>').join('') + '</div>';
  }).join('');
}
// 스캐너로 검색칸에 시리얼을 찍으면(Enter) 그 장비를 바로 연다
document.addEventListener('keydown', e => {
  if (e.key !== 'Enter' || e.isComposing || !e.target || e.target.id !== 'sq') return;
  const c = e.target.value.trim().toLowerCase(); if (!c) return;
  const r = list('stock').find(x => (x.serial || '').trim().toLowerCase() === c && x.status !== '설치완료');
  if (r) { e.preventDefault(); ACT.editStock(r.id); }
});
ACT.newStock = () => formModal('재고 추가',
  '<label class="fl" for="f_mo">모델명 (필수)</label><input id="f_mo" placeholder="예: 토스 포스 T2">' +
  '<label class="fl" for="f_se">시리얼 (여러 개는 한 줄에 하나 — 스캐너로 계속 찍으면 줄이 바뀌어요)</label><textarea id="f_se" placeholder="시리얼 번호"></textarea><button class="btn tint full" data-act="scanInto" data-arg="f_se">📷 카메라로 바코드 찍기</button>' +
  '<label class="fl" for="f_q">시리얼이 없는 물품의 수량 (선택)</label><input id="f_q" inputmode="numeric" placeholder="예: 10">' +
  '<label class="fl" for="f_sf">안전재고 (이 수 아래면 알려줘요, 선택)</label><input id="f_sf" inputmode="numeric" placeholder="예: 3">' +
  (canPerf() ? '<label class="fl" for="f_b">입고 단가 (1대당 산 값, 원, 선택)</label><input id="f_b" inputmode="numeric" placeholder="예: 250000">' : ''), () => {
  const m = $('#f_mo').value.trim(); if (!m) return warn('모델명을 입력해 주세요.');
  const qn = numOf($('#f_q').value), sf = numOf($('#f_sf').value), by = canPerf() ? numOf($('#f_b').value) : 0;
  const ss = $('#f_se').value.split(/[\n,]+/).map(x => x.trim()).filter(Boolean);
  if (!ss.length && qn < 1) return warn('시리얼을 적거나 수량을 입력해 주세요.');
  const seen = new Set();
  for (const x of ss) { const k = x.toLowerCase(); if (seen.has(k) || list('stock').some(r => (r.serial || '').trim().toLowerCase() === k)) return warn('이미 있는 시리얼이에요: ' + x); seen.add(k); }
  const one = (serial, qty) => { const r = create('stock', {model: m, serial, qty, safe: sf, status: '재고', deleted: false}); put('stock', r); if (by > 0) { const c = costDoc(r.id); c.buy = by; put('costs', c); } };
  if (ss.length) ss.forEach(x => one(x, 1)); else one('', qn);
  render(); toast('재고에 추가했어요.'); return true;
}, '추가');
ACT.editStock = id => {
  const r = byId('stock', id); if (!r) return; const c = canPerf() ? costOf(r.id) : null; const mer = r.merchant ? byId('merchants', r.merchant) : null;
  formModal('재고 수정', '<label class="fl" for="f_mo">모델명</label><input id="f_mo" value="' + esc(r.model) + '"><label class="fl" for="f_se">시리얼</label><input id="f_se" value="' + esc(r.serial) + '"><button class="btn tint full" data-act="scanInto" data-arg="f_se">📷 카메라로 바코드 찍기</button>' +
    (r.serial ? '' : '<label class="fl" for="f_q">수량</label><input id="f_q" inputmode="numeric" value="' + (r.qty || 1) + '">') +
    '<label class="fl" for="f_sf">안전재고</label><input id="f_sf" inputmode="numeric" value="' + (r.safe || 0) + '">' +
    (canPerf() ? '<label class="fl" for="f_b">입고 단가 (산 값, 원)</label><input id="f_b" inputmode="numeric" value="' + (c && c.buy ? c.buy : '') + '"><label class="fl" for="f_s">출고 단가 (판 값, 원)</label><input id="f_s" inputmode="numeric" value="' + (c && c.sell ? c.sell : '') + '">' : '') +
    '<div class="sub" style="margin-top:10px">상태: ' + esc(r.status || '재고') + (r.status === '출고중' && mer ? ' · ' + esc(mer.name) : '') + '</div><button class="btn red full" id="f_del">이 재고 삭제</button>', () => {
    const m = $('#f_mo').value.trim(); if (!m) return warn('모델명을 입력해 주세요.');
    const se = $('#f_se').value.trim();
    if (se && se.toLowerCase() !== (r.serial || '').trim().toLowerCase() && list('stock').some(x => x.id !== r.id && (x.serial || '').trim().toLowerCase() === se.toLowerCase())) return warn('이미 있는 시리얼이에요: ' + se);
    r.model = m; r.serial = se; r.safe = numOf($('#f_sf').value); if ($('#f_q')) r.qty = Math.max(1, numOf($('#f_q').value)); put('stock', r);
    if (canPerf()) { const b2 = numOf($('#f_b').value), s2 = numOf($('#f_s').value); const cd = costOf(r.id) || (b2 || s2 ? costDoc(r.id) : null); if (cd) { cd.buy = b2; cd.sell = s2; put('costs', cd); } }
    render(); toast('저장했어요.'); return true;
  }, '저장');
  $('#f_del').onclick = () => askConfirm('이 재고를 지울까요?', (r.model || '') + ' ' + (r.serial || '') + '\n목록에서 지워집니다.', '지우기', () => { r.deleted = true; put('stock', r); render(); toast('삭제했어요.'); }, true);
};

// ---------- 가맹점 고르기 · 장비 고르기 (팝업 안에서 쓰는 조각) ----------
const MP_FILL = '<div class="lb">가맹점 조회 (선택 · 고르면 거래처·전화·주소가 자동으로 채워져요)</div><div id="mpSel" class="sub" style="display:none"></div><input id="mpq" placeholder="🔍 상호·초성·사업자번호" autocomplete="off"><div id="mpl" class="picks"></div>';
const MP_HTML = '<div class="lb">가맹점</div><div id="mpSel" class="sub">아직 고르지 않았어요. 아래에서 찾아 눌러 주세요.</div><input id="mpq" placeholder="🔍 상호·초성·사업자번호" autocomplete="off"><div id="mpl" class="picks"></div>';
function wireMerchantPicker(onPick) {
  const q = $('#mpq'), l = $('#mpl');
  const fill = () => { // 검색칸은 그대로 두고 목록만 바꾼다 → 한글을 치는 중에도 바로 걸러진다
    const v = q.value.trim(); if (!v) { l.innerHTML = ''; return; }
    const hits = list('merchants').filter(m => kmatch(mHay(m), v)).sort((a, b) => (a.name || '').localeCompare(b.name || '')).slice(0, 8);
    l.innerHTML = hits.map(m => { const n = mNums(m); return '<button type="button" class="pick" data-id="' + m.id + '"><b>' + esc(m.name) + '</b><span>' + esc([m.bizno, n.phone || n.mobile].filter(Boolean).join(' · ')) + '</span></button>'; }).join('') || '<div class="sub">찾는 가맹점이 없어요.</div>';
  };
  q.addEventListener('input', fill);
  l.onclick = ev => { const p = ev.target.closest('.pick'); if (!p) return; const m = byId('merchants', p.dataset.id); const s = $('#mpSel'); s.className = 'picked'; s.style.display = ''; s.innerHTML = '✔ <b>' + esc(m.name) + '</b>' + (m.bizno ? ' · ' + esc(m.bizno) : ''); q.value = ''; l.innerHTML = ''; onPick(m); };
}
const EQ_HTML = '<div class="lb">나가는 장비</div><div id="eqSel"></div><div class="row" style="gap:8px"><input id="eqq" placeholder="재고에서 찾기 (모델·시리얼)" autocomplete="off"><button type="button" class="btn tint" id="eqcam" style="flex:none" aria-label="카메라로 바코드 찍기">📷</button></div><div id="eqList" class="picks"></div>' +
  '<details class="direct"><summary>재고에 없는 장비 직접 입력</summary><input id="eqm" placeholder="모델명"><input id="eqs" placeholder="시리얼 (선택)"><button type="button" class="btn full" id="eqadd">장비 추가</button></details>';
function wireEquipPicker(sel) {
  const q = $('#eqq'), L = $('#eqList'), box = $('#eqSel');
  const showSel = () => { box.innerHTML = sel.length ? sel.map((e, i) => '<div class="eqi"><span>' + esc(e.model) + (e.serial ? ' / ' + esc(e.serial) : '') + (e.sid ? '' : ' <i>직접 입력</i>') + '</span><button type="button" data-i="' + i + '" aria-label="빼기">−</button></div>').join('') : '<div class="sub">아직 고른 장비가 없어요.</div>'; };
  const avail = () => list('stock').filter(r => r.status === '재고' && !sel.some(e => e.sid && e.sid === r.id));
  const fill = () => {
    const v = q.value.trim(); const rows = avail().filter(r => kmatch((r.model || '') + ' ' + (r.serial || ''), v)).sort((a, b) => (a.model || '').localeCompare(b.model || '')).slice(0, 40);
    L.innerHTML = rows.map(r => '<button type="button" class="pick" data-id="' + r.id + '"><b>' + esc(r.model) + '</b><span>' + (r.serial ? esc(r.serial) : '시리얼 없음 · ' + (r.qty || 1) + '개 보유') + '</span></button>').join('') || '<div class="sub">' + (v ? '재고에 맞는 장비가 없어요.' : '재고에 남은 장비가 없어요. 아래 ‘직접 입력’을 써 주세요.') + '</div>';
  };
  const addRow = r => { sel.push({sid: r.id, model: r.model, serial: r.serial || ''}); showSel(); fill(); };
  const byCode = code => {
    const c = code.trim().toLowerCase(); if (!c) return;
    const same = list('stock').filter(r => (r.serial || '').trim().toLowerCase() === c); const r = same.find(x => x.status === '재고') || same[0];
    if (r && r.status === '재고') { if (sel.some(e => e.sid === r.id)) toast('이미 고른 장비예요.'); else { addRow(r); toast('장비를 추가했어요: ' + r.model + ' · ' + r.serial); } }
    else if (r) toast('이미 출고된 장비예요: ' + code);
    else { const one = avail().filter(x => kmatch((x.model || '') + ' ' + (x.serial || ''), code)); if (one.length === 1) addRow(one[0]); else if (!/\s/.test(code) && code.length >= 4) { toast('재고에 없는 번호예요. 모델명을 적어 직접 입력해 주세요.'); $('.direct').open = true; $('#eqs').value = code; $('#eqm').focus(); } else return; }
    q.value = ''; fill();
  };
  q.addEventListener('input', fill);
  q.addEventListener('keydown', ev => { if (ev.key === 'Enter' && !ev.isComposing) { ev.preventDefault(); byCode(q.value); } });
  L.onclick = ev => { const p = ev.target.closest('.pick'); if (p) addRow(byId('stock', p.dataset.id)); };
  box.onclick = ev => { const b = ev.target.closest('button[data-i]'); if (b) { sel.splice(Number(b.dataset.i), 1); showSel(); fill(); } };
  $('#eqcam').onclick = () => scanCamera(byCode);
  $('#eqadd').onclick = () => { const m = $('#eqm').value.trim(); if (!m) return toast('모델명을 입력해 주세요.'); sel.push({sid: 0, model: m, serial: $('#eqs').value.trim()}); $('#eqm').value = ''; $('#eqs').value = ''; showSel(); };
  showSel(); fill();
}

// ---------- 계약 ----------
function daysLeft(ymd) { const a = new Date(todayStr() + 'T00:00:00'), b = new Date(String(ymd) + 'T00:00:00'); return isNaN(b) ? 99999 : Math.round((b - a) / 86400000); }
function dday(d) { return d === 99999 ? '' : d < 0 ? '만료 ' + (-d) + '일 지남' : d === 0 ? '오늘 만료' : 'D-' + d; }
function addMonths(ymd, m) { const p = String(ymd).split('-').map(Number); if (p.length < 3 || !p[0]) return ''; const d = new Date(p[0], p[1] - 1 + m, p[2]); return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
const amountLabel = k => k === 'CMS' ? 'CMS 금액' : k === '렌탈' ? '월 렌탈료' : '판매금액';
const expiringContracts = () => list('contracts').filter(c => c.expire && daysLeft(c.expire) <= 90).sort((a, b) => daysLeft(a.expire) - daysLeft(b.expire));
function contractCard(c) {
  const d = c.expire ? daysLeft(c.expire) : 99999; const n = (c.equip || []).length;
  return '<div class="card" data-act="contract" data-arg="' + c.id + '"><div class="row"><div class="grow t1">' + esc(c.client) + '</div>' + (c.expire ? '<span class="chip"' + (d <= 90 ? ' style="background:#fdecea;color:var(--red)"' : ' style="background:var(--field);color:var(--gray)"') + '>' + dday(d) + '</span>' : '') + '</div>' +
    '<div class="t2">' + esc(c.kind + (c.amount ? ' ' + wonFmt(c.amount) : '') + ' · 장비 ' + n + '대 · ' + (c.installed ? '설치 완료' : '설치 대기') + ' · ' + uname(c.who)) + '</div>' + (c.expire ? '<div class="t2">만료일 ' + esc(c.expire) + '</div>' : '') + '</div>';
}
function viewContracts() {
  const exp = expiringContracts(); const all = list('contracts').sort((a, b) => (b.created || '').localeCompare(a.created || ''));
  return '<h1>계약 관리</h1><div class="sub">올린 계약을 눌러 장비를 추가하거나 ‘설치 완료’를 처리해요. 오른쪽 아래 + 로 새 계약을 올립니다.' + (canPerf() ? '' : ' (내가 올린 계약만 보여요)') + '</div>' +
    (exp.length ? '<h2 style="color:var(--red)">만료가 가까운 계약 (3개월 이내)</h2>' + exp.map(contractCard).join('') : '') +
    '<h2>전체 계약</h2>' + (all.length ? all.map(contractCard).join('') : '<div class="empty">아직 올린 계약이 없습니다.</div>');
}
function takeStock(row, merchant, contract) { // 재고 한 줄을 출고 처리 (수량 여러 개짜리는 1개만 떼어 낸다) — 앱의 Store.takeStock 과 같다
  let use = row;
  if (!row.serial && (row.qty || 1) > 1) {
    row.qty = (row.qty || 1) - 1; put('stock', row);
    use = create('stock', {model: row.model, serial: '', safe: row.safe || 0, deleted: false});
    const rc = canPerf() ? costOf(row.id) : null; if (rc) put('costs', create('costs', {stock: use.id, buy: rc.buy || 0, sell: rc.sell || 0}));
  }
  use.qty = 1; use.status = '출고중'; use.merchant = merchant; use.contract = contract; put('stock', use); return use;
}
function addEquip(c, sel) {
  c.equip = c.equip || [];
  sel.forEach(e => {
    const x = {sid: e.sid || 0, model: e.model, serial: e.serial || ''};
    if (x.sid) { const row = byId('stock', x.sid); if (row) { const used = takeStock(row, c.merchant, c.id); if (c.installed) { used.status = '설치완료'; put('stock', used); } x.sid = used.id; } }
    c.equip.push(x);
  });
  c.qty = Math.max(1, c.equip.length); put('contracts', c);
}
ACT.contract = id => {
  const c = byId('contracts', id); if (!c) return;
  const ln = (k, v) => v ? '<div class="line"><b>' + k + '</b><div>' + esc(v) + '</div></div>' : '';
  modal('<h3>계약 정보</h3>' + ln('가맹점', c.client) + ln('구분', c.kind) + (c.amount ? ln(amountLabel(c.kind), wonFmt(c.amount)) : '') + ln('계약일', c.date) +
    (c.expire ? ln('만료일', c.expire + '  (' + dday(daysLeft(c.expire)) + ')') : '') + ln('결제 연동', ((c.linkType || '') + ' ' + (c.company || '')).trim()) + ln('담당', uname(c.who)) +
    ln('상태', c.installed ? '설치 완료 ' + (c.installedAt || '') : '설치 대기') +
    '<h2>나간 장비</h2>' + ((c.equip || []).length ? c.equip.map(e => '<div class="bul">· ' + esc(e.model) + (e.serial ? '  /  ' + esc(e.serial) : '') + '</div>').join('') : '<div class="sub">없음</div>') + ln('장비 메모', c.memo) +
    '<div class="btns" style="margin-top:12px"><button class="btn tint" data-act="contractEquip" data-arg="' + c.id + '">장비 추가</button>' + (c.installed ? '' : '<button class="btn gold" data-act="contractDone" data-arg="' + c.id + '">설치 완료</button>') + '<button class="btn" data-act="close">닫기</button></div>');
};
ACT.contractDone = id => {
  const c = byId('contracts', id); if (!c) return;
  askConfirm('설치 완료로 처리할까요?', '나간 장비가 재고에서 빠집니다.', '설치 완료', () => contractDone(c));
};
function contractDone(c) {
  c.installed = true; c.installedAt = todayStr();
  (c.equip || []).forEach(e => { const r = byId('stock', e.sid); if (r) { r.status = '설치완료'; put('stock', r); } });
  put('contracts', c); render(); toast('설치 완료로 처리했어요.');
}
ACT.contractEquip = id => {
  const c = byId('contracts', id); if (!c) return; const sel = [];
  formModal('장비 추가 — ' + c.client, EQ_HTML, () => { if (!sel.length) return warn('추가할 장비를 골라 주세요.'); addEquip(c, sel); render(); setTimeout(() => ACT.contract(id), 0); toast('장비를 추가했어요.'); return true; }, '추가');
  wireEquipPicker(sel);
};
ACT.newContract = () => {
  let mer = null; const sel = [];
  formModal('신규 계약 올리기', MP_HTML +
    '<label class="fl" for="f_lk">결제 연동</label><select id="f_lk"><option>VAN사</option><option>PG사</option></select><input id="f_co" placeholder="VAN사·PG사 업체명 (예: 다우데이타)">' +
    '<label class="fl" for="f_k">구분 (판매 / CMS / 렌탈)</label><select id="f_k"><option>판매</option><option>CMS</option><option>렌탈</option></select>' +
    '<label class="fl" for="f_a" id="f_al">판매금액 (원)</label><input id="f_a" inputmode="numeric" placeholder="숫자만 (예: 450000)">' + EQ_HTML +
    '<label class="fl" for="f_m">장비 메모 (선택)</label><textarea id="f_m" placeholder="장비 메모"></textarea>' +
    '<label class="fl" for="f_d">계약일</label><input id="f_d" type="date" value="' + todayStr() + '"><label class="fl" for="f_e">계약 만료일 (필수)</label><input id="f_e" type="date">' +
    '<div class="pills"><button type="button" class="pill" data-m="12">+12개월</button><button type="button" class="pill" data-m="24">+24개월</button><button type="button" class="pill" data-m="36">+36개월</button></div>', () => {
    const dt = $('#f_d').value, ex = $('#f_e').value;
    if (!mer) return warn('가맹점을 골라 주세요.');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dt) || !/^\d{4}-\d{2}-\d{2}$/.test(ex)) return warn('계약일과 만료일을 골라 주세요.');
    if (ex < dt) return warn('만료일이 계약일보다 빨라요. 다시 확인해 주세요.');
    const c = create('contracts', {date: dt, who: S.uid, merchant: mer.id, client: mer.name, linkType: $('#f_lk').value, company: $('#f_co').value.trim(), kind: $('#f_k').value, amount: numOf($('#f_a').value), memo: $('#f_m').value.trim(), expire: ex, installed: false, equip: [], qty: 1});
    addEquip(c, sel); render(); toast('신규 계약을 올렸어요.'); return true;
  }, '올리기');
  wireMerchantPicker(m => { mer = m; }); wireEquipPicker(sel);
  $('#f_k').onchange = () => { $('#f_al').textContent = amountLabel($('#f_k').value) + ' (원)'; };
  document.querySelectorAll('#modal .pill[data-m]').forEach(b => b.onclick = () => { $('#f_e').value = addMonths($('#f_d').value, Number(b.dataset.m)); });
};

// ---------- 입금 확인 ----------
const DEP_KINDS = ['판매대금', '미납금', '정보변경수수료', '기타'];
function postToRoom(name, text) { // 이름이 맞는 채팅방에 알림 글을 올린다 (없으면 만든다) — 앱과 같다
  let r = list('rooms').find(x => x.name === name && !(x.mem && x.mem.length));
  if (!r) { r = create('rooms', {name, by: S.uid}); put('rooms', r); }
  put('msgs', create('msgs', {room: r.id, who: S.uid, text, ts: Date.now()}));
}
function viewDeposits() {
  const ls = list('deposits').sort((a, b) => (b.t || '').localeCompare(a.t || ''));
  const wait = ls.filter(d => !d.ok);
  return '<h1>입금 확인</h1><div class="sub">입금 내역을 올리면 ‘입금확인’ 채팅방에도 자동으로 알림이 올라가요. 오른쪽 아래 + 로 올립니다.</div>' +
    (wait.length ? '<div class="sub" style="font-weight:700;margin-top:10px">확인 전 ' + wait.length + '건 · ' + wonFmt(wait.reduce((s, d) => s + (d.amount || 0), 0)) + '</div>' : '') +
    (ls.length ? ls.map(d => '<div class="card" data-act="deposit" data-arg="' + d.id + '"><div class="t1" style="color:' + (d.ok ? 'var(--green)' : 'var(--ink)') + '">' + esc(d.kind) + '  ' + wonFmt(d.amount) + (d.ok ? '  ✔ 확인됨' : '  · 확인 전') + '</div>' +
      '<div class="t2" style="color:var(--ink)">' + esc((d.mname || '(가맹점 미지정)') + (d.memo ? ' · ' + d.memo : '')) + '</div><div class="t2">' + esc((d.t || '') + ' · 올린 사람 ' + uname(d.by) + (d.ok ? ' · 확인 ' + uname(d.okby) : '')) + '</div></div>').join('') : '<div class="empty">올라온 입금 내역이 없습니다.</div>');
}
ACT.deposit = id => {
  const d = byId('deposits', id); if (!d) return;
  const ln = (k, v) => '<div class="line"><b>' + k + '</b><div>' + esc(v || '-') + '</div></div>';
  modal('<h3>입금 내역</h3>' + ln('구분', d.kind) + ln('금액', wonFmt(d.amount)) + ln('가맹점', d.mname) + ln('메모', d.memo) + ln('올린 사람', uname(d.by) + ' · ' + (d.t || '')) + (d.ok ? ln('확인', uname(d.okby)) : '') +
    '<div class="btns" style="margin-top:12px">' + (d.ok ? '' : '<button class="btn gold" data-act="depositOk" data-arg="' + d.id + '">확인 처리</button>') + '<button class="btn" data-act="close">닫기</button></div>');
};
ACT.depositOk = id => {
  const d = byId('deposits', id); if (!d || d.ok) return;
  d.ok = true; d.okby = S.uid; put('deposits', d);
  postToRoom('입금확인', '✔ 입금 확인 완료 · ' + d.kind + ' ' + wonFmt(d.amount) + (d.mname ? ' · ' + d.mname : '') + ' (' + uname(S.uid) + ')');
  closeModal(); render(); toast('확인 처리했어요.');
};
ACT.newDeposit = () => {
  let mer = null;
  formModal('입금 올리기', '<label class="fl" for="f_k">구분</label><select id="f_k">' + DEP_KINDS.map(k => '<option>' + k + '</option>').join('') + '</select>' +
    '<label class="fl" for="f_a">금액 (원)</label><input id="f_a" inputmode="numeric" placeholder="숫자만 (예: 30000)">' + MP_HTML.replace('<div class="lb">가맹점</div>', '<div class="lb">가맹점 (어느 가맹점 입금인지, 선택)</div>') +
    '<label class="fl" for="f_m">메모 (기타일 때 내용, 입금자명 등)</label><input id="f_m" placeholder="메모">', () => {
    const a = numOf($('#f_a').value); if (a <= 0) return warn('금액을 입력해 주세요.');
    const k = $('#f_k').value, memo = $('#f_m').value.trim();
    put('deposits', create('deposits', {kind: k, amount: a, memo, merchant: mer ? mer.id : 0, mname: mer ? mer.name : '', by: S.uid, t: nowStr(), ok: false}));
    postToRoom('입금확인', '💰 입금 확인 요청 · ' + k + ' ' + wonFmt(a) + (mer ? ' · ' + mer.name : '') + (memo ? ' · ' + memo : '') + ' (' + uname(S.uid) + ')');
    render(); toast('올렸어요. ‘입금확인’ 채팅방에도 알렸습니다.'); return true;
  }, '올리기');
  wireMerchantPicker(m => { mer = m; });
};


// ---------- 백업 ----------
// 1) 서버: 삭제가 막혀 있고(지우면 표시만), 모든 변경은 변경 기록(audit)에 따로 남는다.
// 2) 바깥 자동 백업: GitHub Actions 가 매일 새벽 전체 자료를 암호화해 90일 보관 (.github/workflows/backup.yml).
// 3) 관리자 기기: 안드로이드 앱(폰 안 14일치)과 웹(이 PC 안 14일치)이 하루 한 번 저절로 보관 + 언제든 파일로 내려받기.
const BK_COLLS = ['users', 'tasks', 'notices', 'projects', 'contracts', 'events', 'merchants', 'stock', 'rooms', 'msgs', 'reports', 'profiles', 'costs', 'contacts', 'deposits', 'library', 'mlogs', 'config'];
async function buildBackup() {
  const out = {kind: 'nine-family-backup', at: nowStr(), by: S.uid, from: VERSION, skipped: []};
  for (const c of BK_COLLS) {
    if (S.data[c] && c !== 'msgs') { out[c] = [...S.data[c].values()]; continue; }
    if (DEMO) { out[c] = S.data[c] ? [...S.data[c].values()] : []; continue; }
    try { const snap = await fs.collection(c).get(); out[c] = snap.docs.map(d => { try { return JSON.parse(d.get('j')); } catch (e) { return null; } }).filter(Boolean); } catch (e) { out.skipped.push(c); }
  }
  return out;
}
const bkName = () => 'ninefamily_backup_' + nowStr().replace(/[-: ]/g, '').replace(/^(\d{8})(\d{4})$/, '$1_$2') + '.json';
function saveFile(name, text) { const u = URL.createObjectURL(new Blob([text], {type: 'application/json'})); const a = document.createElement('a'); a.href = u; a.download = name; document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(u), 5000); }
ACT.backupDownload = async () => {
  if (!can('staff_manage')) return toast('관리자만 내려받을 수 있어요.');
  toast('백업 파일을 만드는 중…');
  try {
    const b = await buildBackup(); saveFile(bkName(), JSON.stringify(b, null, 1));
    toast('내려받았어요. 가맹점 ' + (b.merchants || []).length + '곳 · 업무 ' + (b.tasks || []).length + '건' + (b.skipped.length ? ' (권한 밖이라 뺀 것: ' + b.skipped.join(', ') + ')' : ''));
  } catch (e) { toast('백업 파일을 만들지 못했어요.'); }
};
// 이 PC 안(IndexedDB)에 하루 한 번 저절로 보관 — 관리자가 웹을 열면 실행, 14일치만 남긴다
function idb() { return new Promise((ok, no) => { const r = indexedDB.open('nine-backup', 1); r.onupgradeneeded = () => r.result.createObjectStore('bk'); r.onsuccess = () => ok(r.result); r.onerror = () => no(r.error); }); }
async function idbDo(mode, fn) { const d = await idb(); return new Promise((ok, no) => { const t = d.transaction('bk', mode); const st = t.objectStore('bk'); const r = fn(st); t.oncomplete = () => ok(r && r.result); t.onerror = () => no(t.error); }); }
async function autoBackup() {
  try {
    if (DEMO || !can('staff_manage') || !window.indexedDB) return;
    const n = list('merchants').length; if (!n || !list('users').length) return; // 아직 다 못 받은 빈 상태는 저장하지 않는다
    const day = todayStr().replace(/-/g, ''); const prev = Number(LS('bkcnt_' + day) || -1), last = Number(LS('bkt_' + day) || 0);
    if (prev > n || (prev === n && Date.now() - last < 3 * 3600 * 1000)) return; // 오늘 더 많은 자료로 저장했으면 덮지 않는다
    const b = await buildBackup(); await idbDo('readwrite', st => st.put(JSON.stringify(b), day));
    LS('bkcnt_' + day, String(n)); LS('bkt_' + day, String(Date.now()));
    const keys = await idbDo('readonly', st => st.getAllKeys()); const old = (keys || []).sort().slice(0, -14); if (old.length) await idbDo('readwrite', st => old.forEach(k => st.delete(k)));
  } catch (e) {}
}
ACT.pcBackups = async () => {
  if (!can('staff_manage')) return;
  let keys = []; try { keys = (await idbDo('readonly', st => st.getAllKeys())) || []; } catch (e) {}
  modal('<h3>이 PC에 보관된 자동 백업</h3><div class="sub">관리자가 웹앱을 열면 하루 한 번 이 PC 안에 저절로 보관돼요(14일치). 눌러서 파일로 내려받아요.</div>' +
    (keys.length ? keys.sort().reverse().map(k => '<button class="mi" data-act="pcBackupGet" data-arg="' + k + '"><span>' + k.replace(/^(\d{4})(\d{2})(\d{2})$/, '$1-$2-$3') + '</span><em>내려받기 ›</em></button>').join('') : '<div class="empty">아직 보관된 백업이 없어요.</div>') +
    '<button class="btn full" data-act="close">닫기</button>');
};
ACT.pcBackupGet = async k => { try { const t = await idbDo('readonly', st => st.get(k)); if (t) saveFile('ninefamily_autobackup_' + k + '.json', t); } catch (e) { toast('꺼내지 못했어요.'); } };

// ---------- 공지 ----------
function viewNotices() {
  const ns = list('notices').sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0) || (b.t || '').localeCompare(a.t || ''));
  return '<h1>공지사항</h1>' + (can('notice_write') ? '' : '<div class="sub">공지 작성은 권한이 있는 사람만 할 수 있어요.</div>') + (ns.length ? ns.map(n => '<div class="card" data-act="notice" data-arg="' + n.id + '"><div class="t1">' + (n.pinned ? '📌 ' : '') + esc(n.title) + (!(n.readBy || []).includes(S.uid) ? ' <span class="badge">새 글</span>' : '') + '</div><div class="t2">' + esc(uname(n.author) + ' · ' + (n.t || '')) + '</div></div>').join('') : '<div class="empty">공지가 없습니다.</div>');
}
ACT.notice = id => {
  const n = byId('notices', id); if (!n) return;
  if (!(n.readBy = n.readBy || []).includes(S.uid)) { n.readBy.push(S.uid); put('notices', n); render(); }
  modal('<h3>' + esc(n.title) + '</h3><div class="sub">' + esc(uname(n.author) + ' · ' + (n.t || '')) + '</div><div style="white-space:pre-wrap;margin:12px 0">' + esc(n.body) + '</div>' +
    (can('notice_write') || can('staff_manage') ? '<div class="sub">읽은 사람 ' + (n.readBy || []).length + '명: ' + esc((n.readBy || []).map(uname).join(', ')) + '</div>' : '') + '<button class="btn full" data-act="close">닫기</button>');
};
ACT.newNotice = () => formModal('공지 작성', '<label class="fl" for="f_t1">제목 (필수)</label><input id="f_t1" placeholder="제목"><label class="fl" for="f_m">내용</label><textarea id="f_m" placeholder="내용"></textarea><label class="chk"><input type="checkbox" id="f_pin"><span>맨 위에 고정</span></label>', () => {
  const t = $('#f_t1').value.trim(); if (!t) return warn('제목을 입력해 주세요.');
  put('notices', create('notices', {title: t, body: $('#f_m').value.trim(), pinned: $('#f_pin').checked, author: S.uid, t: nowStr(), readBy: [S.uid]})); render(); toast('공지를 올렸어요.'); return true;
}, '올리기');

// ---------- 전체 ----------
// 항목과 순서는 안드로이드 앱의 '전체' 탭과 같다. go 가 없는 항목은 아직 앱에서만 되는 기능이다.
function menuItems() {
  const P = can('perf_view') || can('staff_manage'), R = can('report_view'), A = can('staff_manage');
  const nu = unreadNotices(), lk = lockDoc(); const it = [];
  const add = (sec, label, words, go, badge) => it.push({sec, label, words, go, badge});
  add('바로가기', '업무', '업무 지시 할일 설치 AS 서류', 'nav:task:'); add('바로가기', '프로젝트', '프로젝트 진행률', 'nav:project:');
  add('바로가기', '채팅', '채팅 대화 메시지 방', 'nav:chat:'); add('바로가기', '일정 (달력)', '일정 달력 캘린더 방문 약속', 'nav:calendar:');
  add('일', '재고', '재고 장비 바코드 시리얼 단가 입고 출고 스캔', 'nav:all:stock');
  add('일', '가맹점', '가맹점 거래처 상호 사업자 삭제', 'nav:all:merchants');
  add('일', '가맹점 응대 기록 조회', '응대 기록 AS 전화 응대 방문 메모 이력 날짜 가맹점', 'nav:all:logs');
  add('일', '가맹점 일괄 가져오기 (엑셀·이카운트)', '가맹점 일괄 가져오기 업로드 엑셀 csv 이카운트');
  const ex = expiringContracts().length;
  add('일', '계약 관리 (설치 완료·만료 알림)', '계약 설치 완료 만료 알림 장비', 'nav:all:contracts', ex ? '만료 ' + ex : ''); add('일', '신규 계약 올리기', '신규 계약 올리기 CMS 판매 렌탈', 'act:newContract');
  add('일', '입금 확인 (입금 내역 올리기)', '입금 확인 입금 내역 미납금 판매대금 정보변경수수료', 'nav:all:deposit'); add('일', '단체 문자 보내기 (가맹점)', '단체 문자 sms 발송 가맹점 문자 안내');
  add('일', '내 실적', '내 실적 월별 성장률 계약');
  if (P) { add('일', '전체 실적·직원별 순위', '전체 실적 직원별 순위 성장률'); add('일', '수익 현황 (입고·출고 단가)', '수익 현황 입고 출고 단가 매출 매입'); add('일', '실적 보고서 올리기 (대표님께)', '실적 보고서 올리기 대표님 본부장'); }
  add('일', '일일보고 쓰기', '일일보고 보고 오늘 한 일');
  if (R) { add('일', '일일보고 종합 보기', '일일보고 종합 대표님 직원별'); if (!P) add('일', '실적 보고서 보기', '실적 보고서 보기 대표님'); }
  add('일', '지난 업무 기록', '지난 업무 기록 이력 완료 검색');
  add('회사', '자료실', '자료실 게시판 양식 서식 계약서 파일 문서'); add('회사', '비상연락망 (눌러서 전화)', '비상연락망 연락처 전화 번호');
  add('회사', '사용설명서', '사용설명서 매뉴얼 도움말 사용법 설치', 'nav:all:manual'); add('회사', '버전·업데이트 내역', '버전 업데이트 내역 변경 이력', 'nav:all:about');
  add('회사', '공지사항', '공지사항 공지 알림', 'nav:all:notice', nu ? String(nu) : '');
  if (A) {
    add('관리자', '데이터 백업 내려받기', '데이터 백업 내려받기 저장 내보내기 json', 'act:backupDownload'); add('관리자', 'PC에 보관된 자동 백업', '자동 백업 보관 PC 복원', 'act:pcBackups'); add('관리자', '가맹점 보호·복원', '가맹점 보호 복원 되돌리기 삭제 복구 휴지통');
    add('관리자', '가맹점 수정 암호', '가맹점 수정 암호 잠금 비밀번호 설정 변경', 'act:mlockSet', lk ? '설정됨' : '아직 없음');
    if (lk) add('관리자', '가맹점 수정 암호 끄기', '가맹점 수정 암호 끄기 잠금 해제', 'act:mlockOff');
    add('관리자', '직원과 권한', '직원 권한 등록 사용 중지 관리자');
  }
  add('계정', '내 프로필 (사진·상태 메시지)', '프로필 사진 상태 메시지 내 정보');
  const np = 'Notification' in window ? Notification.permission : 'none';
  add('계정', '알림 받기 (이 기기)', '알림 푸시 소리 새 업무 채팅', 'act:notifyOn', np === 'granted' ? '켜짐' : np === 'none' ? '소리만' : '꺼짐');
  add('계정', '알림 소리', '알림 소리 진동 끄기 켜기', 'act:soundToggle', soundOn() ? '켜짐' : '꺼짐');
  add('계정', '로그아웃', '로그아웃 나가기', 'act:logout');
  return it;
}
function menuRow(e) {
  const g = (e.go || '').split(':');
  const attr = g[0] === 'nav' ? 'data-nav="' + g[1] + '" data-sub="' + g[2] + '"' : g[0] === 'act' ? 'data-act="' + g[1] + '"' : 'data-act="appOnly" data-arg="' + esc(e.label) + '"';
  const badge = e.badge ? (/^\d+$/.test(e.badge) ? '<b class="badge">' + e.badge + '</b>' : '<i>' + esc(e.badge) + '</i>') : '';
  return '<button class="mi' + (e.go ? '' : ' off') + '" ' + attr + '><span>' + esc(e.label) + '</span>' + badge + (e.go ? '' : '<i class="app">앱에서</i>') + '<em>›</em></button>';
}
function viewMenu(wide) {
  const q = (S.allQ || '').trim(); const items = menuItems();
  const secs = q ? ['바로가기', '계정', '회사', '일', '관리자'] : ['계정', '회사', '일', '관리자']; // 같은 이름은 한 묶음으로 (앱과 같은 순서)
  const html = secs.map(sec => {
    const rows = items.filter(e => e.sec === sec && (e.go || q) && kmatch(e.label + ' ' + e.words, q));
    if (sec === '계정' && !q) rows.push({label: '앱으로 설치하기', go: 'act:installApp'});
    return rows.length ? '<div class="msec"><h2>' + sec + '</h2>' + rows.map(e => e.label === '앱으로 설치하기' ? '<button class="mi inst" data-act="installApp" style="display:none"><span>' + (wide ? 'PC에 설치하기' : '앱으로 설치하기') + '</span><em>›</em></button>' : menuRow(e)).join('') + '</div>' : '';
  }).join('');
  const appOnly = q ? [] : items.filter(e => !e.go);
  const appSec = appOnly.length ? '<details class="msec appsec"><summary>아직 안드로이드 앱에서만 되는 기능 (' + appOnly.length + '개)</summary>' + appOnly.map(menuRow).join('') + '</details>' : '';
  const search = '<input id="aq" data-q="allQ" placeholder="🔍  기능 검색 (예: 재고, ㅈㄱ, 문자, 수익)" value="' + esc(S.allQ || '') + '">';
  const note = (wide ? '' : appSec) + '<div class="sub" style="margin-top:18px">‘앱에서’ 표시가 있는 기능은 아직 안드로이드 앱에서만 쓸 수 있어요. 웹에도 차례로 넣고 있어요.</div><div class="sub" style="text-align:center;margin:14px 0 30px">나인패밀리 ' + VERSION + '</div>';
  if (wide) return '<div class="mhead2"><h1>전체 메뉴</h1>' + search + '</div><div class="qres mgrid" data-for="allQ">' + (html || '<div class="empty">찾는 기능이 없어요.</div>') + '</div>' + appSec + note;
  return '<h1>전체</h1><div class="card row prof" data-act="appOnly" data-arg="내 프로필"><div class="tile big">' + esc((S.me.name || '?').slice(0, 1)) + '</div><div class="grow"><div class="t1">' + esc(S.me.name) + '</div><div class="t2">' + (can('staff_manage') ? '관리자' : '직원') + '</div><div class="t2" style="font-size:11px">프로필 사진·상태 메시지는 앱에서 바꿔요</div></div></div>' +
    search + '<div class="qres" data-for="allQ">' + (html || '<div class="empty">찾는 기능이 없어요.</div>') + '</div>' + note;
}
ACT.appOnly = lb => toast('‘' + lb + '’은(는) 아직 안드로이드 앱에서만 쓸 수 있어요. 웹에도 차례로 넣고 있어요.');
ACT.back = () => { S.sub = ''; render(); };
ACT.menu = () => { S.menu = !S.menu; render(); };
ACT.home = () => { S.tab = 'task'; S.sub = ''; S.room = 0; S.menu = false; render(); };
ACT.logout = () => askConfirm('로그아웃할까요?', '', '로그아웃', logout);

// ---------- PC 홈 (오늘 현황) ----------
function viewHome() {
  const today = todayStr(); const mine = list('tasks').filter(t => t.assignee === S.uid && t.status !== '완료');
  const evs = list('events').filter(e => e.date === today).map(e => ({time: e.time || '', t: e.title, sub: e.place || ''}))
    .concat(list('tasks').filter(t => t.status !== '완료' && (t.due || '').startsWith(today) && (t.assignee === S.uid || can('task_view_all'))).map(t => ({time: (t.due || '').slice(11, 16), t: t.title, sub: '업무 · ' + uname(t.assignee), id: t.id})))
    .sort((a, b) => (a.time || '99').localeCompare(b.time || '99'));
  const d = new Date(); const mon = new Date(d.getFullYear(), d.getMonth(), d.getDate() - ((d.getDay() + 6) % 7));
  const monStr = mon.getFullYear() + '-' + pad(mon.getMonth() + 1) + '-' + pad(mon.getDate());
  const tile = (lb, n, nav) => '<button class="kpi" ' + nav + '><span>' + lb + '</span><b>' + n + '</b></button>';
  const ns = list('notices').sort((a, b) => ((a.readBy || []).includes(S.uid) ? 1 : 0) - ((b.readBy || []).includes(S.uid) ? 1 : 0) || (b.t || '').localeCompare(a.t || '')).slice(0, 4);
  return '<div class="row" style="margin-top:22px;align-items:baseline"><h1 style="margin:0">오늘 현황</h1><span class="sub">' + d.toLocaleDateString('ko-KR', {month: 'long', day: 'numeric', weekday: 'long'}) + '</span></div>' +
    '<div class="kpis">' + tile('새 업무', newTaskCount(), 'data-act="tfilter" data-arg="1"') + tile('진행 중', mine.filter(t => t.status === '진행중' || t.status === '확인함').length, 'data-act="tfilter" data-arg="2"') +
    tile('오늘 일정', evs.length, 'data-nav="calendar" data-sub=""') + tile('안 읽은 채팅', unreadTotal(), 'data-nav="chat" data-sub=""') + tile('이번 주 응대 기록', list('mlogs').filter(g => (g.date || '') >= monStr).length, 'data-nav="all" data-sub="logs"') + '</div>' +
    '<div class="homecols"><section class="panel big"><h2>업무</h2>' + viewTasks(true) + '</section><div class="side">' +
    '<section class="panel"><h2>오늘 일정</h2>' + (evs.length ? evs.map(o => '<div class="evl"' + (o.id ? ' data-act="task" data-arg="' + o.id + '"' : '') + '><b>' + esc(o.time || '종일') + '</b><span>' + esc(o.t) + (o.sub ? '<small>' + esc(o.sub) + '</small>' : '') + '</span></div>').join('') : '<div class="empty" style="padding:16px 0">오늘은 일정이 없어요.</div>') + '</section>' +
    '<section class="panel"><h2>공지</h2>' + (ns.length ? ns.map(n => '<div class="evl" data-act="notice" data-arg="' + n.id + '"><span>' + (!(n.readBy || []).includes(S.uid) ? '<i class="new">새 글</i> ' : '') + esc(n.title) + '</span></div>').join('') : '<div class="empty" style="padding:16px 0">공지가 없어요.</div>') + '</section></div></div>';
}

// ---------- 사용설명서 · 버전 내역 (웹) ----------
const WEB_CHANGES = [
  ['웹 0.0.7', '2026-10-24', ['안드로이드 앱과 맞춤: 업무 상태 변경은 담당자·최고관리자, 업무 삭제는 만든 사람·최고관리자', '서류 업무(서류 접수·카드사 서류)를 지시하면 ‘서류접수’ 채팅방에 자동으로 알림', '계약 만료 3개월 이내 계약이 있으면 업무 화면 위에 알림 카드', '일정: 다가오는 일정 목록, 일정을 누르면 내용·가맹점 정보 보기, 관리자는 일정 삭제', '가맹점 등급 코드(0~7) 입력·표시', '공지: 작성 권한자·관리자는 읽은 사람 목록', '채팅: 나인이 프렌즈 스티커 80종·이모지, 말풍선 누르면 반응·답장·복사·내 메시지 삭제, 두 번 누르면 ❤️, 글 속 웹 주소 누르면 열림', '가맹점 응대 기록: 종류·날짜로 걸러 보기']],
  ['웹 0.0.6', '2026-10-24', ['저장 상태 표시: 서버에 저장되는 동안 ‘저장 중’, 인터넷이 끊기면 ‘저장 대기 n건’ — 연결되면 자동으로 저장돼요', '가맹점 동시 수정 보호: 수정 창을 연 사이 다른 직원(앱·다른 PC)이 같은 가맹점을 고치면 바뀐 항목을 알려 주고, 덮어쓸지 한 번 더 물어요', '가맹점 등록: 같은 사업자번호가 이미 있으면 알려 줘요', '백업: 관리자 → 데이터 백업 내려받기(PC에 파일로), 관리자가 웹을 열면 하루 한 번 이 PC 안에 자동 보관(14일치)', '매일 새벽 서버 전체 자료를 암호화해 따로 보관하는 자동 백업 준비(관리자가 비밀값을 넣으면 시작)']],
  ['웹 0.0.5', '2026-10-24', ['뒤로가기: 폰 뒤로가기·브라우저 뒤로 버튼이 앱처럼 한 단계씩 닫아요(팝업 → 대화방 → 메뉴 → 하위 화면 → 첫 화면, 첫 화면에서 한 번 더 누르면 나가요)', '빨라짐: 한 번 받은 자료를 기기에 보관해서 다음부터 바로 열리고, 인터넷이 약해도 보여요', '알림: 웹앱을 열어 둔 동안 새 업무·채팅·공지가 오면 소리·진동·안내, PC는 다른 창을 보고 있어도 브라우저 알림(전체 → 알림 받기), 탭 제목에 안 본 개수', '채팅: 사진 보기·저장, 파일 받기, 사진·파일 보내기(+ 버튼, 파일 3MB까지), PC와 아이폰 웹앱끼리 읽음 표시 맞춤', '업무 지시·일정 추가: 모든 칸에 이름표, 가맹점 조회로 거래처·전화·주소 자동 채우기', '일정: 공휴일 빨간색·이름 표시, 토요일 파란색, 오늘 버튼', '전체 메뉴: 아직 앱에서만 되는 기능은 맨 아래에 접어 두기, PC 주 메뉴에 프로젝트', '확인 창을 웹앱 모양으로, 팝업 안 안내가 버튼을 가리지 않게, 채팅 보내기 버튼·말풍선 줄바꿈 고침, 권한이 없을 때 안내']],
  ['웹 0.0.4', '2026-10-24', ['재고: 모델별 재고·출고중 수량, 안전재고 부족 표시, 재고 추가(시리얼 여러 개 한 번에)·수정·삭제, 입고·출고 단가와 보유 재고 자산(실적 권한자)', '바코드: PC는 USB·블루투스 스캐너로 찍으면 바로 입력·검색, 아이폰·휴대폰은 📷 버튼으로 카메라 스캔', '계약 관리: 만료 3개월 이내 계약 따로 보기(D-day), 계약 정보·나간 장비, 장비 추가, 설치 완료(장비가 재고에서 빠짐)', '신규 계약 올리기: 가맹점 찾기(상호·초성·사업자번호), 결제 연동(VAN/PG), 판매·CMS·렌탈과 금액, 재고에서 장비 고르기·스캔·직접 입력, 만료일 +12/24/36개월', '입금 확인: 입금 올리기·확인 처리, ‘입금확인’ 채팅방에 자동 알림', '안드로이드 앱과 같은 서버 자료라 어느 쪽에서 해도 바로 서로 보여요']],
  ['웹 0.0.3', '2026-10-24', ['화면 구성: 아이폰(휴대폰)은 안드로이드 앱과 같은 화면 — 아래 탭 업무·프로젝트·채팅·일정·전체, 나머지 기능은 전체 탭 안에', 'PC: 위쪽 가로줄에 주 메뉴(업무·채팅·일정·가맹점·응대 기록), 왼쪽 위 ☰ 를 누르면 계정·회사·일·관리자로 묶인 전체 메뉴, 왼쪽 위 나인패밀리를 누르면 홈(오늘 현황)', '가맹점 검색 고침: 한글을 치는 중에도 바로 걸러지고, 상호·초성(ㅅㅇㅍ)·사업자번호(- 없이도) 모두 찾아요. 가맹점 목록·응대 기록·채팅·메뉴 검색 모두 같아요', '가맹점: 목록과 PC 표에 전화·휴대폰·VAN사·계약이 보이고, 예전에 한 칸에 넣은 휴대폰 번호는 휴대폰번호 칸으로 이어받아요', '가맹점 삭제: ‘가맹점 등록·수정’ 권한과 암호가 있어야 해요 (목록에서만 사라지고 기록은 남아요)', '전체 메뉴에 사용설명서·버전 내역 추가, 아직 앱에서만 되는 기능은 ‘앱에서’ 로 표시']],
  ['웹 0.0.2', '2026-10-24', ['가맹점 항목 추가(전화·휴대폰·이메일·VAN사·계약구분과 금액), 모든 칸에 이름표', '가맹점 등록·수정 권한과 수정 암호(관리자가 설정)', '한글 검색 입력 끊김 고침, ☰ 전체 메뉴']],
  ['웹 0.0.1', '2026-10-24', ['웹앱 시작: 업무·프로젝트·채팅·일정·가맹점·응대 기록·공지, PC·아이폰 설치']]
];
function viewAbout() {
  return '<h1>버전·업데이트 내역</h1><div class="sub">지금 버전: 나인패밀리 ' + VERSION + ' (안드로이드 앱 내역은 앱의 전체 → 버전·업데이트 내역)</div>' +
    WEB_CHANGES.map(([v, d, items]) => '<div class="card flat"><div class="t1">' + v + ' <span class="sub">' + d + '</span></div>' + items.map(x => '<div class="bul">• ' + esc(x) + '</div>').join('') + '</div>').join('');
}
const WEB_MANUAL = [
  ['처음 시작하기', '등록된 휴대폰 번호로 문자 인증을 하면 로그인돼요. 한 번 로그인하면 계속 유지돼요.\n아이폰: Safari 로 열고 공유 버튼 → ‘홈 화면에 추가’ 하면 앱처럼 써요.\nPC: 크롬·엣지 주소창 오른쪽의 설치 버튼(또는 전체 메뉴 → PC에 설치하기).'],
  ['화면 구성', '휴대폰: 안드로이드 앱과 같아요. 아래 탭 업무·프로젝트·채팅·일정·전체, 나머지 기능은 전체 탭에 있어요.\nPC: 위쪽 가로줄이 주 메뉴(업무·채팅·일정·가맹점·응대 기록)예요. 왼쪽 위 ☰ 를 누르면 계정·회사·일·관리자로 묶인 전체 메뉴가 펼쳐지고, ‘나인패밀리’를 누르면 홈(오늘 현황)으로 가요.\n안드로이드 앱·아이폰·PC 는 같은 서버를 써서 바로 서로 반영돼요.'],
  ['업무', '오른쪽 아래 + 로 업무를 지시해요. 업무를 눌러 진행 시작·보류·완료를 바꾸고, 완료할 때는 처리 결과를 한 줄 남겨요.'],
  ['프로젝트', '+ 로 만들고 체크리스트를 채우면 진행률이 자동으로 올라가요.'],
  ['채팅', '직원을 눌러 1:1 대화, ‘새 그룹 채팅방’으로 단체방을 만들어요.\n말풍선을 한 번 누르면 반응(👍 ❤️ 😂 …)·답장·복사·삭제(내 것), 두 번 빠르게 누르면 ❤️ 가 붙어요. 입력줄 ☺ 로 이모지와 나인이 프렌즈 스티커(80종)를 보내요.\n입력줄 왼쪽 + 로 사진·파일을 보내요(사진은 자동으로 줄여서, 파일은 3MB까지). 사진을 누르면 크게 보고 저장할 수 있어요.\n읽음 표시는 PC와 아이폰 웹앱끼리 맞춰져요. (안드로이드 앱과는 아직 따로예요)'],
  ['알림', '웹앱을 열어 둔 동안 새 업무·채팅·공지가 오면 소리와 안내로 알려요. PC는 전체(☰) → 알림 받기를 누르고 허용하면 다른 창을 보고 있어도 알림이 떠요. 소리는 전체 → 알림 소리로 끄고 켜요.\n웹앱을 닫았거나 화면이 꺼져 있을 때 오는 알림은 아직 안 돼요(서버 연동 후 가능).'],
  ['뒤로가기', '폰 뒤로가기나 브라우저 뒤로 버튼을 누르면 팝업 → 대화방 → 메뉴 → 하위 화면 → 첫 화면 순서로 닫혀요. 첫 화면에서 한 번 더 누르면 나가요.'],
  ['일정', '달력에서 날짜를 누르면 그날 일정과 기한이 있는 업무가 보여요. 일요일·공휴일은 빨간색(공휴일 이름도 보여요), 토요일은 파란색이에요. ‘오늘’로 이번 달에 돌아와요.\n일정 추가는 관리자만 할 수 있어요. 업무에 기한 날짜를 넣으면 달력에 같이 표시돼요.'],
  ['가맹점', '검색칸에 상호, 초성(예: ㅅㅇㅍ), 사업자번호(- 없이도), 전화번호를 치면 바로 걸러져요.\n등록·수정·삭제는 ‘가맹점 등록·수정’ 권한이 있어야 하고, 관리자가 수정 암호를 정해 두면 관리자도 암호를 넣어야 해요(한 번 넣으면 5분 동안은 다시 묻지 않아요).\n계약구분이 임대면 ‘월 CMS 금액’, 판매면 ‘판매 금액’을 적어요.'],
  ['재고', '전체 → 재고. + 로 추가할 때 시리얼을 한 줄에 하나씩 적거나 스캐너로 계속 찍으면 여러 대가 한 번에 들어가요. 시리얼이 없는 물품은 수량만 적어요.\n검색칸에 시리얼을 스캐너로 찍으면 그 장비가 바로 열려요. 휴대폰은 📷 바코드 버튼으로 카메라를 써요(처음에 카메라 권한을 허용해 주세요).\n입고·출고 단가와 재고 자산은 실적 권한이 있는 사람만 보여요.'],
  ['계약 · 입금', '신규 계약 올리기: 가맹점을 찾아 고르고, 구분(판매·CMS·렌탈)과 금액, 나가는 장비(재고에서 고르기·스캔·직접 입력), 계약일과 만료일을 넣어요. 고른 장비는 ‘출고중’이 돼요.\n계약 관리에서 계약을 눌러 장비를 더 넣거나 ‘설치 완료’를 누르면 장비가 재고에서 빠져요. 만료 3개월 이내 계약은 위에 따로 보여요. 실적 권한이 없으면 내가 올린 계약만 보여요.\n입금 확인: + 로 입금을 올리면 ‘입금확인’ 채팅방에 알림이 가고, 확인한 사람이 ‘확인 처리’를 눌러요.'],
  ['가맹점 응대 기록', '가맹점을 찾아 눌러서 요청사항·처리결과를 남기고 고쳐요. 누가 언제 고쳤는지 남아요.'],
  ['공지사항', '전체(☰) → 공지사항. 읽지 않은 공지는 숫자로 표시돼요.'],
  ['백업 (관리자)', '자료는 여러 겹으로 지켜져요.\n① 서버: 지우기가 막혀 있어 ‘삭제’해도 표시만 되고, 모든 변경은 변경 기록에 따로 남아요.\n② 매일 새벽 자동 백업: 서버 전체 자료를 암호화해 90일 보관해요(관리자가 비밀값을 넣어 두면 동작).\n③ 관리자 기기: 안드로이드 앱은 폰 안에, 웹은 이 PC 안에 하루 한 번 저절로 14일치 보관해요. 전체 → 관리자 → PC에 보관된 자동 백업.\n④ 언제든 전체 → 관리자 → 데이터 백업 내려받기로 파일을 받아 드라이브 등에 따로 두세요(한 달에 한 번 이상 권장).'],
  ['저장 상태', '저장하면 화면 위에 ‘저장 중’이 잠깐 보였다가 사라져요. 인터넷이 끊기면 ‘저장 대기’로 바뀌고, 다시 연결되면 저절로 서버에 저장돼요. 이 띠가 남아 있는 동안에는 웹앱을 닫지 마세요.\n가맹점 수정 창을 연 사이 다른 직원이 같은 가맹점을 고치면, 저장할 때 바뀐 항목을 알려 주고 덮어쓸지 한 번 더 물어요.'],
  ['아직 앱에서만 되는 기능', '단체 문자, 실적·수익, 보고, 자료실, 비상연락망, 직원과 권한, 백업, 가맹점 일괄 가져오기 등은 전체 메뉴 맨 아래 ‘아직 안드로이드 앱에서만 되는 기능’에 접혀 있어요. 웹에도 차례로 넣고 있어요.']
];
function viewManual() { return '<h1>사용설명서</h1><div class="sub">나인패밀리 ' + VERSION + ' 기준</div>' + WEB_MANUAL.map(([t, b]) => '<div class="card flat"><div class="t1">' + esc(t) + '</div><div class="mtext">' + esc(b) + '</div></div>').join(''); }

// ---------- 팝업 ----------
function modal(html) {
  closeModal();
  const d = document.createElement('div'); d.className = 'modal'; d.id = 'modal'; document.body.classList.add('hasmodal'); d.innerHTML = '<div class="sheet">' + html + '</div>';
  d.onclick = ev => { if (ev.target === d && !d.querySelector('input:not([type=checkbox]),textarea')) closeModal(); const a = ev.target.closest('[data-act]'); if (a && ACT[a.dataset.act]) { ev.stopPropagation(); ACT[a.dataset.act](a.dataset.arg, a, ev); } };
  document.body.appendChild(d);
}
function closeModal() { const m = $('#modal'); if (m) m.remove(); document.body.classList.remove('hasmodal'); }
function askConfirm(title, msg, okLabel, onOk, danger) { // 브라우저 기본 확인창 대신
  modal('<h3>' + esc(title) + '</h3>' + (msg ? '<div class="sub" style="white-space:pre-wrap;color:var(--ink);font-size:14px;margin:8px 0">' + esc(msg) + '</div>' : '') + '<div class="btns" style="margin-top:12px"><button class="btn" data-act="close">취소</button><button class="btn ' + (danger ? 'red' : 'gold') + '" id="c_ok">' + esc(okLabel) + '</button></div>');
  $('#c_ok').onclick = () => { closeModal(); onOk(); };
}
ACT.close = closeModal;
let rej = false;
function warn(s) { rej = true; toast(s); return false; }
function formModal(title, inner, onOk, okLabel) {
  modal('<h3>' + esc(title) + '</h3>' + inner + '<div class="btns" style="margin-top:12px"><button class="btn" id="m_cancel">취소</button><button class="btn gold" id="m_ok">' + esc(okLabel || '확인') + '</button></div>');
  $('#m_cancel').onclick = closeModal;
  $('#m_ok').onclick = () => { rej = false; const r = onOk(); if (r === true) closeModal(); };
  const f = $('#modal input, #modal textarea'); if (f && !f.readOnly && f.type !== 'date') f.focus();
}

// ---------- 설치(PWA) ----------
let installEv = null;
window.addEventListener('beforeinstallprompt', e => { e.preventDefault(); installEv = e; syncInstall(); });
window.addEventListener('appinstalled', () => { installEv = null; syncInstall(); toast('설치했어요. 바탕화면·시작 메뉴에서 열 수 있어요.'); });
function syncInstall() { document.querySelectorAll('.inst').forEach(b => b.style.display = installEv ? '' : 'none'); }
ACT.installApp = async () => { if (!installEv) return toast('이미 설치됐거나 이 브라우저는 주소창 메뉴에서 설치해야 해요.'); installEv.prompt(); await installEv.userChoice; installEv = null; syncInstall(); };

// ---------- 시작 ----------
function demoSeed() {
  S.uid = 1; S.phone = '+821000000000'; S.caps = {perf: true, admin: true};
  const U = (id, name, perms) => S.data.users.set(id, {id, name, phone: '+82100000000' + id, active: true, perms: perms || ['task_create']});
  U(1, '양문희', ['task_create', 'task_assign', 'task_view_all', 'notice_write', 'staff_manage']); U(2, '김영업'); U(3, '이사무');
  S.me = user(1);
  const T = (o) => S.data.tasks.set(o.id, Object.assign({creator: 1, history: [], created: '2026-10-2' + o.id % 9 + ' 10:00'}, o));
  T({id: 11, title: 'A마트 포스 설치', type: '신규 설치', client: 'A마트', phone: '062-123-4567', assignee: 1, status: '지시됨', due: '2026-10-27 14:00'});
  T({id: 12, title: '삼영푸드 단말기 AS', type: 'AS', client: '삼영푸드', assignee: 1, status: '진행중', due: '2026-10-25'});
  T({id: 13, title: '카드사 서류 접수', type: '카드사 서류', assignee: 2, status: '완료', doneAt: todayStr() + ' 09:00', doneMsg: '접수 완료'});
  S.data.projects.set(21, {id: 21, title: '10월 신규 가맹점 20곳', owner: 1, due: '2026-10-31', created: '2026-10-01 09:00', items: [{t: '리스트 정리', done: true}, {t: '방문 10곳', done: true}, {t: '계약 5건', done: false}], pct: 66});
  S.data.merchants.set(31, {id: 31, name: '삼영푸드', phone: '062-111-2222', addr: '광주 서구 상무대로 100', grade: 5});
  S.data.merchants.set(32, {id: 32, name: '행복분식', phone: '062-333-4444', addr: '광주 북구 용봉로 22', grade: 5});
  S.data.mlogs.set(41, {id: 41, merchant: 31, mname: '삼영푸드', date: todayStr(), kind: 'AS', text: '단말기 용지 걸림 해결', who: 2, created: nowStr()});
  S.data.rooms.set(51, {id: 51, name: '전체방', created: '2026-10-01 09:00'});
  S.data.msgs.set(61, {id: 61, room: 51, who: 2, text: '오늘 입금 확인 부탁드려요', ts: Date.now() - 3600000});
  S.data.msgs.set(62, {id: 62, room: 51, who: 1, text: '네 확인했습니다 👍', ts: Date.now() - 1800000});
  S.data.events.set(71, {id: 71, date: todayStr(), title: '본사 회의', time: '15:00', place: '광주지사', who: 1});
  [[91, 'Toss T2', 'T2-0001'], [92, 'Toss T2', 'T2-0002'], [93, '영수증 용지', '', 30]].forEach(([id, model, serial, qty]) => S.data.stock.set(id, {id, model, serial, qty: qty || 1, safe: serial ? 3 : 0, status: '재고', created: '2026-10-20 10:00'}));
  S.data.costs.set(95, {id: 95, stock: 91, buy: 250000, sell: 0});
  S.data.contracts.set(97, {id: 97, date: '2024-01-10', who: 1, merchant: 31, client: '삼영푸드', linkType: 'VAN사', company: 'KIS정보통신', kind: 'CMS', amount: 30000, expire: '2026-12-31', installed: true, installedAt: '2024-01-12', equip: [{sid: 0, model: 'Toss T1', serial: 'T1-9999'}], qty: 1, created: '2024-01-10 09:00'});
  S.data.deposits.set(98, {id: 98, kind: '판매대금', amount: 450000, memo: '입금자 김사장', merchant: 32, mname: '행복분식', by: 2, t: nowStr(), ok: false, created: nowStr()});
  S.data.notices.set(81, {id: 81, title: '웹앱 체험 중입니다', body: '이 화면은 연습용 가짜 자료예요. 서버에 아무것도 저장되지 않아요.', pinned: true, author: 1, t: nowStr(), readBy: []});
}
if (DEMO) { demoSeed(); render(); } else {
  firebase.initializeApp(CFG); auth = firebase.auth(); fs = firebase.firestore(); auth.languageCode = 'ko';
  fs.enablePersistence({synchronizeTabs: true}).catch(() => {}); // 한 번 받은 자료는 기기에 보관 → 다음부터 바로 열리고, 인터넷이 약해도 보인다
  loginView('phone');
  auth.onAuthStateChanged(u => { if (u && !S.uid) afterLogin(u); });
}
let wasWide = isWide();
window.addEventListener('resize', () => { if (S.me && wasWide !== isWide()) { wasWide = isWide(); later(); } });
if ('serviceWorker' in navigator && !DEMO) navigator.serviceWorker.register('sw.js').catch(() => {});
