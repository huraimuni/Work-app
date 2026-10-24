'use strict';
/* 나인패밀리 PC 프로그램 — 토스포스처럼 설치형 창으로 웹앱(https://ninepayments.github.io/Work-app/)을 띄운다.
 * 화면과 기능은 웹앱 그대로라서, 웹을 고치면 이 프로그램도 다시 설치할 필요 없이 바로 바뀐다. 자료는 서버(Firebase)에 있다. */
const {app, BrowserWindow, shell, session, Menu} = require('electron');

const HOME = 'https://ninepayments.github.io/Work-app/';
const ORIGIN = new URL(HOME).origin;
let win = null;

if (!app.requestSingleInstanceLock()) app.quit(); // 이미 켜져 있으면 그 창을 앞으로
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });

function inApp(url) { // 우리 웹앱과 로그인(문자 인증·reCAPTCHA) 화면만 창 안에서, 나머지 링크는 기본 브라우저로
  try {
    const u = new URL(url);
    return (u.origin === ORIGIN && u.pathname.startsWith('/Work-app')) || /(^|\.)(firebaseapp\.com|google\.com|gstatic\.com|recaptcha\.net)$/.test(u.hostname);
  } catch (e) { return false; }
}

function createWindow() {
  win = new BrowserWindow({
    width: 1280, height: 860, minWidth: 900, minHeight: 600, title: '나인패밀리', backgroundColor: '#ffffff', autoHideMenuBar: true,
    webPreferences: {contextIsolation: true, sandbox: true, nodeIntegration: false, spellcheck: false}
  });
  win.loadURL(HOME);
  win.webContents.on('before-input-event', (e, i) => { // F5·Ctrl+R = 새 버전으로 새로고침 (메뉴줄이 없어서 직접 건다)
    if (i.type === 'keyDown' && (i.key === 'F5' || (i.control && i.key.toLowerCase() === 'r'))) { e.preventDefault(); win.webContents.reloadIgnoringCache(); }
  });
  win.webContents.setWindowOpenHandler(({url}) => { if (!inApp(url)) shell.openExternal(url); return {action: 'deny'}; });
  win.webContents.on('will-navigate', (e, url) => { if (!inApp(url)) { e.preventDefault(); shell.openExternal(url); } });
  win.webContents.on('did-fail-load', (e, code, desc, url, isMain) => { // 인터넷이 끊겨 처음 화면을 못 받으면 안내 후 다시 시도
    if (!isMain || code === -3) return;
    win.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent('<body style="font-family:sans-serif;text-align:center;padding-top:20vh"><h2>인터넷 연결을 확인해 주세요</h2><p>연결되면 자동으로 다시 열려요.</p><script>setTimeout(()=>location.href=' + JSON.stringify(HOME) + ',5000)</script></body>'));
  });
}

app.whenReady().then(async () => {
  Menu.setApplicationMenu(null);
  // 켤 때마다 임시 보관 화면을 비워 항상 최신 웹앱을 받는다 (로그인·자료 보관은 그대로)
  try { await session.defaultSession.clearCache(); } catch (e) {}
  // 바코드 카메라·알림만 허용 (우리 웹앱에서 요청할 때)
  session.defaultSession.setPermissionRequestHandler((wc, perm, cb, details) => cb(inApp(details.requestingUrl || wc.getURL()) && ['media', 'notifications', 'clipboard-sanitized-write'].includes(perm)));
  createWindow();
});
app.on('window-all-closed', () => app.quit());
