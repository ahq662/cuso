import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import PixelSheep from '../components/PixelSheep';

const USERNAME_RE = /^[A-Za-z0-9_\u4e00-\u9fa5]{3,20}$/;

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [nickname, setNickname] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setError('');

    const u = username.trim();
    if (!USERNAME_RE.test(u)) {
      setError('用户名需为 3-20 位字母、数字、下划线或中文');
      return;
    }
    if (password.length < 6 || password.length > 64) {
      setError('密码长度需为 6-64 位');
      return;
    }
    if (password !== confirm) {
      setError('两次输入的密码不一致');
      return;
    }
    if (nickname.trim().length > 20) {
      setError('昵称不能超过 20 个字符');
      return;
    }

    setBusy(true);
    try {
      await register({ username: u, password, nickname: nickname.trim() || undefined });
      navigate('/channels', { replace: true });
    } catch (err) {
      setError(err.message || '注册失败');
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
          <h2 className="auth-title">注册</h2>
          <p className="auth-sub">创建账号，加入频道一起开黑</p>

          <form onSubmit={onSubmit} className="form">
            <label className="field">
              <span className="field-label">用户名</span>
              <input
                className="pixel-input"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="3-20 位，字母/数字/下划线/中文"
                autoComplete="username"
                autoFocus
              />
            </label>

            <label className="field">
              <span className="field-label">昵称（可选）</span>
              <input
                className="pixel-input"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                placeholder="别人看到的名字"
                maxLength={20}
              />
            </label>

            <label className="field">
              <span className="field-label">密码</span>
              <input
                className="pixel-input"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="至少 6 位"
                autoComplete="new-password"
              />
            </label>

            <label className="field">
              <span className="field-label">确认密码</span>
              <input
                className="pixel-input"
                type="password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                placeholder="再输入一次"
                autoComplete="new-password"
              />
            </label>

            {error ? (
              <div className="pixel-alert" role="alert">
                {error}
              </div>
            ) : null}

            <button className="pixel-btn pixel-btn-block" type="submit" disabled={busy}>
              {busy ? '注册中…' : '注 册'}
            </button>
          </form>

          <p className="auth-switch">
            已有账号？<Link className="pixel-link" to="/login">去登录</Link>
          </p>
        </div>
      </section>
    </div>
  );
}
