'use strict';

/**
 * M3 实时状态 + Agora Token 模块 — 接口验收测试
 * 用法：先启动服务，再执行 node test/m3.test.js
 * 说明：若服务端配置了 AGORA 凭据，会校验 token 生成；未配置则校验 503 提示。
 */

const { io } = require('socket.io-client');

const BASE = process.env.BASE_URL || 'http://127.0.0.1:3000';

let pass = 0;
let fail = 0;

function check(name, expected, actual) {
  if (actual === expected) {
    pass++;
    console.log('PASS  ' + name.padEnd(46) + ' expect ' + expected + ' got ' + actual);
  } else {
    fail++;
    console.log('FAIL  ' + name.padEnd(46) + ' expect ' + expected + ' got ' + actual);
  }
}

async function req(method, path, body, token) {
  const headers = {};
  if (token) headers.Authorization = 'Bearer ' + token;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let data = null;
  try { data = JSON.parse(text); } catch { data = text; }
  return { status: res.status, data };
}

async function register(username) {
  const r = await req('POST', '/api/auth/register', { username, password: 'secret123' });
  if (r.status !== 201) throw new Error('register failed: ' + JSON.stringify(r.data));
  return r.data; // { token, user }
}

function connectSocket(token) {
  return new Promise((resolve, reject) => {
    const s = io(BASE, {
      auth: { token },
      transports: ['websocket'],
      reconnection: false,
      forceNew: true,
      timeout: 5000,
    });
    s.on('connect', () => resolve(s));
    s.on('connect_error', (e) => reject(e));
  });
}

function waitPresence(socket, predicate, timeoutMs = 4000) {
  return new Promise((resolve, reject) => {
    let done = false;
    const timer = setTimeout(() => {
      if (done) return;
      done = true;
      socket.off('presence:update', handler);
      reject(new Error('presence timeout'));
    }, timeoutMs);

    function handler(data) {
      if (!predicate(data)) return;
      if (done) return;
      done = true;
      clearTimeout(timer);
      socket.off('presence:update', handler);
      resolve(data);
    }

    socket.on('presence:update', handler);
  });
}

function waitEvent(socket, event, timeoutMs = 4000) {
  return new Promise((resolve, reject) => {
    let done = false;
    const timer = setTimeout(() => {
      if (done) return;
      done = true;
      socket.off(event, handler);
      reject(new Error(event + ' timeout'));
    }, timeoutMs);

    function handler(data) {
      if (done) return;
      done = true;
      clearTimeout(timer);
      socket.off(event, handler);
      resolve(data);
    }

    socket.on(event, handler);
  });
}

