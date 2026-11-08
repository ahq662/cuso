'use strict';

/**
 * M2 频道管理模块 — 接口验收测试
 * 用法：先启动服务，再执行 node test/m2.test.js
 */

const BASE = process.env.BASE_URL || 'http://127.0.0.1:3000';
const INVITE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

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
  return r.data.token;
}

async function main() {
  const stamp = Date.now();
  const tokenA = await register('ch_a' + stamp);
  const tokenB = await register('ch_b' + stamp);
  let r;

  // ---- 创建频道 ----
  r = await req('POST', '/api/channels', { name: '开黑小队' }, tokenA);
  check('create channel -> 201', 201, r.status);
  const ch1 = r.data.channel;
  check('inviteCode length = 6', 6, (ch1.inviteCode || '').length);
  check('inviteCode charset valid', true, (ch1.inviteCode || '').split('').every((c) => INVITE_ALPHABET.includes(c)));
  check('creator auto member (isMember)', true, ch1.isMember);
  check('memberCount = 1', 1, ch1.memberCount);
  check('creatorId = A', true, typeof ch1.creatorId === 'number' && ch1.creatorId > 0);

  // ---- 非法创建 ----
  r = await req('POST', '/api/channels', { name: '' }, tokenA);
  check('create empty name -> 400', 400, r.status);
  r = await req('POST', '/api/channels', { name: 'x'.repeat(21) }, tokenA);
  check('create too-long name -> 400', 400, r.status);
  r = await req('POST', '/api/channels', { name: 'no auth' });
  check('create without token -> 401', 401, r.status);

  // ---- 我的频道 / 大厅 ----
  r = await req('GET', '/api/channels/mine', undefined, tokenA);
  check('mine A includes ch1', true, r.data.channels.some((c) => c.id === ch1.id));

  r = await req('GET', '/api/channels/mine', undefined, tokenB);
  check('mine B excludes ch1', false, r.data.channels.some((c) => c.id === ch1.id));

  r = await req('GET', '/api/channels/public', undefined, tokenB);
  const pubCh1 = r.data.channels.find((c) => c.id === ch1.id);
  check('public includes ch1', true, !!pubCh1);
  check('public ch1 isMember=false for B', false, pubCh1 && pubCh1.isMember);

  // ---- 邀请码加入（小写输入也应成功）----
  r = await req('POST', '/api/channels/join', { inviteCode: ch1.inviteCode.toLowerCase() }, tokenB);
  check('join by lowercase invite -> 200', 200, r.status);
  check('join sets isMember=true', true, r.data.channel.isMember);
  check('join increments memberCount=2', 2, r.data.channel.memberCount);

  r = await req('POST', '/api/channels/join', { inviteCode: ch1.inviteCode }, tokenB);
  check('join again -> 409', 409, r.status);

  r = await req('GET', '/api/channels/mine', undefined, tokenB);
  check('mine B includes ch1 after join', true, r.data.channels.some((c) => c.id === ch1.id));

  // ---- 频道详情 ----
  r = await req('GET', '/api/channels/' + ch1.id, undefined, tokenB);
  check('detail -> 200', 200, r.status);
  check('detail memberCount=2', 2, r.data.channel.memberCount);
  check('detail isMember=true', true, r.data.channel.isMember);
  check('detail members length=2', 2, r.data.members.length);
  check('detail members have username', true, r.data.members.every((m) => typeof m.username === 'string' && m.username.length > 0));

  // ---- 非法 id / 不存在 ----
  r = await req('GET', '/api/channels/abc', undefined, tokenA);
  check('detail invalid id -> 400', 400, r.status);
  r = await req('GET', '/api/channels/999999', undefined, tokenA);
  check('detail missing -> 404', 404, r.status);

  // ---- 加入异常 ----
  r = await req('POST', '/api/channels/join', { inviteCode: 'ZZZZZZ' }, tokenB);
  check('join bad invite -> 404', 404, r.status);
  r = await req('POST', '/api/channels/join', {}, tokenB);
  check('join no args -> 400', 400, r.status);

  r = await req('POST', '/api/channels/join', { channelId: 'abc' }, tokenB);
  check('join invalid channelId -> 400', 400, r.status);

  // ---- 通过 channelId 加入 ----
  r = await req('POST', '/api/channels', { name: '第二个频道' }, tokenA);
  const ch2 = r.data.channel;
  r = await req('POST', '/api/channels/join', { channelId: ch2.id }, tokenB);
  check('join by channelId -> 200', 200, r.status);

  // ---- 退出频道 ----
  r = await req('POST', '/api/channels/' + ch2.id + '/leave', undefined, tokenB);
  check('leave ch2 -> 200', 200, r.status);
  r = await req('GET', '/api/channels/mine', undefined, tokenB);
  check('mine B excludes ch2 after leave', false, r.data.channels.some((c) => c.id === ch2.id));
  r = await req('POST', '/api/channels/' + ch2.id + '/leave', undefined, tokenB);
  check('leave again -> 404', 404, r.status);

  // ---- 删除权限 ----
  r = await req('DELETE', '/api/channels/' + ch1.id, undefined, tokenB);
  check('delete by non-creator -> 403', 403, r.status);
  r = await req('DELETE', '/api/channels/' + ch1.id, undefined, tokenA);
  check('delete by creator -> 200', 200, r.status);
  r = await req('GET', '/api/channels/' + ch1.id, undefined, tokenA);
  check('deleted channel -> 404', 404, r.status);

  // ---- 邀请码唯一性 ----
  const codes = new Set();
  for (let i = 0; i < 5; i++) {
    const rr = await req('POST', '/api/channels', { name: 'uniq-' + i }, tokenA);
    codes.add(rr.data.channel.inviteCode);
  }
  check('invite codes unique (5)', 5, codes.size);

  console.log('');
  console.log('RESULT: pass=' + pass + ' fail=' + fail);
  if (fail > 0) process.exit(1);
}

main().catch((err) => {
  console.error('TEST ERROR:', err);
  process.exit(2);
});
