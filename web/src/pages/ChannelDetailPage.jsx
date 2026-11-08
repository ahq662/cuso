import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { api, assetUrl } from '../api';
import { useAuth } from '../auth/AuthContext';
import { useSocket } from '../socket/SocketContext';
import { copyText } from '../utils/clipboard';
import { accentFor } from '../utils/accents';
import { CrackedScene } from '../components/PixelScenes';
import Modal from '../components/Modal';

export default function ChannelDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const { socket } = useSocket();

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const [confirmAction, setConfirmAction] = useState(null); // 'leave' | 'delete' | null
  const [kickTarget, setKickTarget] = useState(null);
  const inviteRef = useRef(null);

  const load = useCallback(async () => {
    setError('');
    try {
      const res = await api.channelDetail(id);
      setData(res);
    } catch (e) {
      setError(e.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  // 当前频道被删除时，自动退回频道列表
  useEffect(() => {
    if (!socket) return undefined;
    const onDeleted = (payload) => {
      if (payload && Number(payload.channelId) === Number(id)) {
        navigate('/channels', { replace: true });
      }
    };
    socket.on('channel:deleted', onDeleted);
    return () => socket.off('channel:deleted', onDeleted);
  }, [socket, id, navigate]);

  async function doLeave() {
    setBusy(true);
    setError('');
    try {
      await api.leaveChannel(id);
      navigate('/channels', { replace: true });
    } catch (e) {
      setError(e.message || '退出失败');
      setConfirmAction(null);
    } finally {
      setBusy(false);
    }
  }

  async function doDelete() {
    setBusy(true);
    setError('');
    try {
      await api.deleteChannel(id);
      navigate('/channels', { replace: true });
    } catch (e) {
      setError(e.message || '删除失败');
      setConfirmAction(null);
    } finally {
      setBusy(false);
    }
  }

  function runConfirm() {
    if (confirmAction === 'leave') doLeave();
    else if (confirmAction === 'delete') doDelete();
  }

  async function doKick() {
    if (!kickTarget) return;
    setBusy(true);
    setError('');
    try {
      await api.kickMember(id, kickTarget.userId);
      setKickTarget(null);
      await load();
    } catch (e) {
      setError(e.message || '踢出失败');
      setKickTarget(null);
    } finally {
      setBusy(false);
    }
  }

  async function onCopy() {
    const ok = await copyText(data.channel.inviteCode);
    if (ok) {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
      return;
    }

    const el = inviteRef.current;
    if (el) {
      const range = document.createRange();
      range.selectNodeContents(el);
      const sel = window.getSelection();
      if (sel) {
        sel.removeAllRanges();
        sel.addRange(range);
      }
    }
    setError('复制失败，已为你选中邀请码，请按 Ctrl+C 复制');
  }

  if (loading) {
    return (
      <div className="page">
        <div className="empty-state">加载中…</div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="page">
        <div className="empty-state empty-scene">
          <CrackedScene width={220} />
          <p>{error || '频道不存在'}</p>
        </div>
      </div>
    );
  }

  const { channel, members, creator } = data;
  const isCreator = channel.creatorId === user.id;
  const creatorLabel = creator ? creator.nickname || creator.username : '未知';
  const accent = (location.state && location.state.accent) || accentFor(channel.id);

  return (
    <div className="page">
      <div className="channel-hero" style={{ '--accent': accent }}>
        <span className="channel-hero-accent" />

        <div className="channel-hero-info">
          <button className="back-link" type="button" onClick={() => navigate(-1)}>
            ← 返回
          </button>
          <h2 className="channel-hero-title">{channel.name}</h2>
          <p className="channel-hero-sub">
            {members.length} 位成员 · 创建者：{creatorLabel}
            {isCreator ? '（你）' : ''}
          </p>
          <div className="channel-hero-tags">
            <span className="pixel-chip">
              邀请码 <span className="invite-code" ref={inviteRef}>{channel.inviteCode}</span>
            </span>
            <button className="pixel-btn pixel-btn-sm pixel-btn-ghost" type="button" onClick={onCopy}>
              {copied ? '已复制' : '复制'}
            </button>
            <span className="pixel-chip">ID {channel.id}</span>
          </div>
        </div>

        <div className="channel-hero-actions">
          {channel.isMember ? (
            <button
              className="pixel-btn channel-enter-btn"
              type="button"
              onClick={() => navigate('/channels/' + channel.id + '/voice')}
            >
              🎙 进入语音
            </button>
          ) : null}
          {channel.isMember ? (
            <button
              className="pixel-btn pixel-btn-ghost"
              type="button"
              onClick={() => setConfirmAction('leave')}
              disabled={busy}
            >
              退出频道
            </button>
          ) : null}
          {isCreator ? (
            <button
              className="pixel-btn pixel-btn-danger"
              type="button"
              onClick={() => setConfirmAction('delete')}
              disabled={busy}
            >
              删除频道
            </button>
          ) : (
            <span className="action-hint">只有创建者可删除</span>
          )}
        </div>
      </div>

      {error ? (
        <div className="pixel-alert" role="alert">
          {error}
        </div>
      ) : null}

      <h3 className="section-title">成员列表</h3>
      <div className="member-grid">
        {members.map((m) => (
          <div key={m.userId} className="member-item">
            {m.avatarUrl ? (
              <img className="member-avatar" src={assetUrl(m.avatarUrl)} alt="" />
            ) : (
              <span className="member-avatar member-avatar-text">
                {(m.nickname || m.username || '?').slice(0, 1)}
              </span>
            )}
            <span className="member-name">{m.nickname || m.username}</span>
            {m.userId === channel.creatorId ? <span className="member-badge">创建者</span> : null}
            {isCreator && m.userId !== user.id ? (
              <button
                className="pixel-btn pixel-btn-sm pixel-btn-danger member-kick"
                type="button"
                onClick={() => setKickTarget(m)}
              >
                踢出
              </button>
            ) : null}
          </div>
        ))}
      </div>

      <div className="voice-hint">🎙 语音已接入：点「进入语音」开始通话</div>

      {confirmAction ? (
        <Modal
          title={confirmAction === 'delete' ? '删除频道' : '退出频道'}
          onClose={() => {
            if (!busy) setConfirmAction(null);
          }}
        >
          <p className="confirm-text">
            {confirmAction === 'delete'
              ? '确定删除该频道吗？此操作不可恢复，所有成员都会被移出。'
              : '确定退出该频道吗？退出后可以再用邀请码重新加入。'}
          </p>
          <div className="confirm-actions">
            <button
              className="pixel-btn pixel-btn-ghost"
              type="button"
              onClick={() => setConfirmAction(null)}
              disabled={busy}
            >
              取消
            </button>
            <button
              className={'pixel-btn' + (confirmAction === 'delete' ? ' pixel-btn-danger' : '')}
              type="button"
              onClick={runConfirm}
              disabled={busy}
            >
              {busy ? '处理中…' : '确定'}
            </button>
          </div>
        </Modal>
      ) : null}

      {kickTarget ? (
        <Modal
          title="踢出成员"
          onClose={() => {
            if (!busy) setKickTarget(null);
          }}
        >
          <p className="confirm-text">
            确定将「{kickTarget.nickname || kickTarget.username}」踢出该频道吗？对方需要邀请码才能重新加入。
          </p>
          <div className="confirm-actions">
            <button
              className="pixel-btn pixel-btn-ghost"
              type="button"
              onClick={() => setKickTarget(null)}
              disabled={busy}
            >
              取消
            </button>
            <button className="pixel-btn pixel-btn-danger" type="button" onClick={doKick} disabled={busy}>
              {busy ? '处理中…' : '确定'}
            </button>
          </div>
        </Modal>
      ) : null}
    </div>
  );
}
