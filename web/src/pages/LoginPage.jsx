import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import PixelSheep from '../components/PixelSheep';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state && location.state.from) || '/channels';

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    if (!username.trim() || !password) {
      setError('请输入用户名和密码');
      return;
    }
    setBusy(true);
    try {
      await login(username.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.message || '登录失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-shell">
      <section className="auth-hero">
        <div className="auth-hero-sheep">
          <PixelSheep width={176} />
        </div>
        <h1 className="auth-hero-title">CUSO 语音</h1>
        <p className="auth-hero-tagline">PIXEL VOICE CHAT</p>
        <div className="auth-stars" aria-hidden="true">
          <span className="auth-star auth-star-1" />
          <span className="auth-star auth-star-2" />
          <span className="auth-star auth-star-3" />
          <span className="auth-star auth-star-4" />
          <span className="auth-star auth-star-5" />
          <span className="auth-star auth-star-6" />
        </div>
        <div className="auth-hero-foot">v0.1 · PIXEL EDITION</div>
      </section>

      <section className="auth-panel">
        <div className="pixel-card auth-card">
          <h2 className="auth-title">登录</h2>
          <p className="auth-sub">欢迎回来，羊羊在等你</p>

          <form onSubmit={onSubmit} className="form">
            <label className="field">
              <span className="field-label">用户名</span>
              <input
                className="pixel-input"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="输入用户名"
                autoComplete="username"
                autoFocus
              />
            </label>

            <label className="field">
              <span className="field-label">密码</span>
              <input
                className="pixel-input"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="输入密码"
                autoComplete="current-password"
              />
            </label>

            {error ? (
              <div className="pixel-alert" role="alert">
                {error}
              </div>
            ) : null}

            <button className="pixel-btn pixel-btn-block" type="submit" disabled={busy}>
              {busy ? '登录中…' : '登 录'}
            </button>
          </form>

          <p className="auth-switch">
            没有账号？<Link className="pixel-link" to="/register">去注册</Link>
          </p>
        </div>
      </section>
    </div>
  );
}
