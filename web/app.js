'use strict';
/* 나인패밀리 웹앱 — 앱과 같은 Firebase(nine-family)를 쓴다. 문서는 {j: JSON문자열, u: 시각} 형식. */
const CFG = {apiKey: 'AIzaSyCsA-M6QGGGk0owyDXzziv1X79TqF5NptI', authDomain: 'nine-family.firebaseapp.com', projectId: 'nine-family',
  storageBucket: 'nine-family.firebasestorage.app', messagingSenderId: '48232341344', appId: '1:48232341344:web:19be016c9c1c1de5a34805'};
const VERSION = '웹 0.1.3';
const DEMO = location.hash === '#demo';
const COLLS = ['users', 'tasks', 'notices', 'projects', 'events', 'merchants', 'rooms', 'msgs', 'profiles', 'mlogs'];
const TYPES = ['신규 설치', 'AS', '영업 방문', '서류 접수', '카드사 서류', '기타'];
const KINDS = ['AS', '전화 응대', '방문', '기타'];
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
function kmatch(text, q) {
  q = (q || '').trim().toLowerCase(); if (!q) return true;
  const t = String(text || '').toLowerCase();
  if (t.includes(q)) return true;
  return /^[ㄱ-ㅎ]+$/.test(q) && cho(t).includes(q);
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
  fs.collection(target).doc(String(o.id)).set(m).catch(e => toast('저장하지 못했어요: ' + (e.code || e.message)));
  if (!(priv || coll === 'msgs' || coll === 'profiles')) fs.collection('audit').add({c: coll, id: o.id, by: S.uid, u: Date.now(), j}).catch(() => {});
}
function create(coll, extra) { const o = Object.assign({id: nextId(), created: nowStr()}, extra || {}); return o; }

// ---------- 서버 읽기 ----------
let unsubs = [], renderT = 0;
function later() { clearTimeout(renderT); renderT = setTimeout(render, 200); }
function attach() {
  const srvs = COLLS.map(c => [c, c]);
  srvs.push(['rooms', 'prooms'], ['msgs', 'pmsgs']);
  srvs.forEach(([coll, srv]) => {
    const priv = srv === 'prooms' || srv === 'pmsgs';
    let q = fs.collection(srv);
    if (priv) q = q.where('mem', 'array-contains', S.phone);
    if (srv === 'msgs') q = q.orderBy('u', 'desc').limit(400);
    unsubs.push(q.onSnapshot(snap => {
      let ch = false;
      snap.docChanges().forEach(dc => {
        if (dc.type === 'removed') return;
        const j = dc.doc.get('j'); if (!j) return;
        try {
          const o = JSON.parse(j);
          if (S.pushed[coll + '/' + o.id] === j) return;
          S.pushed[coll + '/' + o.id] = j; S.data[coll].set(o.id, o); ch = true;
        } catch (e) {}
      });
      if (ch) { S.ready = true; if (S.me === null || coll === 'users') refreshMe(); later(); }
    }, err => console.warn(srv, err.code)));
  });
}
function refreshMe() { if (S.uid) { const u = user(S.uid); if (u) S.me = u; } }

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
    setTimeout(() => { if (!S.me) { $('#root').innerHTML = '<div class="login"><h1>처음 오셨나요?</h1><div class="sub">직원 정보가 아직 없어요. 앱에서 한 번 로그인한 뒤 다시 열어 주세요.</div><button class="btn full" id="lo">로그아웃</button></div>'; $('#lo').onclick = logout; } }, 8000);
  } catch (e) { toast('내 정보를 불러오지 못했어요.'); loginView('phone', '내 정보를 불러오지 못했어요. 다시 시도해 주세요.'); }
}
async function logout() {
  unsubs.forEach(u => u()); unsubs = [];
  COLLS.forEach(c => S.data[c].clear()); S.pushed = {}; S.me = null; S.uid = 0;
  try { await auth.signOut(); } catch (e) {}
  loginView('phone');
}

// ---------- 화면 틀 ----------
const NAVS = [['task', '업무'], ['project', '프로젝트'], ['chat', '채팅'], ['calendar', '일정'], ['all', '전체']];
// 넓은 화면(PC·태블릿)은 왼쪽에 메뉴를 모두 펼쳐 둔다: [키, 이름, 탭, 하위]
const NAVW = [['task', '업무', 'task', ''], ['project', '프로젝트', 'project', ''], ['chat', '채팅', 'chat', ''], ['calendar', '일정', 'calendar', ''],
  ['merchants', '가맹점', 'all', 'merchants'], ['logs', '가맹점 응대 기록', 'all', 'logs'], ['notice', '공지사항', 'all', 'notice']];
