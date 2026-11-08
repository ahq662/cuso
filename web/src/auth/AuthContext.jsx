import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, getCachedUser, getToken, setCachedUser, setToken, UNAUTHORIZED_EVENT } from '../api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => getCachedUser());
  const [loading, setLoading] = useState(true);

  // 启动时若本地有 token，则校验并恢复登录态
  useEffect(() => {
    let cancelled = false;

    async function bootstrap() {
      if (!getToken()) {
        if (!cancelled) {
          setCachedUser(null);
          setUser(null);
          setLoading(false);
        }
        return;
      }
      try {
        const data = await api.me();
        if (!cancelled) {
          setUser(data.user);
          setCachedUser(data.user);
        }
      } catch (e) {
        // 只有鉴权失败才清除 token；网络故障不清，避免误踢下线
        if (!cancelled && e && (e.status === 401 || e.status === 403)) {
          setToken('');
          setCachedUser(null);
          setUser(null);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    bootstrap();
    return () => {
      cancelled = true;
    };
  }, []);

  // 任意已登录接口返回 401 时，统一退出登录
  useEffect(() => {
    function onUnauthorized() {
      setToken('');
      setCachedUser(null);
      setUser(null);
    }
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, []);

  const login = useCallback(async (username, password) => {
    const data = await api.login({ username, password });
    setToken(data.token);
    setCachedUser(data.user);
    setUser(data.user);
    return data.user;
  }, []);

  const register = useCallback(async (payload) => {
    const data = await api.register(payload);
    setToken(data.token);
    setCachedUser(data.user);
    setUser(data.user);
    return data.user;
  }, []);

  const logout = useCallback(() => {
    setToken('');
    setCachedUser(null);
    setUser(null);
  }, []);

  const refresh = useCallback(async () => {
    const data = await api.me();
    setCachedUser(data.user);
    setUser(data.user);
    return data.user;
  }, []);

  const updateUser = useCallback((next) => {
    setCachedUser(next);
    setUser(next);
  }, []);

  const value = { user, loading, login, register, logout, refresh, updateUser };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth 必须在 AuthProvider 内使用');
  return ctx;
}
