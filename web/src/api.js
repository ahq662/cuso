const API_BASE = import.meta.env.VITE_API_BASE || '/api';

const TOKEN_KEY = 'cuso_token';

export const UNAUTHORIZED_EVENT = 'cuso:unauthorized';

/** 把后端返回的相对资源路径（如 /uploads/x.png）转成可访问 URL */
export function assetUrl(path) {
  if (!path) return '';
  if (/^https?:\/\//i.test(path)) return path;
  if (API_BASE.startsWith('http')) {
    try {
      return new URL(API_BASE).origin + (path.startsWith('/') ? path : '/' + path);
    } catch (e) {
      return path;
    }
  }
  return path;
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

const USER_KEY = 'cuso_user';

export function getCachedUser() {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

export function setCachedUser(user) {
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
  else localStorage.removeItem(USER_KEY);
}

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function request(path, options) {
  const opts = options || {};
  const method = opts.method || 'GET';
  const headers = {};
  const token = getToken();

  if (opts.auth !== false && token) headers.Authorization = 'Bearer ' + token;

  let body;
  if (opts.body instanceof FormData) {
    body = opts.body;
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(opts.body);
  }

  let res;
  try {
    res = await fetch(API_BASE + path, { method, headers, body });
  } catch (e) {
    throw new ApiError('无法连接服务器，请确认后端已启动', 0);
  }

  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch (e) {
    data = text;
  }

  // 已登录接口返回 401：清 token 并通知全局退出
  if (res.status === 401 && opts.auth !== false) {
    setToken('');
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
    }
  }

  if (!res.ok) {
    throw new ApiError((data && data.error) || ('请求失败（' + res.status + '）'), res.status);
  }
  return data;
}

export const api = {
  register: (payload) => request('/auth/register', { method: 'POST', body: payload, auth: false }),
  login: (payload) => request('/auth/login', { method: 'POST', body: payload, auth: false }),
  me: () => request('/auth/me'),
  updateProfile: (payload) => request('/auth/profile', { method: 'PUT', body: payload }),
  uploadAvatar: (file) => {
    const form = new FormData();
    form.append('avatar', file);
    return request('/auth/avatar', { method: 'POST', body: form });
  },

  // ---- 频道 ----
  listMine: () => request('/channels/mine'),
  listPublic: () => request('/channels/public'),
  createChannel: (name) => request('/channels', { method: 'POST', body: { name } }),
  joinChannel: (payload) => request('/channels/join', { method: 'POST', body: payload }),
  leaveChannel: (channelId) => request('/channels/' + channelId + '/leave', { method: 'POST' }),
  deleteChannel: (channelId) => request('/channels/' + channelId, { method: 'DELETE' }),
  kickMember: (channelId, userId) =>
    request('/channels/' + channelId + '/members/' + userId, { method: 'DELETE' }),
  channelDetail: (channelId) => request('/channels/' + channelId),
  rtcToken: (channelId) => request('/rtc/token', { method: 'POST', body: { channelId } }),
};
