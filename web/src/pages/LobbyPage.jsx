import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { useSocket } from '../socket/SocketContext';
import { accentFor, accentMap } from '../utils/accents';
import { SignpostScene, CrackedScene } from '../components/PixelScenes';

export default function LobbyPage() {
  const navigate = useNavigate();
  const { socket } = useSocket();

  const [channels, setChannels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [query, setQuery] = useState('');
  const [code, setCode] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [busyCode, setBusyCode] = useState(false);

  const load = useCallback(async () => {
    setError('');
    try {
      const data = await api.listPublic();
      setChannels(data.channels || []);
    } catch (e) {
      setError(e.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // 有人新建/删除频道时，大厅实时刷新
  useEffect(() => {
    if (!socket) return undefined;
    const onCreated = () => load();
    const onDeleted = () => load();
    socket.on('channel:created', onCreated);
    socket.on('channel:deleted', onDeleted);
    return () => {
      socket.off('channel:created', onCreated);
      socket.off('channel:deleted', onDeleted);
    };
  }, [socket, load]);

  async function joinById(id) {
    setBusyId(id);
    setError('');
    setNotice('');
    try {
      const data = await api.joinChannel({ channelId: id });
      setNotice('已加入「' + data.channel.name + '」');
      navigate('/channels/' + id, { state: { accent: colors[id] } });
    } catch (e) {
      setError(e.message || '加入失败');
    } finally {
      setBusyId(null);
    }
  }

  async function joinByCode(e) {
    e.preventDefault();
    const c = code.trim();
    if (!c) {
      setError('请输入邀请码');
      return;
    }

    setBusyCode(true);
    setError('');
    setNotice('');
    try {
      const data = await api.joinChannel({ inviteCode: c });
      setNotice('已加入「' + data.channel.name + '」');
      setCode('');
      navigate('/channels/' + data.channel.id, { state: { accent: accentFor(data.channel.id) } });
    } catch (err) {
      setError(err.message || '加入失败');
    } finally {
      setBusyCode(false);
    }
  }

  const colors = accentMap(channels);

  const filtered = channels.filter((c) =>
    c.name.toLowerCase().includes(query.trim().toLowerCase())
  );

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h2 className="page-title">大厅</h2>
          <p className="page-sub">所有公开频道 · 共 {channels.length} 个</p>
        </div>
      </header>

      <div className="toolbar">
        <input
          className="pixel-input toolbar-search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="搜索频道名称…"
        />
      </div>

      <form className="join-code-bar" onSubmit={joinByCode}>
        <input
          className="pixel-input"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          placeholder="输入 6 位邀请码"
          maxLength={6}
        />
        <button className="pixel-btn" type="submit" disabled={busyCode}>
          {busyCode ? '加入中…' : '用邀请码加入'}
        </button>
      </form>

      {error ? (
        <div className="pixel-alert" role="alert">
          {error}
        </div>
      ) : null}
      {notice ? (
        <div className="pixel-alert pixel-alert-ok" role="status">
          {notice}
        </div>
      ) : null}

      {loading ? (
        <div className="empty-state">加载中…</div>
      ) : channels.length === 0 ? (
        <div className="empty-state empty-scene">
          <SignpostScene width={230} />
          <p>大厅还没有频道</p>
          <p className="empty-hint">去「我的频道」创建一个吧</p>
        </div>
      ) : filtered.length === 0 ? (
        <div className="empty-state empty-scene">
          <CrackedScene width={200} />
          <p>没有匹配的频道</p>
          <p className="empty-hint">换个关键词试试</p>
        </div>
      ) : (
        <div className="channel-grid">
          {filtered.map((c) => (
            <div
              key={c.id}
              className="channel-card channel-card-static"
              style={{ '--accent': colors[c.id] }}
            >
              <span className="channel-accent" />
              <div className="channel-card-top">
                <span className="channel-name">{c.name}</span>
                {c.isMember ? <span className="badge">已加入</span> : null}
              </div>
              <div className="channel-meta">
                <span className="channel-stat">👥 {c.memberCount}</span>
                <span className="channel-code">{c.inviteCode}</span>
              </div>
              <div className="channel-actions">
                {c.isMember ? (
                  <button
                    className="pixel-btn pixel-btn-sm"
                    type="button"
                    onClick={() => navigate('/channels/' + c.id, { state: { accent: colors[c.id] } })}
                  >
                    打开
                  </button>
                ) : (
                  <button
                    className="pixel-btn pixel-btn-sm"
                    type="button"
                    disabled={busyId === c.id}
                    onClick={() => joinById(c.id)}
                  >
                    {busyId === c.id ? '加入中…' : '加入'}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
