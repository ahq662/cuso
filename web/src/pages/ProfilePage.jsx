import { useRef, useState } from 'react';
import { api, assetUrl } from '../api';
import { useAuth } from '../auth/AuthContext';
import PixelSheep from '../components/PixelSheep';

export default function ProfilePage() {
  const { user, logout, updateUser } = useAuth();

  const [nickname, setNickname] = useState(user ? user.nickname : '');
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [busy, setBusy] = useState(false);
  const fileRef = useRef(null);

  if (!user) return null;

  const joinDate = user.createdAt
    ? new Date(user.createdAt * 1000).toLocaleDateString('zh-CN')
    : '—';

  async function saveNickname(e) {
    e.preventDefault();
    setError('');
    setOk('');

    const n = nickname.trim();
    if (n.length < 1 || n.length > 20) {
      setError('昵称需为 1-20 个字符');
      return;
    }

    setBusy(true);
    try {
      const data = await api.updateProfile({ nickname: n });
      updateUser(data.user);
      setOk('昵称已保存');
    } catch (err) {
      setError(err.message || '保存失败');
    } finally {
      setBusy(false);
    }
  }

  async function onPickAvatar(e) {
    const file = e.target.files && e.target.files[0];
    if (!file) return;

    setError('');
    setOk('');

    if (!/^image\//.test(file.type)) {
      setError('请选择图片文件（png/jpg/webp/gif）');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setError('图片不能超过 10MB');
      return;
    }

    setBusy(true);
    try {
      const data = await api.uploadAvatar(file);
      updateUser(data.user);
      setOk('头像已更新');
    } catch (err) {
      setError(err.message || '上传失败');
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  }

  return (
    <div className="page profile-page">
      <div className="profile-banner">
        <div>
          <span className="profile-banner-tag">MY PROFILE</span>
          <h2 className="profile-banner-title">个人资料</h2>
        </div>
        <div className="profile-banner-sheep">
          <PixelSheep width={88} />
        </div>
      </div>

      <div className="pixel-card profile-card">
        <div className="profile-head">
          <div className="avatar-box">
            <div className="avatar-frame">
              {user.avatarUrl ? (
                <img className="pixel-avatar" src={assetUrl(user.avatarUrl)} alt="我的头像" />
              ) : (
                <div className="pixel-avatar avatar-placeholder">
                  {(user.nickname || user.username || '?').slice(0, 1)}
                </div>
              )}
            </div>
            <button
              className="pixel-btn pixel-btn-sm"
              type="button"
              onClick={() => fileRef.current && fileRef.current.click()}
              disabled={busy}
            >
              更换头像
            </button>
            <input
              ref={fileRef}
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif"
              className="hidden-file"
              onChange={onPickAvatar}
            />
          </div>

          <div className="profile-meta">
            <div className="profile-name">{user.nickname || user.username}</div>
            <div className="profile-username">@{user.username}</div>
            <div className="profile-chips">
              <span className="pixel-chip">ID {user.id}</span>
              <span className="pixel-chip">加入于 {joinDate}</span>
            </div>
          </div>
        </div>

        <div className="profile-divider" />

        <form onSubmit={saveNickname} className="form">
          <label className="field">
            <span className="field-label">昵称</span>
            <input
              className="pixel-input"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              maxLength={20}
              placeholder="1-20 个字符"
            />
          </label>

          {error ? (
            <div className="pixel-alert" role="alert">
              {error}
            </div>
          ) : null}
          {ok ? (
            <div className="pixel-alert pixel-alert-ok" role="status">
              {ok}
            </div>
          ) : null}

          <button className="pixel-btn pixel-btn-block" type="submit" disabled={busy}>
            {busy ? '保存中…' : '保 存'}
          </button>
        </form>

        <button className="pixel-btn pixel-btn-danger pixel-btn-block" type="button" onClick={logout}>
          退出登录
        </button>
      </div>
    </div>
  );
}