const isWide = () => window.innerWidth >= 900;
function newTaskCount() { return list('tasks').filter(t => t.assignee === S.uid && t.status === '지시됨').length; }
function render() {
  if (S.comp) { later(); return; } // 글자 조합 중에는 화면을 갈아엎지 않는다
  if (!S.me) { refreshMe(); if (!S.me) return; }
  if (S.me.active === false) { toast('사용이 중지된 계정입니다.'); return logout(); }
  const root = $('#root');
  const keepScroll = $('.scroll') ? $('.scroll').scrollTop : 0;
  const act = document.activeElement; const focusId = act && act.id; const selPos = act && act.selectionStart; const focusVal = act && act.value;
  const chatBadge = unreadTotal();
  const badges = {task: newTaskCount(), all: unreadNotices(), chat: chatBadge};
  const k = S.sub || S.tab;
  let body = '';
  if (S.tab === 'chat' && !S.sub) body = viewChat();
  else body = '<div class="scroll"><div class="col">' + viewBody(k) + '</div></div>';
  const nb = isWide()
    ? '<div class="nav w"><div class="logo"><img src="icon-192.png" alt="">나인패밀리</div>' +
      NAVW.map(([key, lb, tab, sub]) => { const n = key === 'notice' ? badges.all : badges[key]; return '<button data-nav="' + tab + '" data-sub="' + sub + '" class="' + (k === key ? 'on' : '') + '"><span class="t">' + lb + '</span>' + (n ? '<span class="badge">' + n + '</span>' : '') + '</button>'; }).join('') +
      '<div class="grow"></div><div class="me">' + esc(S.me.name) + '<br><span>' + (can('staff_manage') ? '관리자' : '직원') + '</span></div><button data-act="installApp" class="inst" style="display:none">PC에 설치하기</button><button data-act="logout">로그아웃</button></div>'
    : '<div class="nav">' + NAVS.map(([key, lb]) => '<button data-nav="' + key + '" class="' + (S.tab === key ? 'on' : '') + '"><span class="t">' + lb + '</span>' + (badges[key] ? '<span class="badge">' + badges[key] + '</span>' : '') + '</button>').join('') + '</div>';
  root.innerHTML = '<div class="app"><div class="main">' + body + '</div>' + nb + '</div>' + fab();
  syncInstall();
  bind(root);
  const sc = $('.scroll'); if (sc) sc.scrollTop = keepScroll;
  const ms = $('#msgbox'); if (ms) ms.scrollTop = ms.scrollHeight;
  if (focusId && $('#' + focusId) && $('#' + focusId).closest('#root')) { const el = $('#' + focusId); if (focusVal != null && el.tagName !== 'SELECT') el.value = focusVal; el.focus(); try { el.setSelectionRange(selPos, selPos); } catch (e) {} }
}
function viewBody(k) {
  switch (k) {
    case 'task': return viewTasks();
    case 'project': return viewProjects();
    case 'calendar': return viewCalendar();
    case 'merchants': return viewMerchants();
    case 'notice': return viewNotices();
    case 'logs': return viewAllLogs();
    default: return viewAll();
  }
}
function fab() {
  const k = S.sub || S.tab;
  const map = {task: 'newTask', project: 'newProject', merchants: 'newMerchant', calendar: can('staff_manage') ? 'newEvent' : '', notice: can('notice_write') ? 'newNotice' : ''};
  return map[k] ? '<button class="fab" data-act="' + map[k] + '">+</button>' : '';
}
function bind(root) {
  root.onclick = ev => {
    const n = ev.target.closest('[data-nav]'); if (n) { S.tab = n.dataset.nav; S.sub = n.dataset.sub || ''; S.room = 0; return render(); }
    const a = ev.target.closest('[data-act]'); if (a) { ACT[a.dataset.act] && ACT[a.dataset.act](a.dataset.arg, a, ev); }
  };
  // 한글은 글자를 조합하는 동안 입력칸을 다시 그리면 'ㅌㅗㅅㅡ' 처럼 쪼개진다 → 조합이 끝난 뒤에만 다시 그린다
  root.oninput = ev => {
    const t = ev.target;
    if (ev.isComposing || S.comp) return;
    if (t.dataset.q) { S[t.dataset.q] = t.value; if (t.dataset.q === 'mq') S.mlimit = 50; render(); }
  };
  root.oncompositionstart = () => { S.comp = true; };
  root.oncompositionend = ev => {
    S.comp = false; const t = ev.target;
    if (t.dataset && t.dataset.q) { S[t.dataset.q] = t.value; if (t.dataset.q === 'mq') S.mlimit = 50; render(); }
  };
}
const ACT = {};

