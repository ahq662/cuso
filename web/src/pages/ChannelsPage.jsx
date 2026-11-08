import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import Modal from '../components/Modal';
import { useSocket } from '../socket/SocketContext';
import { accentAt, accentMap } from '../utils/accents';
import { CabinScene, CrackedScene } from '../components/PixelScenes';

export default function ChannelsPage() {
  const navigate = useNavigate();
  const { socket } = useSocket();
  const [channels, setChannels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setError('');
    try {
      const data = await api.listMine();
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

  // 频道被删除时实时刷新「我的频道」
  useEffect(() => {
    if (!socket) return undefined;
    const onDeleted = () => load();
    socket.on('channel:deleted', onDeleted);
    return () => socket.off('channel:deleted', onDeleted);
  }, [socket, load]);

  async function onCreate(e) {
    e.preventDefault();
    const n = name.trim();
    if (!n) {
      setError('请输入频道名称');
      return;
    }
    if (n.length > 20) {
      setError('频道名称不能超过 20 个字符');
      return;
    }

    setBusy(true);
    setError('');
    try {
      const data = await api.createChannel(n);
      setCreateOpen(false);
      setName('');
      navigate('/channels/' + data.channel.id, { state: { accent: accentAt(0) } });
    } catch (err) {
      setError(err.message || '创建失败');
    } finally {
      setBusy(false);
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
          <h2 className="page-title">我的频道</h2>
          <p className="page-sub">你加入的固定群组 · 共 {channels.length} 个</p>
        </div>
        <button
          className="pixel-btn"
          type="button"
          onClick={() => {
            setError('');
            setCreateOpen(true);
          }}
        >
          ＋ 创建频道
        </button>
      </header>

      {channels.length > 0 ? (
        <div className="toolbar">
          <input
            className="pixel-input toolbar-search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="搜索频道名称…"
          />
        </div>
      ) : null}

      {error && !createOpen ? (
        <div className="pixel-alert" role="alert">
          {error}
        </div>
      ) : null}

      {loading ? (
        <div className="empty-state">加载中…</div>
      ) : channels.length === 0 ? (
        <div className="empty-state">
          <p>还没有加入任何频道</p>
          <p className="empty-hint">点右上角创建，或去「大厅」加入一个</p>
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
            <button
              key={c.id}
              className="channel-card"
              type="button"
              style={{ '--accent': colors[c.id] }}
              onClick={() => navigate('/channels/' + c.id, { state: { accent: colors[c.id] } })}
            >
              <span className="channel-accent" />
              <div className="channel-card-top">
                <span className="channel-name">{c.name}</span>
                <span className="channel-dot" />
              </div>
              <div className="channel-meta">
                <span className="channel-stat">👥 {c.memberCount}</span>
                <span className="channel-code">{c.inviteCode}</span>
              </div>
            </button>
          ))}
        </div>
      )}

      {createOpen ? (
        <Modal title="创建频道" onClose={() => setCreateOpen(false)}>
          <form className="form" onSubmit={onCreate}>
            <label className="field">
              <span className="field-label">频道名称</span>
              <input
                className="pixel-input"
                value={name}
                onChange={(e) => setName(e.target.value)}
                maxLength={20}
                placeholder="1-20 个字符"
                autoFocus
              />
            </label>
            {error ? (
              <div className="pixel-alert" role="alert">
                {error}
              </div>
            ) : null}
            <button className="pixel-btn pixel-btn-block" type="submit" disabled={busy}>
              {busy ? '创建中…' : '创 建'}
            </button>
          </form>
        </Modal>
      ) : null}
    </div>
  );
}
