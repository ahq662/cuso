import { lazy, Suspense } from 'react';
import GlobalClickSound from './sound/GlobalClickSound';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { SocketProvider } from './socket/SocketContext';
import { VoiceProvider } from './voice/VoiceContext';
import AppLayout from './components/AppLayout';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ProfilePage from './pages/ProfilePage';
import ChannelsPage from './pages/ChannelsPage';
import LobbyPage from './pages/LobbyPage';
import ChannelDetailPage from './pages/ChannelDetailPage';

// 语音页按需加载，避免 Agora SDK 打进首屏包
const VoiceRoomPage = lazy(() => import('./pages/VoiceRoomPage'));

function Loading() {
  return <div className="center-screen">载入中…</div>;
}

function ProtectedRoute({ children }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading && !user) return <Loading />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return children;
}

function PublicOnly({ children }) {
  const { user, loading } = useAuth();
  if (loading && !user) return <Loading />;
  if (user) return <Navigate to="/channels" replace />;
  return children;
}

export default function App() {
  return (
    <>
      <GlobalClickSound />
      <AuthProvider>
        <SocketProvider>
        <VoiceProvider>
        <BrowserRouter>
          <Routes>
            <Route
              path="/login"
              element={
                <PublicOnly>
                  <LoginPage />
                </PublicOnly>
              }
            />
            <Route
              path="/register"
              element={
                <PublicOnly>
                  <RegisterPage />
                </PublicOnly>
              }
            />

            <Route
              element={
                <ProtectedRoute>
                  <AppLayout />
                </ProtectedRoute>
              }
            >
              <Route path="/" element={<Navigate to="/channels" replace />} />
              <Route path="/channels" element={<ChannelsPage />} />
              <Route
                path="/channels/:id/voice"
                element={
                  <Suspense fallback={<Loading />}>
                    <VoiceRoomPage />
                  </Suspense>
                }
              />
              <Route path="/channels/:id" element={<ChannelDetailPage />} />
              <Route path="/lobby" element={<LobbyPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="*" element={<Navigate to="/channels" replace />} />
            </Route>
          </Routes>
        </BrowserRouter>
        </VoiceProvider>
        </SocketProvider>
      </AuthProvider>
    </>
  );
}
