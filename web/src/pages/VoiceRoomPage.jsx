import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, assetUrl } from '../api';
import { useAuth } from '../auth/AuthContext';
import { useSocket } from '../socket/SocketContext';
import { useVoice } from '../voice/VoiceContext';
import { CrackedScene } from '../components/PixelScenes';

const SPEAK_THRESHOLD = 8;

export default function VoiceRoomPage() {
  const { id } = useParams();
  const channelId = Number(id);
  const navigate = useNavigate();
  const { user } = useAuth();
  const { socket } = useSocket();
  const {
    channel: activeChannel,
    members,
    levels,
    selfMuted,
    forceMuted,
    volumes,
    joined,
    error: voiceError,
    notice,
    micError,
    join,
    leave,
    toggleMic,
    enableMic,
    setUserVolume,
    adminToggleMute,
  } = useVoice();

  const [channel, setChannel] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [selectedUser, setSelectedUser] = useState(null);

  // 只负责拿频道信息并让全局会话加入，不负责离开
  useEffect(() => {
    let cancelled = false;
    setLoadError('');
    api
      .channelDetail(channelId)
      .then((data) => {
        if (cancelled) return;
        setChannel(data.channel);
        if (data.channel.isMember) join(data.channel);
      })
      .catch((e) => {
        if (!cancelled) setLoadError(e.message || '加载频道失败');
      });
    return () => {
      cancelled = true;
    };
  }, [channelId, join]);

  useEffect(() => {
    if (!socket) return undefined;
    const onDeleted = (payload) => {
      if (payload && Number(payload.channelId) === channelId) {
        navigate('/channels', { replace: true });
      }
    };
    socket.on('channel:deleted', onDeleted);
    return () => socket.off('channel:deleted', onDeleted);
  }, [socket, channelId, navigate]);

  const displayChannel = channel || activeChannel;
  const canEnter = !!(displayChannel && displayChannel.isMember);
  const muted = forceMuted || selfMuted;
  const isCreator = !!(displayChannel && user && displayChannel.creatorId === user.id);
  const error = loadError || voiceError;

  function exitVoice() {
    leave();
    navigate('/channels/' + channelId);
  }

  return (
    <div className="page voice-page">
      <header className="page-head">
        <div>
          <button className="back-link" type="button" onClick={() => navigate('/channels/' + channelId)}>
            ← 返回频道
          </button>
          <h2 className="page-title">
            {displayChannel ? displayChannel.name : '语音房'} <span className="voice-live">语音中</span>
          </h2>
          <p className="page-sub">
            {members.length} 人在线 {joined ? '· 已连接语音' : '· 连接中…'}
          </p>
        </div>
        <div className="page-actions">
          <button className="pixel-btn pixel-btn-danger" type="button" onClick={exitVoice}>
            退出语音
          </button>
        </div>
      </header>

      {error ? (
        <div className="pixel-alert" role="alert">
          {error}
        </div>
      ) : null}
      {micError ? (
        <div className="pixel-alert" role="alert">
          麦克风不可用（{micError}），当前为仅收听模式。
          <br />
          请点 Edge 地址栏左侧锁图标 → 麦克风 → 允许，然后
          <button className="pixel-btn pixel-btn-sm" type="button" onClick={enableMic} style={{ marginLeft: 8 }}>
            重试麦克风
          </button>
        </div>
      ) : notice ? (
        <div className="pixel-alert pixel-alert-ok" role="status">
          {notice}
        </div>
      ) : null}

      {!displayChannel && !error ? <div className="empty-state">加载中…</div> : null}
      {displayChannel && !displayChannel.isMember && !error ? (
        <div className="empty-state empty-scene">
          <CrackedScene width={200} />
          <p>你不是该频道成员，无法进入语音</p>
        </div>
      ) : null}

      {canEnter ? (
        <div className="voice-grid">
          {members.map((m) => {
            const isMe = m.userId === user.id;
            const speaking = (levels[m.userId] || 0) > SPEAK_THRESHOLD && !m.muted && !(isMe && selfMuted);
            return (
              <div key={m.userId} className={'voice-member' + (speaking ? ' speaking' : '')}>
                <button
                  className="voice-avatar-btn"
                  type="button"
                  onClick={() => !isMe && setSelectedUser(m)}
                  title={isMe ? '这是你' : '点击调节对方音量'}
                >
                  {m.avatarUrl ? (
                    <img className="voice-avatar" src={assetUrl(m.avatarUrl)} alt="" />
                  ) : (
                    <span className="voice-avatar voice-avatar-text">
                      {(m.nickname || m.username || '?').slice(0, 1)}
                    </span>
                  )}
                </button>

                <div className="voice-name">
                  {m.nickname || m.username}
                  {isMe ? '（你）' : ''}
                </div>

                <div className="voice-flags">
                  {m.muted ? <span className="voice-flag">🔇 已闭麦</span> : null}
                  {speaking ? <span className="voice-flag voice-flag-speaking">说话中</span> : null}
                </div>

                {isCreator && !isMe ? (
                  <button
                    className="pixel-btn pixel-btn-sm pixel-btn-ghost voice-admin-btn"
                    type="button"
                    onClick={() => adminToggleMute(m)}
                  >
                    {m.muted ? '解除闭麦' : '闭麦'}
                  </button>
                ) : null}
              </div>
            );
          })}
        </div>
      ) : null}

      {selectedUser ? (
        <div className="pixel-card volume-panel">
          <div className="volume-head">
            <span>{selectedUser.nickname || selectedUser.username} 的音量</span>
            <button className="modal-close" type="button" onClick={() => setSelectedUser(null)} aria-label="关闭">
              ✕
            </button>
          </div>
          <div className="volume-row">
            <input
              className="volume-range"
              type="range"
              min="0"
              max="100"
              value={volumes[selectedUser.userId] ?? 100}
              onChange={(e) => setUserVolume(selectedUser.userId, e.target.value)}
            />
            <span className="volume-value">{volumes[selectedUser.userId] ?? 100}%</span>
          </div>
          <div className="volume-actions">
            <button
              className="pixel-btn pixel-btn-sm"
              type="button"
              onClick={() => setUserVolume(selectedUser.userId, 0)}
            >
              单独静音
            </button>
            <button
              className="pixel-btn pixel-btn-sm pixel-btn-ghost"
              type="button"
              onClick={() => setUserVolume(selectedUser.userId, 100)}
            >
              恢复 100%
            </button>
          </div>
        </div>
      ) : null}

      <div className="voice-controls">
        <button
          className={'pixel-btn voice-mic-btn' + (muted ? ' pixel-btn-danger' : '')}
          type="button"
          onClick={toggleMic}
          disabled={forceMuted}
        >
          {forceMuted ? '🔇 已被管理员闭麦' : muted ? '🔇 开麦' : '🎤 闭麦'}
        </button>
        <span className="voice-hint-inline">
          {forceMuted ? '需管理员解除才能开麦' : muted ? '你已闭麦' : '麦克风开启中'}
        </span>
        <span className="voice-hint-inline">降噪：开启</span>
      </div>
    </div>
  );
}
