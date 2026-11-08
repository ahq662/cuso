import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { assetUrl } from '../api';
import { useAuth } from '../auth/AuthContext';
import { useSocket } from '../socket/SocketContext';
import { useVoice } from '../voice/VoiceContext';
import { isSoundOn, playClick, setSoundOn } from '../sound/click';
import PixelSheep from './PixelSheep';

export default function AppLayout() {
  const { user, logout } = useAuth();
  const { socket, connected } = useSocket();
  const navigate = useNavigate();
  const location = useLocation();
  const voice = useVoice();
  const [notice, setNotice] = useState('');
  const [soundOn, setSoundState] = useState(() => isSoundOn());

  function toggleSound() {
    const next = !soundOn;
    setSoundState(next);
    setSoundOn(next);
    if (next) playClick();
  }

  // 被管理员踢出频道时，全局提示并回到频道列表
  useEffect(() => {
    if (!socket) return undefined;
    const onKicked = (payload) => {
      setNotice('你已被管理员移出频道「' + ((payload && payload.channelName) || '') + '」');
      navigate('/channels');
    };
    socket.on('member:kicked', onKicked);
    return () => socket.off('member:kicked', onKicked);
  }, [socket, navigate]);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <PixelSheep width={64} className="brand-sheep-sm" />
          <span className="sidebar-brand-text">CUSO 语音</span>
        </div>

        <nav className="sidebar-nav">
          <NavLink
            to="/channels"
            className={({ isActive }) => 'nav-item' + (isActive ? ' nav-item-active' : '')}
          >
            <span className="nav-icon">▤</span>
            <span>我的频道</span>
          </NavLink>
          <NavLink
            to="/lobby"
            className={({ isActive }) => 'nav-item' + (isActive ? ' nav-item-active' : '')}
          >
            <span className="nav-icon">◈</span>
            <span>大厅</span>
          </NavLink>
          <NavLink
            to="/profile"
            className={({ isActive }) => 'nav-item' + (isActive ? ' nav-item-active' : '')}
          >
            <span className="nav-icon">☺</span>
            <span>我的</span>
          </NavLink>
        </nav>

        <div className="sidebar-foot">
          <div className="sidebar-user">
            {user.avatarUrl ? (
              <img className="mini-avatar" src={assetUrl(user.avatarUrl)} alt="" />
            ) : (
              <span className="mini-avatar mini-avatar-text">
                {(user.nickname || user.username || '?').slice(0, 1)}
              </span>
            )}
            <div className="sidebar-user-meta">
              <div className="sidebar-user-name">{user.nickname || user.username}</div>
              <div className="conn-dot">
                <span className={connected ? 'dot dot-on' : 'dot dot-off'} />
                {connected ? '实时已连接' : '实时未连接'}
              </div>
            </div>
          </div>
          <div className="sidebar-actions">
            <button
              className="pixel-btn pixel-btn-sm pixel-btn-ghost"
              type="button"
              onClick={toggleSound}
              title="音效开关"
              aria-label="音效开关"
            >
              {soundOn ? '🔊' : '🔇'}
            </button>
            <button className="pixel-btn pixel-btn-sm pixel-btn-ghost" type="button" onClick={logout}>
              退出
            </button>
          </div>
        </div>
      </aside>

      <main className="app-main">
        {notice ? (
          <div className="global-alert" role="alert">
            <span>{notice}</span>
            <button type="button" onClick={() => setNotice('')} aria-label="关闭">
              ✕
            </button>
          </div>
        ) : null}
        {voice.channel && !location.pathname.includes('/voice') ? (
          <div className="voice-dock">
            <button
              className="voice-dock-main"
              type="button"
              onClick={() => navigate('/channels/' + voice.channel.id + '/voice')}
            >
              <span className="voice-dock-dot" />
              <span className="voice-dock-title">正在语音 · {voice.channel.name}</span>
              <span className="voice-dock-meta">{voice.members.length} 人 · 点击返回</span>
            </button>
            <button className="pixel-btn pixel-btn-sm pixel-btn-danger" type="button" onClick={() => voice.leave()}>
              退出
            </button>
          </div>
        ) : null}
        <Outlet />
      </main>
    </div>
  );
}
