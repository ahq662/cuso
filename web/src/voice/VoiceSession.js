import { api } from '../api';

function micErrorReason(e) {
  if (!e) return '未知错误';
  if (e.name === 'NotAllowedError') return '麦克风权限被拒绝';
  if (e.name === 'NotFoundError') return '未检测到麦克风';
  if (e.name === 'NotReadableError') return '麦克风被其他程序占用';
  return e.message || '未知错误';
}

function emptyState() {
  return {
    channel: null,
    members: [],
    levels: {},
    joined: false,
    selfMuted: false,
    forceMuted: false,
    volumes: {},
    error: '',
    notice: '',
    micError: '',
  };
}

export class VoiceSession {
  constructor() {
    this.socket = null;
    this.user = null;
    this.state = emptyState();
    this.listeners = new Set();
    this.client = null;
    this.localTrack = null;
    this.remoteUsers = new Map();
    this.presenceBound = false;
    this.pointerHandler = null;
    this.subscribe = this.subscribe.bind(this);
    this.getSnapshot = this.getSnapshot.bind(this);
  }

  subscribe(cb) {
    this.listeners.add(cb);
    return () => this.listeners.delete(cb);
  }

  getSnapshot() {
    return this.state;
  }

  emit() {
    this.listeners.forEach((cb) => cb());
  }

  patch(patch) {
    this.state = { ...this.state, ...patch };
    this.emit();
  }

  setSocket(socket) {
    if (this.socket === socket) return;
    this.unbindPresence();
    this.socket = socket;
    this.bindPresence();
    if (socket && this.state.channel) {
      socket.emit('channel:join', { channelId: this.state.channel.id });
    }
  }

  setUser(user) {
    this.user = user;
    if (!user) this.leave();
  }

  bindPresence() {
    const socket = this.socket;
    if (!socket || this.presenceBound) return;
    this.presenceBound = true;

    this.onPresence = (payload) => {
      if (!this.state.channel || Number(payload.channelId) !== this.state.channel.id) return;
      this.patch({ members: payload.members || [] });
    };
    this.onForceMute = (payload) => {
      if (!this.state.channel || Number(payload.channelId) !== this.state.channel.id) return;
      const forceMuted = !!payload.muted;
      this.patch({
        forceMuted,
        notice: forceMuted ? '你已被管理员闭麦' : '管理员已解除你的闭麦',
      });
      this.applyMute();
    };
    this.onPresenceError = (payload) => {
      if (payload && payload.error) this.patch({ error: payload.error });
    };
    this.onKicked = (payload) => {
      if (!this.state.channel || Number(payload.channelId) !== this.state.channel.id) return;
      this.leave();
      this.patch({ notice: '你已被移出频道' });
    };
    this.onDeleted = (payload) => {
      if (!this.state.channel || Number(payload.channelId) !== this.state.channel.id) return;
      this.leave();
      this.patch({ error: '频道已被删除' });
    };
    this.onConnect = () => {
      if (this.socket && this.state.channel) {
        this.socket.emit('channel:join', { channelId: this.state.channel.id });
      }
    };

    socket.on('presence:update', this.onPresence);
    socket.on('force:mute', this.onForceMute);
    socket.on('presence:error', this.onPresenceError);
    socket.on('member:kicked', this.onKicked);
    socket.on('channel:deleted', this.onDeleted);
    socket.on('connect', this.onConnect);
  }

  unbindPresence() {
    const socket = this.socket;
    if (!socket || !this.presenceBound) return;
    socket.off('presence:update', this.onPresence);
    socket.off('force:mute', this.onForceMute);
    socket.off('presence:error', this.onPresenceError);
    socket.off('member:kicked', this.onKicked);
    socket.off('channel:deleted', this.onDeleted);
    socket.off('connect', this.onConnect);
    this.presenceBound = false;
  }

  async join(channel) {
    if (!channel) return;
    if (this.state.channel && this.state.channel.id === channel.id) {
      this.patch({ channel });
      return;
    }
    if (this.state.channel) await this.leave();

    this.patch({
      ...emptyState(),
      channel,
    });
    this.bindPresence();
    if (this.socket) this.socket.emit('channel:join', { channelId: channel.id });
    this.addPointerRetry();

    try {
      const tokenData = await api.rtcToken(channel.id);
      if (!this.state.channel || this.state.channel.id !== channel.id) return;

      if (!this.agora) {
        const mod = await import('agora-rtc-sdk-ng');
        this.agora = mod.default;
      }

      const client = this.agora.createClient({ mode: 'rtc', codec: 'vp8' });
      this.client = client;
      this.bindClient(client, channel.id);

      await client.join(tokenData.appId, tokenData.channelName, tokenData.token, tokenData.uid);
      if (!this.state.channel || this.state.channel.id !== channel.id) {
        try {
          await client.leave();
        } catch (e) {
          // ignore
        }
        return;
      }

      client.enableAudioVolumeIndicator();
      this.patch({ joined: true });
      await this.createMic();
    } catch (e) {
      this.patch({ error: (e && e.message) || '语音连接失败' });
    }
  }