async function main() {
  const stamp = Date.now();
  const a = await register('m3a' + stamp);
  const b = await register('m3b' + stamp);
  const c = await register('m3c' + stamp);

  // 创建频道，B 加入
  let r = await req('POST', '/api/channels', { name: 'M3测试频道' }, a.token);
  const chId = r.data.channel.id;
  r = await req('POST', '/api/channels/join', { channelId: chId }, b.token);
  check('prepare: join channel', 200, r.status);

  // ---- Socket 鉴权 ----
  const sA = await connectSocket(a.token);
  const sB = await connectSocket(b.token);
  check('socket A authenticated', true, sA.connected);
  check('socket B authenticated', true, sB.connected);

  // ---- A 加入在线房间 ----
  let p = waitPresence(sA, (d) => d.channelId === chId && d.members.length === 1);
  sA.emit('channel:join', { channelId: chId });
  let ev = await p;
  check('presence after A join: 1', 1, ev.members.length);
  check('presence member is A', a.user.id, ev.members[0] && ev.members[0].userId);

  // ---- B 加入 → 广播 2 人 ----
  p = waitPresence(sA, (d) => d.channelId === chId && d.members.length === 2);
  sB.emit('channel:join', { channelId: chId });
  ev = await p;
  check('presence after B join: 2', 2, ev.members.length);
  check('members have nickname', true, ev.members.every((m) => typeof m.nickname === 'string'));

  // ---- B 离开 → 广播 1 人 ----
  p = waitPresence(sA, (d) => d.channelId === chId && d.members.length === 1);
  sB.emit('channel:leave', { channelId: chId });
  ev = await p;
  check('presence after B leave: 1', 1, ev.members.length);

  // ---- B 重连后断开 → 广播 1 人 ----
  p = waitPresence(sA, (d) => d.channelId === chId && d.members.length === 2);
  sB.emit('channel:join', { channelId: chId });
  await p;
  p = waitPresence(sA, (d) => d.channelId === chId && d.members.length === 1);
  sB.disconnect();
  ev = await p;
  check('presence after B disconnect: 1', 1, ev.members.length);

  // ---- 成员资料实时性：A 改昵称后，新加入者应看到最新昵称 ----
  const newNick = '新昵称' + (Date.now() % 100000);
  r = await req('PUT', '/api/auth/profile', { nickname: newNick }, a.token);
  check('change nickname -> 200', 200, r.status);
  const sB2 = await connectSocket(b.token);
  const pFresh = waitPresence(
    sB2,
    (d) =>
      d.channelId === chId &&
      d.members.length === 2 &&
      d.members.some((m) => m.userId === a.user.id && m.nickname === newNick)
  );
  sB2.emit('channel:join', { channelId: chId });
  const evFresh = await pFresh;
  check('presence shows fresh nickname', true, evFresh.members.some((m) => m.userId === a.user.id && m.nickname === newNick));
  sB2.disconnect();

  // ---- 非成员进房 → presence:error ----
  const sC = await connectSocket(c.token);
  const errP = new Promise((resolve) => sC.once('presence:error', resolve));
  sC.emit('channel:join', { channelId: chId });
  const err = await errP;
  check('non-member join -> presence:error', true, !!err && typeof err.error === 'string');

  // ---- 非法 channelId 进房 → presence:error ----
  const errP2 = new Promise((resolve) => sC.once('presence:error', resolve));
  sC.emit('channel:join', { channelId: 'abc' });
  const err2 = await errP2;
  check('invalid channelId join -> presence:error', true, !!err2 && typeof err2.error === 'string');

  // ---- 创建频道广播 channel:created ----
  const createdP = new Promise((resolve) => sA.once('channel:created', resolve));
  await req('POST', '/api/channels', { name: '广播频道' }, a.token);
  const created = await createdP;
  check('channel:created broadcast', true, !!(created.channel && created.channel.id));

  // ---- 删除频道广播 channel:deleted ----
  const delP = new Promise((resolve) => sA.once('channel:deleted', resolve));
  const toDelete = await req('POST', '/api/channels', { name: '待删除频道' }, a.token);
  const delId = toDelete.data.channel.id;
  await req('DELETE', '/api/channels/' + delId, undefined, a.token);
  const delEv = await delP;
  check('channel:deleted broadcast', delId, delEv.channelId);

  // ---- 管理员权限：踢人 ----
  const sB3 = await connectSocket(b.token);
  p = waitPresence(sA, (d) => d.channelId === chId && d.members.length === 2);
  sB3.emit('channel:join', { channelId: chId });
  await p;

  r = await req('DELETE', '/api/channels/' + chId + '/members/' + b.user.id, undefined, c.token);
  check('kick by non-creator -> 403', 403, r.status);

  const kickedP = waitEvent(sB3, 'member:kicked');
  r = await req('DELETE', '/api/channels/' + chId + '/members/' + b.user.id, undefined, a.token);
  check('kick by creator -> 200', 200, r.status);
  const kicked = await kickedP;
  check('kicked user notified', chId, kicked.channelId);

  r = await req('GET', '/api/channels/' + chId, undefined, b.token);
  check('kicked user no longer member', false, r.data.channel.isMember);

  r = await req('DELETE', '/api/channels/' + chId + '/members/' + a.user.id, undefined, a.token);
  check('kick self -> 400', 400, r.status);

  r = await req('DELETE', '/api/channels/' + chId + '/members/' + c.user.id, undefined, a.token);
  check('kick non-member -> 404', 404, r.status);

  // ---- 管理员权限：强制闭麦 ----
  r = await req('POST', '/api/channels/join', { channelId: chId }, b.token);
  check('kicked user can rejoin', 200, r.status);

  p = waitPresence(sA, (d) => d.channelId === chId && d.members.length === 2);
  sB3.emit('channel:join', { channelId: chId });
  await p;

  const muteP = waitEvent(sB3, 'force:mute');
  p = waitPresence(
    sA,
    (d) => d.channelId === chId && d.members.some((m) => m.userId === b.user.id && m.muted === true)
  );
  sA.emit('admin:mute', { channelId: chId, userId: b.user.id, muted: true });
  const muteEv = await muteP;
  const mutedPresence = await p;
  check('admin mute -> target notified', true, muteEv.muted === true);
  check(
    'presence shows muted=true',
    true,
    mutedPresence.members.some((m) => m.userId === b.user.id && m.muted === true)
  );

  const notAdminErrP = waitEvent(sC, 'presence:error');
  sC.emit('admin:mute', { channelId: chId, userId: b.user.id, muted: true });
  const notAdminErr = await notAdminErrP;
  check('non-admin mute -> presence:error', true, !!notAdminErr && typeof notAdminErr.error === 'string');

  const notMemberErrP = waitEvent(sA, 'presence:error');
  sA.emit('admin:mute', { channelId: chId, userId: c.user.id, muted: true });
  const notMemberErr = await notMemberErrP;
  check('mute non-member -> presence:error', true, !!notMemberErr && typeof notMemberErr.error === 'string');

  const unmuteP = waitEvent(sB3, 'force:mute');
  sA.emit('admin:mute', { channelId: chId, userId: b.user.id, muted: false });
  const unmuteEv = await unmuteP;
  check('admin unmute -> target notified', false, unmuteEv.muted);

  sB3.disconnect();

  // ---- 未鉴权 socket 被拒 ----
  const bad = io(BASE, {
    auth: {},
    transports: ['websocket'],
    reconnection: false,
    forceNew: true,
    timeout: 5000,
  });
  const badResult = await new Promise((resolve) => {
    let done = false;
    const finish = (v) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      resolve(v);
    };
    const timer = setTimeout(() => finish('timeout'), 4000);
    bad.on('connect', () => finish('connected'));
    bad.on('connect_error', () => finish('rejected'));
  });
  check('unauthenticated socket rejected', 'rejected', badResult);
  bad.close();

  // ---- Token 接口 ----
  r = await req('POST', '/api/rtc/token', { channelId: chId });
  check('rtc token without auth -> 401', 401, r.status);

  r = await req('POST', '/api/rtc/token', { channelId: 'abc' }, a.token);
  check('rtc token invalid channelId -> 400', 400, r.status);

  r = await req('POST', '/api/rtc/token', {}, a.token);
  check('rtc token no body -> 400', 400, r.status);

  r = await req('POST', '/api/rtc/token', { channelId: 999999 }, a.token);
  check('rtc token missing channel -> 404', 404, r.status);

  r = await req('POST', '/api/rtc/token', { channelId: chId }, c.token);
  check('rtc token non-member -> 403', 403, r.status);

  r = await req('POST', '/api/rtc/token', { channelId: chId }, a.token);
  if (r.status === 200) {
    check('rtc token generated', true, typeof r.data.token === 'string' && r.data.token.length > 20);
    check('rtc token uid = A', a.user.id, r.data.uid);
    check('rtc token channelName = chId', String(chId), String(r.data.channelName));
    check('rtc token prefix 007', true, String(r.data.token).slice(0, 3) === '007');
  } else {
    check('rtc token unavailable -> 503', 503, r.status);
    check('503 mentions Agora config', true, typeof r.data.error === 'string' && r.data.error.indexOf('Agora') >= 0);
  }

  sA.disconnect();
  sC.disconnect();

  console.log('');
  console.log('RESULT: pass=' + pass + ' fail=' + fail);
  if (fail > 0) process.exit(1);
}

main().catch((err) => {
  console.error('TEST ERROR:', err);
  process.exit(2);
});
