'use strict';

/**
 * M1 用户认证模块 — 接口验收测试
 * 用法：先启动服务，再执行 node test/m1.test.js
 */

const BASE = process.env.BASE_URL || 'http://127.0.0.1:3000';

let pass = 0;
let fail = 0;

function check(name, expected, actual) {
  if (actual === expected) {
    pass++;
    console.log('PASS  ' + name.padEnd(44) + ' expect ' + expected + ' got ' + actual);
  } else {
    fail++;
    console.log('FAIL  ' + name.padEnd(44) + ' expect ' + expected + ' got ' + actual);
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

async function uploadAvatar(token, filename, mime, buf) {
  const form = new FormData();
  form.append('avatar', new Blob([buf], { type: mime }), filename);
  const res = await fetch(BASE + '/api/auth/avatar', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token },
    body: form,
  });
  const text = await res.text();
  let data = null;
  try { data = JSON.parse(text); } catch { data = text; }
  return { status: res.status, data };
}

const PNG_1x1 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64'
);

async function main() {
  const username = 'alice_' + Date.now();
  let token = '';
  let r;

  r = await req('GET', '/api/health');
  check('health', 200, r.status);

  r = await req('POST', '/api/auth/register', { username, password: 'secret123', nickname: 'Alice' });
  check('register -> 201', 201, r.status);
  token = r.data && r.data.token;
  if (!token) throw new Error('register did not return token: ' + JSON.stringify(r.data));

  r = await req('POST', '/api/auth/register', { username, password: 'secret123' });
  check('register duplicate -> 409', 409, r.status);

  r = await req('POST', '/api/auth/register', { username: 'bob_' + Date.now(), password: '123' });
  check('register short password -> 400', 400, r.status);

  r = await req('POST', '/api/auth/register', { username: 'a', password: 'secret123' });
  check('register invalid username -> 400', 400, r.status);

  r = await req('POST', '/api/auth/login', { username, password: 'wrongpass' });
  check('login wrong password -> 401', 401, r.status);

  r = await req('POST', '/api/auth/login', { username, password: 'secret123' });
  check('login ok -> 200', 200, r.status);
  check('login returns user', username, r.data && r.data.user && r.data.user.username);
  token = r.data.token;

  r = await req('GET', '/api/auth/me', undefined, token);
  check('me with token -> 200', 200, r.status);
  check('me username matches', username, r.data && r.data.user && r.data.user.username);

  r = await req('GET', '/api/auth/me');
  check('me without token -> 401', 401, r.status);

  r = await req('GET', '/api/auth/me', undefined, 'bad.token.value');
  check('me with bad token -> 401', 401, r.status);

  r = await req('PUT', '/api/auth/profile', { nickname: 'Alicia' }, token);
  check('update nickname -> 200', 200, r.status);
  check('nickname changed', 'Alicia', r.data && r.data.user && r.data.user.nickname);

  r = await uploadAvatar(token, 'a.png', 'image/png', PNG_1x1);
  check('upload avatar -> 200', 200, r.status);
  const avatarUrl = r.data && r.data.user && r.data.user.avatarUrl;
  check('avatarUrl startsWith /uploads/', true, typeof avatarUrl === 'string' && avatarUrl.indexOf('/uploads/') === 0);
  if (typeof avatarUrl === 'string') {
    r = await req('GET', avatarUrl);
    check('serve uploaded avatar -> 200', 200, r.status);
  }

  r = await uploadAvatar(token, 'a.txt', 'text/plain', Buffer.from('hello'));
  check('avatar non-image -> 400', 400, r.status);

  const emptyForm = new FormData();
  const res = await fetch(BASE + '/api/auth/avatar', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token },
    body: emptyForm,
  });
  check('avatar without file -> 400', 400, res.status);

  // ---- 头像大小上限（10MB）----
  const nineMB = Buffer.alloc(9 * 1024 * 1024, 1);
  r = await uploadAvatar(token, 'nine.png', 'image/png', nineMB);
  check('avatar 9MB accepted -> 200', 200, r.status);

  const elevenMB = Buffer.alloc(11 * 1024 * 1024, 1);
  r = await uploadAvatar(token, 'eleven.png', 'image/png', elevenMB);
  check('avatar 11MB rejected -> 400', 400, r.status);

  // ---- 补充：空/缺失字段 ----
  r = await req('POST', '/api/auth/register', { username: 'nopass_' + Date.now() });
  check('register missing password -> 400', 400, r.status);

  r = await req('POST', '/api/auth/register', {});
  check('register empty body -> 400', 400, r.status);

  r = await req('POST', '/api/auth/login', { username });
  check('login missing password -> 400', 400, r.status);

  // ---- 补充：非法 JSON ----
  const badJson = await fetch(BASE + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{bad json',
  });
  check('malformed JSON -> 400', 400, badJson.status);

  // ---- 补充：用户名区分大小写 ----
  const mixedUser = 'Usr' + (Date.now() % 100000000);
  r = await req('POST', '/api/auth/register', { username: mixedUser, password: 'secret123' });
  check('register mixed-case -> 201', 201, r.status);
  check('username case preserved', mixedUser, r.data && r.data.user && r.data.user.username);

  r = await req('POST', '/api/auth/login', { username: mixedUser, password: 'secret123' });
  check('login exact-case -> 200', 200, r.status);

  r = await req('POST', '/api/auth/login', { username: mixedUser.toUpperCase(), password: 'secret123' });
  check('login wrong-case -> 401', 401, r.status);

  // ---- 补充：重新上传头像替换旧文件 ----
  r = await uploadAvatar(token, 'b.png', 'image/png', PNG_1x1);
  check('re-upload avatar -> 200', 200, r.status);
  const avatarUrl2 = r.data && r.data.user && r.data.user.avatarUrl;
  check('avatarUrl changed', true, typeof avatarUrl2 === 'string' && avatarUrl2 !== avatarUrl);
  if (typeof avatarUrl === 'string' && typeof avatarUrl2 === 'string') {
    r = await req('GET', avatarUrl);
    check('old avatar deleted -> 404', 404, r.status);
  }

  console.log('');
  console.log('RESULT: pass=' + pass + ' fail=' + fail);
  if (fail > 0) process.exit(1);
}

main().catch((err) => {
  console.error('TEST ERROR:', err);
  process.exit(2);
});