// ---------- 업무 ----------
function stColor(s) { return {'지시됨': ['#fff1db', '#8a5200'], '진행중': ['#f7efd9', '#8f6b14'], '보류': ['#ffe9e5', '#b3261e'], '완료': ['#e3f6ee', '#0b7a55']}[s] || ['#eef0f4', '#4a4f5e']; }
function viewTasks() {
  const all = list('tasks'); const today = todayStr();
  let open = 0, done = 0, fresh = 0, doing = 0, held = 0;
  all.forEach(t => { if (t.assignee !== S.uid) return; if (t.status === '완료') { if ((t.doneAt || '').startsWith(today)) done++; } else { open++; if (t.status === '지시됨') fresh++; if (t.status === '진행중' || t.status === '확인함') doing++; if (t.status === '보류') held++; } });
  const ratio = open + done ? Math.round(100 * done / (open + done)) : 0;
  const scope = all.filter(t => (S.showAll && can('task_view_all')) || t.assignee === S.uid);
  const F = ['미완료', '미확인', '진행중', '보류', '완료'], FS = ['', '지시됨', '진행중', '보류', '완료'];
  const norm = t => t.status === '확인함' ? '진행중' : t.status;
  const cnt = F.map((f, i) => scope.filter(t => i === 0 ? norm(t) !== '완료' : norm(t) === FS[i]).length);
  const rows = scope.filter(t => S.taskFilter === 0 ? norm(t) !== '완료' : norm(t) === FS[S.taskFilter]).sort((a, b) => (b.created || '').localeCompare(a.created || ''));
  return '<div class="hero"><div class="row"><div class="grow">남은 업무<div class="n">' + open + '건</div></div><div style="text-align:right;font-size:12px">미확인 ' + fresh + ' · 진행 ' + doing + ' · 보류 ' + held + '<br>오늘 완료 ' + done + '</div></div><div class="bar"><i style="width:' + ratio + '%"></i></div></div>' +
    (can('task_view_all') ? '<div class="pills"><button class="pill ' + (!S.showAll ? 'on' : '') + '" data-act="scope" data-arg="0">내 업무</button><button class="pill ' + (S.showAll ? 'on' : '') + '" data-act="scope" data-arg="1">전체 업무</button></div>' : '') +
    '<div class="sub" style="margin-top:14px;font-weight:700">상태별로 보기</div><div class="seg">' + F.map((f, i) => '<button class="' + (S.taskFilter === i ? 'on' : '') + '" data-act="tfilter" data-arg="' + i + '">' + f + '<b>' + cnt[i] + '</b></button>').join('') + '</div>' +
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
  const own = t.assignee === S.uid || can('task_assign') || can('task_view_all');
  const ln = (k, v) => v ? '<div class="line"><b>' + k + '</b><div>' + esc(v) + '</div></div>' : '';
  const ph = (t.phone || '').replace(/[^0-9+*#]/g, '');
  modal('<h3>' + esc(t.title) + '</h3><div class="sub" style="font-weight:700">' + esc(t.type) + ' · ' + (ST[t.status] || t.status) + '</div>' +
    ln('거래처', t.client) + (ph ? '<div class="line"><b>전화</b><a href="tel:' + ph + '">' + esc(t.phone) + '</a></div>' : '') + ln('주소', t.addr) + ln('기한', t.due) + ln('담당', uname(t.assignee)) + ln('지시한 사람', uname(t.creator)) + ln('열어본 시각', t.seenAt) + ln('메모', t.memo) + ln('완료 메시지', t.doneMsg) +
    '<h2>처리 이력</h2>' + (t.history || []).map(h => '<div class="hist">' + esc(h.t + '  ' + uname(h.who) + '\n' + h.text) + '</div>').join('') +
    (own && t.status !== '완료' ? '<div class="btns" style="margin-top:12px">' + (t.status !== '진행중' && t.status !== '확인함' ? '<button class="btn tint" data-act="tstatus" data-arg="' + id + '|진행중">진행 시작</button>' : '') + '<button class="btn" data-act="thold" data-arg="' + id + '">보류·연기</button><button class="btn gold" data-act="tdone" data-arg="' + id + '">완료</button></div>' : '') +
    (own && t.status === '완료' ? '<button class="btn full" data-act="tstatus" data-arg="' + id + '|진행중">진행중으로 되돌리기</button>' : '') +
    (own && t.status === '보류' ? '<button class="btn tint full" data-act="tresched" data-arg="' + id + '">일정 다시 잡기</button>' : '') +
    '<button class="btn full" data-act="close">닫기</button>');
};
ACT.tstatus = a => {
  const [id, st] = a.split('|'); const t = byId('tasks', id); if (!t || t.status === st) return;
  t.status = st; tlog(t, '상태 → ' + st); put('tasks', t); closeModal(); render(); toast('바꿨어요.');
};
ACT.thold = id => formModal('보류·연기', '<div class="sub">다시 할 날짜를 정하면 일정 달력도 같이 바뀌어요. (정하지 않아도 돼요)</div><input type="date" id="f_d"><input type="time" id="f_t">', () => {
  const t = byId('tasks', id); const d = $('#f_d').value, tm = $('#f_t').value;
  t.status = '보류'; tlog(t, '상태 → 보류');
  if (d) { const nd = d + (tm ? ' ' + tm : ''); tlog(t, '일정 변경 ' + (t.due || '없음') + ' → ' + nd); t.due = nd; }
  put('tasks', t); render(); toast('보류로 바꿨어요.'); return true;
}, '보류하기');
ACT.tresched = id => formModal('일정 다시 잡기', '<input type="date" id="f_d"><input type="time" id="f_t">', () => {
  const d = $('#f_d').value, tm = $('#f_t').value; if (!d) return warn('날짜를 골라 주세요.');
  const t = byId('tasks', id); const nd = d + (tm ? ' ' + tm : ''); tlog(t, '일정 변경 ' + (t.due || '없음') + ' → ' + nd); t.due = nd; put('tasks', t); render(); toast('일정을 바꿨어요.'); return true;
}, '변경');
ACT.tdone = id => formModal('완료 처리', '<div class="sub">처리 결과를 한 줄 남겨 주세요.</div><textarea id="f_m" placeholder="완료 메시지 (필수)"></textarea>', () => {
  const m = $('#f_m').value.trim(); if (!m) return warn('완료 메시지를 입력해 주세요.');
  const t = byId('tasks', id); t.status = '완료'; t.doneMsg = m; t.doneAt = nowStr(); tlog(t, '상태 → 완료 : ' + m); put('tasks', t); render(); toast('완료 처리했어요.'); return true;
}, '완료 처리');
ACT.newTask = () => {
  const staff = list('users').filter(u => u.active !== false && (can('task_assign') || u.id === S.uid));
  formModal('업무 지시', '<input id="f_t1" placeholder="업무 제목 (필수)"><select id="f_ty">' + TYPES.map(x => '<option>' + x + '</option>').join('') + '</select><input id="f_c" placeholder="거래처(상호)"><input id="f_p" type="tel" placeholder="매장 전화"><input id="f_a" placeholder="매장 주소"><input id="f_d" type="date"><input id="f_tm" type="time"><textarea id="f_m" placeholder="메모"></textarea><div class="sub" style="margin-top:8px">담당 직원</div><select id="f_w">' + staff.map(u => '<option value="' + u.id + '"' + (u.id === S.uid ? ' selected' : '') + '>' + esc(u.name) + '</option>').join('') + '</select>', () => {
    const ti = $('#f_t1').value.trim(); if (!ti) return warn('업무 제목을 입력해 주세요.');
    const d = $('#f_d').value, tm = $('#f_tm').value; const as = Number($('#f_w').value);
    const t = create('tasks', {title: ti, type: $('#f_ty').value, client: $('#f_c').value.trim(), phone: $('#f_p').value.trim(), addr: $('#f_a').value.trim(), due: d ? d + (tm ? ' ' + tm : '') : '', memo: $('#f_m').value.trim(), assignee: as, creator: S.uid, status: '지시됨', history: []});
    tlog(t, '업무 지시 → ' + uname(as)); put('tasks', t);
    if (d) { const p = d.split('-'); S.calY = +p[0]; S.calM = +p[1]; S.calSel = d; }
    render(); toast(uname(as) + '님에게 업무를 지시했습니다.'); return true;
  }, '지시하기');
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
  formModal('프로젝트 만들기', '<input id="f_t1" placeholder="프로젝트 이름 (필수)"><input id="f_d" type="date"><textarea id="f_m" placeholder="메모"></textarea><textarea id="f_i" placeholder="체크리스트 (한 줄에 항목 하나, 선택)"></textarea><div class="sub" style="margin-top:8px">담당자</div><select id="f_w">' + staff.map(u => '<option value="' + u.id + '"' + (u.id === S.uid ? ' selected' : '') + '>' + esc(u.name) + '</option>').join('') + '</select>', () => {
    const ti = $('#f_t1').value.trim(); if (!ti) return warn('프로젝트 이름을 입력해 주세요.');
    const items = $('#f_i').value.split('\n').map(s => s.trim()).filter(Boolean).map(t => ({t, done: false}));
    const p = create('projects', {title: ti, due: $('#f_d').value, memo: $('#f_m').value.trim(), owner: Number($('#f_w').value), pct: 0, check: false, notes: []});
    if (items.length) p.items = items; put('projects', p); render(); toast('프로젝트를 만들었어요.'); return true;
  }, '만들기');
};

// ---------- 일정 ----------
function viewCalendar() {
  const now = new Date();
  if (!S.calY) { S.calY = now.getFullYear(); S.calM = now.getMonth() + 1; S.calSel = todayStr(); }
  const ev = {};
  const add = (d, o) => { if (d) (ev[d] = ev[d] || []).push(o); };
  list('events').forEach(e => add(e.date, {kind: 'e', t: e.title, time: e.time, sub: e.place, who: e.who}));
  list('tasks').forEach(t => { if (t.due && t.status !== '완료' && (S.showAll || t.assignee === S.uid || can('task_view_all'))) add(t.due.slice(0, 10), {kind: 't', t: t.title, time: t.due.length > 10 ? t.due.slice(11, 16) : '', sub: uname(t.assignee) + ' · ' + (ST[t.status] || t.status), id: t.id}); });
  const first = new Date(S.calY, S.calM - 1, 1).getDay(); const days = new Date(S.calY, S.calM, 0).getDate();
  let cells = ''; for (let i = 0; i < first; i++) cells += '<td></td>';
  for (let d = 1; d <= days; d++) {
    const k = S.calY + '-' + pad(S.calM) + '-' + pad(d); const dow = (first + d - 1) % 7;
    cells += '<td class="' + (k === S.calSel ? 'sel ' : '') + (k === todayStr() ? 'today ' : '') + (dow === 0 ? 'sun' : '') + '" data-act="calsel" data-arg="' + k + '"><div>' + d + (ev[k] ? '<i></i>' : '') + '</div></td>';
    if (dow === 6 && d < days) cells += '</tr><tr>';
  }
  const sel = (ev[S.calSel] || []).sort((a, b) => (a.time || '').localeCompare(b.time || ''));
  return '<div class="row" style="margin-top:18px"><button class="btn" data-act="calmove" data-arg="-1" style="margin:0">‹</button><div class="grow t1" style="text-align:center;font-size:19px">' + S.calY + '년 ' + S.calM + '월</div><button class="btn" data-act="calmove" data-arg="1" style="margin:0">›</button></div>' +
    '<table class="cal"><tr>' + ['일', '월', '화', '수', '목', '금', '토'].map(x => '<th>' + x + '</th>').join('') + '</tr><tr>' + cells + '</tr></table>' +
    '<h2>' + esc(S.calSel) + '</h2>' + (sel.length ? sel.map(o => '<div class="card ' + (o.kind === 'e' ? 'flat' : '') + '" ' + (o.kind === 't' ? 'data-act="task" data-arg="' + o.id + '"' : '') + '><div class="row"><div class="tile">' + (o.kind === 't' ? '업무' : '일정') + '</div><div class="grow"><div class="t1">' + esc(o.t) + '</div><div class="t2">' + esc((o.time ? o.time + ' · ' : '') + (o.sub || '')) + '</div></div></div></div>').join('') : '<div class="empty">이 날은 일정이 없어요.</div>');
}
ACT.calsel = d => { S.calSel = d; render(); };
ACT.calmove = a => { S.calM += Number(a); if (S.calM < 1) { S.calM = 12; S.calY--; } if (S.calM > 12) { S.calM = 1; S.calY++; } render(); };
ACT.newEvent = () => formModal('일정 추가', '<input id="f_t1" placeholder="일정 제목 (필수)"><input id="f_d" type="date" value="' + (S.calSel || todayStr()) + '"><input id="f_tm" type="time"><input id="f_p" placeholder="장소·주소">', () => {
  const ti = $('#f_t1').value.trim(), d = $('#f_d').value; if (!ti || !d) return warn('제목과 날짜를 확인해 주세요.');
  put('events', create('events', {date: d, title: ti, who: S.uid, time: $('#f_tm').value, place: $('#f_p').value.trim()})); S.calSel = d; render(); toast('일정을 남겼어요.'); return true;
}, '추가');

// ---------- 채팅 ----------
const LS = (k, v) => { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) {} return null; };
const readAt = r => { const v = LS('rd_' + S.uid + '_' + r); if (v) return Number(v); let i = LS('rdinit_' + S.uid); if (!i) { i = Date.now(); LS('rdinit_' + S.uid, String(i)); } return Number(i); };
function markRead(r) { if (!r) return; let mx = readAt(r); list('msgs').forEach(m => { if (m.room === r && m.ts > mx) mx = m.ts; }); LS('rd_' + S.uid + '_' + r, String(mx)); }
function unreadMap() { const o = {}, since = {}; list('rooms').forEach(r => since[r.id] = readAt(r.id)); list('msgs').forEach(m => { if (since[m.room] != null && m.ts > since[m.room] && m.who !== S.uid) o[m.room] = (o[m.room] || 0) + 1; }); return o; }
function unreadTotal() { if (!S.me) return 0; return Object.values(unreadMap()).reduce((a, b) => a + b, 0); }
function unreadNotices() { return list('notices').filter(n => !(n.readBy || []).includes(S.uid)).length; }
function dmOther(r) { return (r.members || []).find(x => x !== S.uid) || 0; }
function roomTitle(r) { return r.dm ? (dmOther(r) ? uname(dmOther(r)) : '나와의 대화') : (r.name || ''); }
function lastMsg(rid) { let b = null; list('msgs').forEach(m => { if (m.room === rid && (!b || m.ts > b.ts)) b = m; }); return b; }
function msgText(m) { if (m.kind === 'img') return '📷 사진'; if (m.kind === 'stk') return '스티커'; if (m.kind === 'file') return '📎 ' + (m.fname || '파일'); return m.text || ''; }
function viewChat() {
  const rooms = list('rooms').map(r => ({r, last: lastMsg(r.id)})).filter(x => x.last || x.r.id === S.room).sort((a, b) => (b.last ? b.last.ts : 0) - (a.last ? a.last.ts : 0));
  const un = unreadMap();
  const staff = list('users').filter(u => u.id !== S.uid && u.active !== false).sort((a, b) => a.name.localeCompare(b.name));
  const lst = '<div class="chatlist"><h1>채팅</h1><button class="btn gold full" data-act="newGroup">+ 새 그룹 채팅방</button>' +
    '<div class="sub" style="margin-top:12px;font-weight:700">직원 총 ' + (staff.length + 1) + '명 · 눌러서 1:1 대화</div><div class="avs">' + staff.map(u => '<div class="av" data-act="dm" data-arg="' + u.id + '"><b>' + esc(u.name.slice(0, 1)) + '</b>' + esc(u.name) + '</div>').join('') + '</div>' +
    '<input id="chq" data-q="chatQ" placeholder="🔍 대화방 검색 (초성 가능)" value="' + esc(S.chatQ) + '">' +
    rooms.filter(x => kmatch(roomTitle(x.r), S.chatQ)).map(x => {
      const l = x.last; const c = un[x.r.id] || 0;
      return '<div class="card" data-act="room" data-arg="' + x.r.id + '"><div class="row"><div class="tile">' + esc((roomTitle(x.r) || '?').slice(0, 1)) + '</div><div class="grow"><div class="t1">' + esc(roomTitle(x.r)) + '</div><div class="t2">' + esc(l ? (l.who === S.uid ? '나' : uname(l.who)) + ': ' + msgText(l) : '아직 대화가 없어요') + '</div></div><div style="text-align:right;font-size:11px;color:var(--gray)">' + (l ? new Date(l.ts).toLocaleString('ko-KR', {month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false}) : '') + (c ? '<br><span class="badge">' + c + '</span>' : '') + '</div></div></div>';
    }).join('') + (rooms.length ? '' : '<div class="empty">아직 채팅방이 없어요.</div>') + '<div style="height:90px"></div></div>';
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
    html += '<div class="m ' + (me ? 'me' : '') + '"><div>' + (me || room.dm ? '' : '<div class="who">' + esc(uname(m.who)) + '</div>') + '<div class="bub">' + (m.rto ? '<div class="rto">' + esc((uname(m.rto.who)) + ': ' + (m.rto.text || '')) + '</div>' : '') + esc(msgText(m)) + '</div><div class="who" style="' + (me ? 'text-align:right' : '') + '">' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + '</div></div></div>';
  });
  const roomView = '<div class="chatroom"><div class="hdr"><button data-act="leave">‹</button><div class="grow t1">' + esc(roomTitle(room)) + (room.grp ? ' <span class="sub">(' + (room.members || []).length + '명)</span>' : '') + '</div></div><div class="msgs" id="msgbox">' + (html || '<div class="empty">첫 메시지를 남겨 보세요.</div>') + '</div><div class="send"><input id="msgin" placeholder="메시지 입력" autocomplete="off"><button data-act="send">보내기</button></div></div>';
  return wide ? '<div class="chatwrap">' + lst + roomView + '</div>' : roomView;
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
  let room = byId('rooms', S.room);
  if (!room && S.dmDraft && S.dmDraft.id === S.room) { room = S.dmDraft; put('rooms', room); S.dmDraft = null; }
  if (!room) return;
  const m = create('msgs', {room: room.id, who: S.uid, text, ts: Date.now()});
  if (room.mem && room.mem.length) m.mem = room.mem.slice();
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
function viewMerchants() {
  const all = list('merchants').sort((a, b) => (a.name || '').localeCompare(b.name || ''));
  const hits = all.filter(m => kmatch((m.name || '') + ' ' + (m.bizno || '') + ' ' + (m.phone || '') + ' ' + (m.addr || ''), S.mq));
  const shown = hits.slice(0, S.mlimit);
  return '<h1>가맹점</h1><div class="sub">눌러서 보고, 오른쪽 아래 + 로 새로 등록해요.</div><input id="mq" data-q="mq" placeholder="상호·번호·주소 검색 (초성도 돼요)" value="' + esc(S.mq) + '">' +
    '<div class="sub" style="margin-top:8px;font-weight:700">' + (S.mq.trim() ? '검색 결과 ' : '전체 ') + hits.length + '곳</div>' +
    shown.map(m => '<div class="card" data-act="merchant" data-arg="' + m.id + '"><div class="t1">' + esc(m.name) + '</div><div class="t2">' + esc((m.phone ? m.phone + ' · ' : '') + (m.addr || '')) + '</div></div>').join('') +
    (hits.length > shown.length ? '<button class="btn full" data-act="mmore">더 보기 (' + (hits.length - shown.length) + '곳 남음)</button>' : '') +
    (!hits.length ? '<div class="empty">' + (S.mq.trim() ? '찾는 가맹점이 없어요.' : '등록된 가맹점이 없습니다.') + '</div>' : '');
}
ACT.mmore = () => { S.mlimit += 100; render(); };
ACT.merchant = id => {
  const m = byId('merchants', id); if (!m) return;
  if (S.logMid !== m.id) { S.logMid = m.id; S.logLimit = 30; }
  const logs = list('mlogs').filter(g => g.merchant === m.id).sort((a, b) => (b.date || '').localeCompare(a.date || '') || (b.created || '').localeCompare(a.created || ''));
  const ln = (k, v) => v ? '<div class="line"><b>' + k + '</b><div>' + esc(v) + '</div></div>' : '';
  const ph = (m.phone || '').replace(/[^0-9+*#]/g, '');
  modal('<h3>' + esc(m.name) + '</h3>' + ln('사업자번호', m.bizno) + ln('주소', m.addr) + ln('계약조건', m.terms) + ln('메모', m.memo) +
    (ph ? '<a class="btn tint full" style="display:block;text-align:center;text-decoration:none" href="tel:' + ph + '">📞 ' + esc(m.phone) + ' 전화 걸기</a>' : '') +
    '<h2>📝 응대 기록 (' + logs.length + '건)</h2><button class="btn gold full" data-act="newLog" data-arg="' + m.id + '">+ 응대 기록 남기기</button>' +
    logs.slice(0, S.logLimit).map(g => logCard(g, 'editLog|' + g.id + '|' + m.id)).join('') + (logs.length > S.logLimit ? '<button class="btn full" data-act="logmore" data-arg="' + m.id + '">더 보기 (' + (logs.length - S.logLimit) + '건 남음)</button>' : '') +
    '<div class="btns" style="margin-top:10px"><button class="btn" data-act="editMerchant" data-arg="' + m.id + '">정보 수정</button><button class="btn" data-act="close">닫기</button></div>');
};
function logCard(g, act) {
  const [a, id, mid] = act.split('|');
  return '<div class="card" data-act="' + a + '" data-arg="' + id + '|' + mid + '"><span class="chip">' + esc(g.kind) + '</span> <b style="font-size:13px">' + esc(g.date) + '</b><div style="margin-top:6px;white-space:pre-wrap">' + esc(g.text) + '</div><div class="t2">' + esc(uname(g.who) + (g.editedBy ? ' · 수정 ' + uname(g.editedBy) + ' ' + (g.editedAt || '') : '')) + '</div></div>';
}
ACT.logmore = mid => { S.logLimit += 50; ACT.merchant(mid); };
ACT.editLog = a => {
  const [id, mid] = a.split('|'); const g = byId('mlogs', id); if (!g) return;
  const mine = g.who === S.uid || can('staff_manage');
  formModal('응대 기록 수정', '<div class="sub">' + esc(g.mname) + ' · 작성 ' + esc(uname(g.who)) + (g.editedBy ? ' · 마지막 수정 ' + esc(uname(g.editedBy)) + ' ' + esc(g.editedAt || '') : '') + '</div><input id="f_d" type="date" value="' + esc(g.date) + '"><select id="f_k">' + KINDS.map(k => '<option' + (k === g.kind ? ' selected' : '') + '>' + k + '</option>').join('') + '</select><textarea id="f_m">' + esc(g.text) + '</textarea>' + (mine ? '<button class="btn red full" id="f_del">이 기록 지우기</button>' : ''), () => {
    const d = $('#f_d').value, t = $('#f_m').value.trim(); if (!d || !t) return warn('날짜와 내용을 확인해 주세요.');
    if (t !== g.text) { g.editedBy = S.uid; g.editedAt = nowStr(); }
    g.date = d; g.kind = $('#f_k').value; g.text = t; put('mlogs', g); render(); if (mid && byId('merchants', mid)) ACT.merchant(mid); toast('고쳤어요.'); return true;
  }, '저장');
  const del = $('#f_del'); if (del) del.onclick = () => { if (!confirm('이 기록을 지울까요?')) return; g.deleted = true; put('mlogs', g); closeModal(); render(); if (mid && byId('merchants', mid)) ACT.merchant(mid); };
};
ACT.newLog = mid => formModal('응대 기록', '<input id="f_d" type="date" value="' + todayStr() + '"><select id="f_k">' + KINDS.map(k => '<option>' + k + '</option>').join('') + '</select><textarea id="f_m" placeholder="내용 (필수)"></textarea>', () => {
  const d = $('#f_d').value, t = $('#f_m').value.trim(); if (!d || !t) return warn('날짜와 내용을 확인해 주세요.');
  const m = byId('merchants', mid); put('mlogs', create('mlogs', {merchant: m.id, mname: m.name, date: d, kind: $('#f_k').value, text: t, who: S.uid})); render(); ACT.merchant(mid); toast('기록을 남겼어요.'); return true;
}, '저장');
const mfield = (id, ph, v, t) => '<input id="' + id + '" placeholder="' + ph + '" value="' + esc(v || '') + '"' + (t ? ' type="' + t + '"' : '') + '>';
function merchantForm(m) {
  return mfield('f_n', '상호 (필수)', m && m.name) + mfield('f_b', '사업자번호 (예: 123-45-67890)', m && m.bizno) + mfield('f_p', '연락처', m && m.phone, 'tel') + mfield('f_a', '주소', m && m.addr) + mfield('f_t', '계약조건 (예: 렌탈 36개월, 월 3만원)', m && m.terms) + '<textarea id="f_m" placeholder="메모">' + esc((m && m.memo) || '') + '</textarea>';
}
function readMerchant(o) { o.name = $('#f_n').value.trim(); o.bizno = $('#f_b').value.trim(); o.phone = $('#f_p').value.trim(); o.addr = $('#f_a').value.trim(); o.terms = $('#f_t').value.trim(); o.memo = $('#f_m').value.trim(); return o; }
ACT.newMerchant = () => formModal('가맹점 등록', merchantForm(null), () => {
  if (!$('#f_n').value.trim()) return warn('상호를 입력해 주세요.');
  const o = readMerchant(create('merchants', {grade: 5})); put('merchants', o); render(); toast('저장했어요.'); return true;
}, '저장');
ACT.editMerchant = id => formModal('가맹점 정보 수정', merchantForm(byId('merchants', id)), () => {
  if (!$('#f_n').value.trim()) return warn('상호를 입력해 주세요.');
  const o = readMerchant(byId('merchants', id)); put('merchants', o); render(); toast('저장했어요.'); return true;
}, '저장');
function viewAllLogs() {
  const mq = (S.lmq || '').trim(); const q = S.lq || '';
  let found = '';
  if (mq) {
    const hits = list('merchants').filter(m => { const b = m.bizno || ''; return kmatch((m.name || '') + ' ' + b + ' ' + b.replace(/-/g, ''), mq); }).sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    found = hits.slice(0, 20).map(m => { const c = list('mlogs').filter(g => g.merchant === m.id).length; return '<div class="card" data-act="merchant" data-arg="' + m.id + '"><div class="row"><div class="grow"><div class="t1">' + esc(m.name) + '</div><div class="t2">' + esc((m.bizno || '사업자번호 없음') + ' · 응대 기록 ' + c + '건') + '</div></div><span style="color:var(--deep);font-size:22px">›</span></div></div>'; }).join('') +
      (hits.length > 20 ? '<div class="sub">더 있어요. 검색어를 더 적어 주세요.</div>' : '') + (hits.length ? '' : '<div class="empty">찾는 가맹점이 없어요. (상호·사업자번호만 찾아요)</div>');
  }
  const ls = list('mlogs').filter(g => kmatch((g.mname || '') + ' ' + (g.text || ''), q)).sort((a, b) => (b.date || '').localeCompare(a.date || '') || (b.created || '').localeCompare(a.created || ''));
  return '<h1>가맹점 응대 기록</h1><div class="sub">상호 또는 사업자번호로 가맹점을 찾아 눌러서 기록을 보고·추가·수정해요.</div><input id="lmq" data-q="lmq" placeholder="🔍 가맹점 찾기 — 상호 또는 사업자번호" value="' + esc(S.lmq) + '">' + found +
    '<h2>최근 기록</h2><input id="lq" data-q="lq" placeholder="가맹점·내용 검색 (초성 가능)" value="' + esc(q) + '"><div class="sub" style="margin-top:8px;font-weight:700">기록 ' + ls.length + '건 · 눌러서 수정</div>' +
    ls.slice(0, 100).map(g => logCard(g, 'editLog|' + g.id + '|' + g.merchant).replace('<span class="chip">' + esc(g.kind) + '</span>', '<span class="chip">' + esc(g.kind) + '</span> <b>' + esc(g.mname) + '</b>')).join('') + (ls.length > 100 ? '<div class="sub">최근 100건만 보여요.</div>' : '') + (ls.length ? '' : '<div class="empty">기록이 없어요.</div>');
}

// ---------- 공지 ----------
function viewNotices() {
  const ns = list('notices').sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0) || (b.t || '').localeCompare(a.t || ''));
  return '<h1>공지사항</h1>' + (ns.length ? ns.map(n => '<div class="card" data-act="notice" data-arg="' + n.id + '"><div class="t1">' + (n.pinned ? '📌 ' : '') + esc(n.title) + (!(n.readBy || []).includes(S.uid) ? ' <span class="badge">새 글</span>' : '') + '</div><div class="t2">' + esc(uname(n.author) + ' · ' + (n.t || '')) + '</div></div>').join('') : '<div class="empty">공지가 없습니다.</div>');
}
ACT.notice = id => {
  const n = byId('notices', id); if (!n) return;
  if (!(n.readBy = n.readBy || []).includes(S.uid)) { n.readBy.push(S.uid); put('notices', n); render(); }
  modal('<h3>' + esc(n.title) + '</h3><div class="sub">' + esc(uname(n.author) + ' · ' + (n.t || '')) + '</div><div style="white-space:pre-wrap;margin:12px 0">' + esc(n.body) + '</div><button class="btn full" data-act="close">닫기</button>');
};
ACT.newNotice = () => formModal('공지 작성', '<input id="f_t1" placeholder="제목 (필수)"><textarea id="f_m" placeholder="내용"></textarea><label class="chk"><input type="checkbox" id="f_pin"><span>맨 위에 고정</span></label>', () => {
  const t = $('#f_t1').value.trim(); if (!t) return warn('제목을 입력해 주세요.');
  put('notices', create('notices', {title: t, body: $('#f_m').value.trim(), pinned: $('#f_pin').checked, author: S.uid, t: nowStr(), readBy: [S.uid]})); render(); toast('공지를 올렸어요.'); return true;
}, '올리기');

