import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { io } from 'socket.io-client';
import { getToken } from '../api';
import { useAuth } from '../auth/AuthContext';

const SocketContext = createContext(null);

function resolveSocketUrl() {
  const explicit = import.meta.env.VITE_SOCKET_URL;
  if (explicit) return explicit;

  const apiBase = import.meta.env.VITE_API_BASE || '';
  if (apiBase.startsWith('http')) {
    try {
      return new URL(apiBase).origin;
    } catch (e) {
      return undefined;
    }
  }
  return undefined; // 默认连当前页面 origin（开发环境由 Vite 代理到后端）
}

export function SocketProvider({ children }) {
  const { user } = useAuth();
  const userId = user ? user.id : null;
  const [socket, setSocket] = useState(null);
  const [connected, setConnected] = useState(false);
  const socketRef = useRef(null);

  useEffect(() => {
    if (!userId) {
      if (socketRef.current) {
        socketRef.current.disconnect();
        socketRef.current = null;
      }
      setSocket(null);
      setConnected(false);
      return;
    }

    const token = getToken();
    if (!token) return;

    const s = io(resolveSocketUrl(), {
      auth: { token },
      reconnection: true,
    });
    socketRef.current = s;
    setSocket(s);

    const onConnect = () => setConnected(true);
    const onDisconnect = () => setConnected(false);
    const onError = () => setConnected(false);

    s.on('connect', onConnect);
    s.on('disconnect', onDisconnect);
    s.on('connect_error', onError);

    return () => {
      s.off('connect', onConnect);
      s.off('disconnect', onDisconnect);
      s.off('connect_error', onError);
      s.disconnect();
      if (socketRef.current === s) socketRef.current = null;
      setSocket(null);
      setConnected(false);
    };
  }, [userId]);

  const value = useMemo(() => ({ socket, connected }), [socket, connected]);

  return <SocketContext.Provider value={value}>{children}</SocketContext.Provider>;
}

export function useSocket() {
  const ctx = useContext(SocketContext);
  if (!ctx) throw new Error('useSocket 必须在 SocketProvider 内使用');
  return ctx;
}
