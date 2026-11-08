import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useSyncExternalStore,
} from 'react';
import { useAuth } from '../auth/AuthContext';
import { useSocket } from '../socket/SocketContext';
import { VoiceSession } from './VoiceSession';

const VoiceContext = createContext(null);

export function VoiceProvider({ children }) {
  const { user } = useAuth();
  const { socket } = useSocket();
  const sessionRef = useRef(null);
  if (!sessionRef.current) sessionRef.current = new VoiceSession();
  const session = sessionRef.current;

  useEffect(() => {
    session.setSocket(socket);
  }, [session, socket]);

  useEffect(() => {
    session.setUser(user);
  }, [session, user]);

  const state = useSyncExternalStore(session.subscribe, session.getSnapshot, session.getSnapshot);

  const join = useCallback((channel) => session.join(channel), [session]);
  const leave = useCallback(() => session.leave(), [session]);
  const toggleMic = useCallback(() => session.toggleMic(), [session]);
  const enableMic = useCallback(() => session.enableMic(), [session]);
  const setUserVolume = useCallback((id, v) => session.setUserVolume(id, v), [session]);
  const adminToggleMute = useCallback((m) => session.adminToggleMute(m), [session]);
  const retryPlay = useCallback(() => session.retryPlay(), [session]);

  const value = useMemo(
    () => ({
      ...state,
      join,
      leave,
      toggleMic,
      enableMic,
      setUserVolume,
      adminToggleMute,
      retryPlay,
    }),
    [state, join, leave, toggleMic, enableMic, setUserVolume, adminToggleMute, retryPlay],
  );

  return <VoiceContext.Provider value={value}>{children}</VoiceContext.Provider>;
}

export function useVoice() {
  const ctx = useContext(VoiceContext);
  if (!ctx) throw new Error('useVoice must be used within VoiceProvider');
  return ctx;
}