// ---------- 전체 ----------
function viewAll() {
  const items = [['merchants', '가맹점', ''], ['logs', '가맹점 응대 기록 조회', ''], ['notice', '공지사항', unreadNotices() ? unreadNotices() + '건 안 읽음' : '']];
  const q = S.allQ;
  return '<h1>' + esc(S.me.name) + '님, 안녕하세요</h1><div class="sub">' + (can('staff_manage') ? '관리자' : '직원') + ' · ' + esc(S.phone) + '</div>' +
    '<input id="aq" data-q="allQ" placeholder="🔍 기능 검색 (예: 가맹점, ㄱㅁㅈ)" value="' + esc(q) + '">' +
    '<h2>일</h2>' + items.filter(i => kmatch(i[1], q)).map(i => '<div class="menu" data-act="go" data-arg="' + i[0] + '">' + i[1] + (i[2] ? '<span>' + i[2] + '</span>' : '') + '</div>').join('') +
    '<h2>계정</h2><div class="menu" data-act="logout">로그아웃</div>' +
    '<h2>설치하기</h2><div class="sub">아이폰: Safari 아래 공유 버튼 → <b>홈 화면에 추가</b>.<br>안드로이드: 크롬 메뉴 → <b>홈 화면에 추가</b>.<br>PC: 크롬·엣지 주소창 오른쪽의 설치 아이콘(또는 아래 버튼).</div><button class="btn tint full inst" data-act="installApp" style="display:none">PC·폰에 설치하기</button>' +
    '<div class="sub" style="margin-top:22px">재고·계약·실적·보고·문자 발송·자료실 등은 아직 앱에서만 쓸 수 있어요. 웹은 계속 늘려 가고 있어요.</div><div class="sub" style="text-align:center;margin-top:20px">나인패밀리 ' + VERSION + ' · 주식회사 나인페이먼츠</div>';
}
ACT.go = k => { S.sub = k; render(); };
ACT.logout = () => { if (confirm('로그아웃할까요?')) logout(); };