  bindClient(client, channelId) {
    client.on('user-published', async (remoteUser, mediaType) => {
      if (mediaType !== 'audio') return;
      try {
        await client.subscribe(remoteUser, 'audio').catch(() => {});
      } catch (e) {
        // ignore
      }

      const uid = Number(remoteUser.uid);
      this.remoteUsers.set(uid, remoteUser);

      try {
        if (remoteUser.audioTrack) remoteUser.audioTrack.play();
      } catch (e) {
        this.patch({ notice: '浏览器阻止了自动播放，请点击页面后再试' });
      }

      const saved = this.state.volumes[uid];
      if (typeof saved === 'number' && remoteUser.audioTrack) {
        remoteUser.audioTrack.setVolume(saved);
      }
    });

    client.on('user-unpublished', (remoteUser) => {
      this.remoteUsers.delete(Number(remoteUser.uid));
    });
    client.on('user-left', (remoteUser) => {
      this.remoteUsers.delete(Number(remoteUser.uid));
    });
    client.on('volume-indicator', (vols) => {
      const next = {};
      for (const v of vols) next[Number(v.uid)] = v.level;
      this.patch({ levels: next });
    });
    client.on('token-privilege-will-expire', async () => {
      try {
        const fresh = await api.rtcToken(channelId);
        await client.renewToken(fresh.token);
      } catch (e) {
        // ignore
      }
    });
  }

  async createMic() {
    const client = this.client;
    if (!client || this.localTrack || !this.agora) return;
    try {
      const track = await this.agora.createMicrophoneAudioTrack({
        AEC: true,
        AGC: true,
        ANS: true,
      });
      if (!this.client || this.client !== client) {
        try {
          track.close();
        } catch (e) {
          // ignore
        }
        return;
      }
      this.localTrack = track;
      await client.publish([track]);
      this.applyMute();
      this.patch({ micError: '', notice: '' });
    } catch (e) {
      this.patch({
        micError: micErrorReason(e),
        notice: '麦克风不可用，当前为仅收听模式',
      });
    }
  }

  enableMic() {
    return this.createMic();
  }

  applyMute() {
    const muted = this.state.forceMuted || this.state.selfMuted;
    if (this.localTrack) this.localTrack.setMuted(muted).catch(() => {});
  }

  toggleMic() {
    if (this.state.forceMuted) {
      this.patch({ notice: '你已被管理员闭麦，需管理员解除' });
      return;
    }
    if (!this.localTrack) {
      this.patch({ notice: this.state.joined ? '麦克风不可用' : '麦克风准备中，请稍候' });
      return;
    }
    const selfMuted = !this.state.selfMuted;
    this.patch({ selfMuted });
    this.applyMute();
  }

  setUserVolume(userId, value) {
    const v = Math.max(0, Math.min(100, Number(value)));
    this.patch({ volumes: { ...this.state.volumes, [userId]: v } });
    const remote = this.remoteUsers.get(Number(userId));
    if (remote && remote.audioTrack) remote.audioTrack.setVolume(v);
  }

  adminToggleMute(member) {
    if (!this.socket || !this.state.channel) return;
    this.socket.emit('admin:mute', {
      channelId: this.state.channel.id,
      userId: member.userId,
      muted: !member.muted,
    });
  }

  retryPlay() {
    this.remoteUsers.forEach((u) => {
      try {
        if (u.audioTrack) u.audioTrack.play();
      } catch (e) {
        // ignore
      }
    });
  }

  addPointerRetry() {
    if (this.pointerHandler) return;
    this.pointerHandler = () => this.retryPlay();
    document.addEventListener('pointerdown', this.pointerHandler);
  }

  removePointerRetry() {
    if (this.pointerHandler) document.removeEventListener('pointerdown', this.pointerHandler);
    this.pointerHandler = null;
  }

  async leave() {
    const channel = this.state.channel;
    if (this.socket && channel) {
      this.socket.emit('channel:leave', { channelId: channel.id });
    }
    this.removePointerRetry();

    const track = this.localTrack;
    this.localTrack = null;
    if (track) {
      try {
        track.close();
      } catch (e) {
        // ignore
      }
    }

    const client = this.client;
    this.client = null;
    this.remoteUsers.clear();
    if (client) {
      try {
        client.removeAllListeners();
      } catch (e) {
        // ignore
      }
      try {
        await client.leave();
      } catch (e) {
        // ignore
      }
    }

    this.patch(emptyState());
  }
}
