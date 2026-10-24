'use strict';
/* Firestore(nine-family) 전체 자료를 JSON 한 파일로 내보낸다. — 매일 자동 백업(.github/workflows/backup.yml)용
 * 서비스 계정 키(JSON)는 환경변수 FIREBASE_SERVICE_ACCOUNT 로만 받는다. 키를 파일로 남기지 않는다.
 * 결과: {kind, at, project, counts, collections: {이름: [{id, data}]}} — 컬렉션 안의 하위 컬렉션도 함께 담는다. */
const admin = require('firebase-admin');
const fs = require('fs');

const out = process.argv[2];
if (!out) { console.error('사용법: node export.js <저장할 파일>'); process.exit(2); }
const sa = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT || '{}');
admin.initializeApp({credential: admin.credential.cert(sa), projectId: sa.project_id});
const db = admin.firestore();

function plain(v) { // Timestamp 등 Firestore 전용 값을 JSON 으로 옮길 수 있게
  if (v === null || typeof v !== 'object') return v;
  if (v instanceof admin.firestore.Timestamp) return {_ts: v.toDate().toISOString()};
  if (v instanceof admin.firestore.DocumentReference) return {_ref: v.path};
  if (Array.isArray(v)) return v.map(plain);
  const o = {}; for (const k of Object.keys(v)) o[k] = plain(v[k]); return o;
}

async function dumpColl(ref, path, store, counts) {
  const snap = await ref.get();
  store[path] = snap.docs.map(d => ({id: d.id, data: plain(d.data())}));
  counts[path] = snap.size;
  for (const d of snap.docs) {
    for (const sub of await d.ref.listCollections()) await dumpColl(sub, path + '/' + d.id + '/' + sub.id, store, counts);
  }
}

(async () => {
  const store = {}, counts = {};
  for (const c of await db.listCollections()) await dumpColl(c, c.id, store, counts);
  if (!counts.users || !counts.merchants) throw new Error('직원·가맹점 자료가 비어 있어요. 백업을 저장하지 않습니다.');
  fs.writeFileSync(out, JSON.stringify({kind: 'nine-family-firestore-backup', at: new Date().toISOString(), project: sa.project_id, counts, collections: store}));
  console.log('백업 완료:', Object.entries(counts).map(([k, n]) => k + ' ' + n).join(', '));
})().catch(e => { console.error('백업 실패:', e.message); process.exit(1); });