// ---------- 팝업 ----------
function modal(html) {
  closeModal();
  const d = document.createElement('div'); d.className = 'modal'; d.id = 'modal'; d.innerHTML = '<div class="sheet">' + html + '</div>';
  d.onclick = ev => { if (ev.target === d && !d.querySelector('input:not([type=checkbox]),textarea')) closeModal(); const a = ev.target.closest('[data-act]'); if (a && ACT[a.dataset.act]) { ev.stopPropagation(); ACT[a.dataset.act](a.dataset.arg, a, ev); } };
  document.body.appendChild(d);
}
function closeModal() { const m = $('#modal'); if (m) m.remove(); }
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
  S.uid = 1; S.phone = '+821000000000';
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
  S.data.notices.set(81, {id: 81, title: '웹앱 체험 중입니다', body: '이 화면은 연습용 가짜 자료예요. 서버에 아무것도 저장되지 않아요.', pinned: true, author: 1, t: nowStr(), readBy: []});
}
if (DEMO) { demoSeed(); render(); } else {
  firebase.initializeApp(CFG); auth = firebase.auth(); fs = firebase.firestore(); auth.languageCode = 'ko';
  loginView('phone');
  auth.onAuthStateChanged(u => { if (u && !S.uid) afterLogin(u); });
}
let wasWide = isWide();
window.addEventListener('resize', () => { if (S.me && wasWide !== isWide()) { wasWide = isWide(); later(); } });
if ('serviceWorker' in navigator && !DEMO) navigator.serviceWorker.register('sw.js').catch(() => {});
