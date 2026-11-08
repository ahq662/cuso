const { Server } = require('socket.io');
const jwt = require('jsonwebtoken');
const db = require('./db');
const config = require('./config');

// channelId -> Map(socketId -> userId)
// 只存「谁在房间里」；昵称/头像在广播时实时查库，保证资料修改后立即生效
const presence = new Map();

// channelId -> Set(userId)：被管理员强制闭麦的成员（需管理员解除）
const forceMuted = new Map();

// userId -> Set(socket)：便于向某个用户的所有连接推送
const userSockets = new Map();

function isForceMuted(channelId, userId) {
  const set = forceMuted.get(channelId);
  return !!(set && set.has(userId));
}

function memberList(channelId) {
  const map = presence.get(channelId);
  if (!map || map.size === 0) return [];
  const userIds = Array.from(new Set(map.values()));
  const placeholders = userIds.map(() => '?').join(',');
  const rows = db
    .prepare(
      'SELECT id AS userId, username, nickname, avatar_url AS avatarUrl FROM users WHERE id IN (' + placeholders + ')'
    )
    .all(...userIds);
  return rows
    .sort((a, b) => a.userId - b.userId)
    .map((r) => ({ ...r, muted: isForceMuted(channelId, r.userId) }));
}

function broadcastPresence(io, channelId) {
  io.to('channel:' + channelId).emit('presence:update', {
    channelId: Number(channelId),
    members: memberList(channelId),
  });
}

function emitToUser(io, userId, event, payload) {
  io.to('user:' + userId).emit(event, payload);
}

function removeSocketFromChannel(io, socket, channelId) {
  const wasJoined = !!(socket.data.channels && socket.data.channels.has(channelId));

  const map = presence.get(channelId);
  if (map) {
    map.delete(socket.id);
    if (map.size === 0) presence.delete(channelId);
  }
  if (socket.data.channels) socket.data.channels.delete(channelId);
  socket.leave('channel:' + channelId);

  if (wasJoined) broadcastPresence(io, channelId);
}

/** 把某个用户从频道的在线状态里移除（踢人时用），并清空其强制闭麦状态 */
function removeUserFromChannel(io, channelId, userId) {
  const sockets = userSockets.get(userId);
  const map = presence.get(channelId);

  if (sockets) {
    for (const s of sockets) {
      if (map) map.delete(s.id);
      s.leave('channel:' + channelId);
      if (s.data.channels) s.data.channels.delete(channelId);
    }
  }
  if (map && map.size === 0) presence.delete(channelId);

  const muted = forceMuted.get(channelId);
  if (muted) {
    muted.delete(userId);
    if (muted.size === 0) forceMuted.delete(channelId);
  }

  broadcastPresence(io, channelId);
}

/** 频道被删除时清理全部在线状态 */
function clearChannelState(io, channelId) {
  const map = presence.get(channelId);
  if (map) {
    for (const socketId of Array.from(map.keys())) {
      const s = io.sockets.sockets.get(socketId);
      if (s) {
        s.leave('channel:' + channelId);
        if (s.data.channels) s.data.channels.delete(channelId);
      }
    }
    presence.delete(channelId);
  }
  forceMuted.delete(channelId);
}

function initSocket(httpServer) {
  const io = new Server(httpServer, { cors: { origin: '*' } });

  // 握手鉴权：socket.handshake.auth.token = JWT
  io.use((socket, next) => {
    try {
      const token = socket.handshake.auth && socket.handshake.auth.token;
      if (!token) return next(new Error('未提供 Token'));

      const payload = jwt.verify(token, config.jwtSecret);
      const userId = Number(payload.sub);
      if (!Number.isInteger(userId) || userId <= 0) return next(new Error('Token 无效'));

      const user = db.prepare('SELECT id FROM users WHERE id = ?').get(userId);
      if (!user) return next(new Error('用户不存在'));

      socket.data.userId = userId;
      socket.data.channels = new Set();
      return next();
    } catch (e) {
      return next(new Error('Token 无效或已过期'));
    }
  });

  io.on('connection', (socket) => {
    // 个人房间：便于按用户推送（踢人 / 强制闭麦）
    socket.join('user:' + socket.data.userId);
    if (!userSockets.has(socket.data.userId)) userSockets.set(socket.data.userId, new Set());
    userSockets.get(socket.data.userId).add(socket);

    socket.on('channel:join', (payload) => {
      const channelId = Number(payload && payload.channelId);
      if (!Number.isInteger(channelId) || channelId <= 0) {
        return socket.emit('presence:error', { error: 'channelId 非法' });
      }

      const member = db
        .prepare('SELECT id FROM channel_members WHERE channel_id = ? AND user_id = ?')
        .get(channelId, socket.data.userId);
      if (!member) {
        return socket.emit('presence:error', { error: '你不是该频道成员，请先加入频道' });
      }

      socket.join('channel:' + channelId);
      socket.data.channels.add(channelId);
      if (!presence.has(channelId)) presence.set(channelId, new Map());
      presence.get(channelId).set(socket.id, socket.data.userId);
      broadcastPresence(io, channelId);

      // 如果已被管理员强制闭麦，进入房间时立即下发
      if (isForceMuted(channelId, socket.data.userId)) {
        socket.emit('force:mute', { channelId, muted: true });
      }
    });

    socket.on('channel:leave', (payload) => {
      const channelId = Number(payload && payload.channelId);
      if (!Number.isInteger(channelId) || channelId <= 0) return;
      removeSocketFromChannel(io, socket, channelId);
    });

    // 管理员（频道创建者）强制闭麦 / 解除
    socket.on('admin:mute', (payload) => {
      const channelId = Number(payload && payload.channelId);
      const targetUserId = Number(payload && payload.userId);
      const muted = !!(payload && payload.muted);

      if (
        !Number.isInteger(channelId) ||
        channelId <= 0 ||
        !Number.isInteger(targetUserId) ||
        targetUserId <= 0
      ) {
        return socket.emit('presence:error', { error: '参数非法' });
      }

      const channel = db.prepare('SELECT creator_id FROM channels WHERE id = ?').get(channelId);
      if (!channel) return socket.emit('presence:error', { error: '频道不存在' });
      if (channel.creator_id !== socket.data.userId) {
        return socket.emit('presence:error', { error: '只有创建者可以闭麦' });
      }
      if (targetUserId === socket.data.userId) {
        return socket.emit('presence:error', { error: '不能闭麦自己' });
      }

      const member = db
        .prepare('SELECT id FROM channel_members WHERE channel_id = ? AND user_id = ?')
        .get(channelId, targetUserId);
      if (!member) return socket.emit('presence:error', { error: '该用户不在频道中' });

      if (!forceMuted.has(channelId)) forceMuted.set(channelId, new Set());
      if (muted) forceMuted.get(channelId).add(targetUserId);
      else forceMuted.get(channelId).delete(targetUserId);

      emitToUser(io, targetUserId, 'force:mute', {
        channelId,
        muted,
        byUserId: socket.data.userId,
      });
      broadcastPresence(io, channelId);
      socket.emit('admin:ok', { action: 'mute', channelId, userId: targetUserId, muted });
    });

    socket.on('disconnect', () => {
      const set = userSockets.get(socket.data.userId);
      if (set) {
        set.delete(socket);
        if (set.size === 0) userSockets.delete(socket.data.userId);
      }
      for (const channelId of Array.from(socket.data.channels || [])) {
        removeSocketFromChannel(io, socket, channelId);
      }
    });
  });

  return io;
}

module.exports = {
  initSocket,
  memberList,
  emitToUser,
  removeUserFromChannel,
  clearChannelState,
};
