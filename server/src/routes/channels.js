const express = require('express');
const crypto = require('crypto');
const db = require('../db');
const { requireAuth } = require('../middleware/auth');
const { removeUserFromChannel, emitToUser, clearChannelState } = require('../socket');

const router = express.Router();

// 邀请码字符集：去掉易混淆的 0/O/1/I，共 32 个字符
const INVITE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const INVITE_LENGTH = 6;
const NAME_MAX = 20;

function generateInviteCode() {
  for (let attempt = 0; attempt < 20; attempt++) {
    const bytes = crypto.randomBytes(INVITE_LENGTH);
    let code = '';
    for (let i = 0; i < INVITE_LENGTH; i++) {
      // 256 % 32 === 0，取模无偏
      code += INVITE_ALPHABET[bytes[i] % INVITE_ALPHABET.length];
    }
    const exists = db.prepare('SELECT id FROM channels WHERE invite_code = ?').get(code);
    if (!exists) return code;
  }
  throw new Error('邀请码生成失败，请重试');
}

function countMembers(channelId) {
  return db.prepare('SELECT COUNT(*) AS c FROM channel_members WHERE channel_id = ?').get(channelId).c;
}

function isMemberOf(channelId, userId) {
  const row = db
    .prepare('SELECT id FROM channel_members WHERE channel_id = ? AND user_id = ?')
    .get(channelId, userId);
  return !!row;
}

/** 统一频道 DTO；若 SQL 已带 member_count / is_member 则直接复用，避免 N+1 查询 */
function toChannelDto(row, userId) {
  const memberCount = row.member_count !== undefined ? Number(row.member_count) : countMembers(row.id);
  const isMember = row.is_member !== undefined ? !!row.is_member : isMemberOf(row.id, userId);
  return {
    id: row.id,
    name: row.name,
    inviteCode: row.invite_code,
    creatorId: row.creator_id,
    createdAt: row.created_at,
    memberCount,
    isMember,
  };
}

function parseChannelId(raw) {
  const id = Number(raw);
  if (!Number.isInteger(id) || id <= 0) return null;
  return id;
}

// ---------- POST /api/channels 创建频道 ----------
router.post('/', requireAuth, (req, res) => {
  const name = req.body && typeof req.body.name === 'string' ? req.body.name.trim() : '';

  if (name.length < 1 || name.length > NAME_MAX) {
    return res.status(400).json({ error: `频道名称需为 1-${NAME_MAX} 个字符` });
  }

  const inviteCode = generateInviteCode();

  const create = db.transaction(() => {
    const info = db
      .prepare('INSERT INTO channels (name, invite_code, creator_id) VALUES (?, ?, ?)')
      .run(name, inviteCode, req.userId);
    const channelId = info.lastInsertRowid;
    db.prepare('INSERT INTO channel_members (channel_id, user_id) VALUES (?, ?)').run(channelId, req.userId);
    return channelId;
  });

  const channelId = create();
  const row = db.prepare('SELECT * FROM channels WHERE id = ?').get(channelId);
  const dto = toChannelDto(row, req.userId);

  // 通知所有在线客户端「有新频道」，大厅可据此实时刷新
  const io = req.app.get('io');
  if (io) io.emit('channel:created', { channel: dto });

  return res.status(201).json({ channel: dto });
});

// ---------- GET /api/channels/mine 我加入的频道 ----------
router.get('/mine', requireAuth, (req, res) => {
  const rows = db
    .prepare(`
      SELECT c.*,
             (SELECT COUNT(*) FROM channel_members m2 WHERE m2.channel_id = c.id) AS member_count,
             1 AS is_member
      FROM channels c
      JOIN channel_members m ON m.channel_id = c.id AND m.user_id = ?
      ORDER BY m.joined_at DESC, c.id DESC
    `)
    .all(req.userId);

  return res.json({ channels: rows.map((r) => toChannelDto(r, req.userId)) });
});

// ---------- GET /api/channels/public 公开大厅 ----------
router.get('/public', requireAuth, (req, res) => {
  const rows = db
    .prepare(`
      SELECT c.*,
             (SELECT COUNT(*) FROM channel_members m2 WHERE m2.channel_id = c.id) AS member_count,
             EXISTS(SELECT 1 FROM channel_members m3 WHERE m3.channel_id = c.id AND m3.user_id = ?) AS is_member
      FROM channels c
      ORDER BY c.created_at DESC, c.id DESC
    `)
    .all(req.userId);

  return res.json({ channels: rows.map((r) => toChannelDto(r, req.userId)) });
});

// ---------- POST /api/channels/join 加入频道 ----------
router.post('/join', requireAuth, (req, res) => {
  const body = req.body || {};
  let row = null;

  if (typeof body.inviteCode === 'string' && body.inviteCode.trim()) {
    const code = body.inviteCode.trim().toUpperCase();
    row = db.prepare('SELECT * FROM channels WHERE invite_code = ?').get(code);
  } else if (body.channelId !== undefined) {
    const id = parseChannelId(body.channelId);
    if (!id) return res.status(400).json({ error: '频道 id 非法' });
    row = db.prepare('SELECT * FROM channels WHERE id = ?').get(id);
  } else {
    return res.status(400).json({ error: '请提供 inviteCode 或 channelId' });
  }

  if (!row) return res.status(404).json({ error: '频道不存在' });

  if (isMemberOf(row.id, req.userId)) {
    return res.status(409).json({ error: '你已经在该频道中' });
  }

  try {
    db.prepare('INSERT INTO channel_members (channel_id, user_id) VALUES (?, ?)').run(row.id, req.userId);
  } catch (e) {
    // 并发下两人同时加入，先到的成功、后到的命中唯一约束 → 返回 409
    if (e && e.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      return res.status(409).json({ error: '你已经在该频道中' });
    }
    throw e;
  }
  return res.json({ channel: toChannelDto(row, req.userId) });
});

// ---------- GET /api/channels/:id 频道详情 ----------
router.get('/:id', requireAuth, (req, res) => {
  const id = parseChannelId(req.params.id);
  if (!id) return res.status(400).json({ error: '频道 id 非法' });

  const row = db.prepare('SELECT * FROM channels WHERE id = ?').get(id);
  if (!row) return res.status(404).json({ error: '频道不存在' });

  const creator = db
    .prepare('SELECT id AS userId, username, nickname, avatar_url AS avatarUrl FROM users WHERE id = ?')
    .get(row.creator_id);

  const members = db
    .prepare(`
      SELECT u.id AS userId, u.username, u.nickname, u.avatar_url AS avatarUrl, m.joined_at AS joinedAt
      FROM channel_members m
      JOIN users u ON u.id = m.user_id
      WHERE m.channel_id = ?
      ORDER BY m.joined_at ASC
    `)
    .all(id);

  return res.json({ channel: toChannelDto(row, req.userId), members, creator: creator || null });
});

// ---------- POST /api/channels/:id/leave 退出频道 ----------
router.post('/:id/leave', requireAuth, (req, res) => {
  const id = parseChannelId(req.params.id);
  if (!id) return res.status(400).json({ error: '频道 id 非法' });

  const row = db.prepare('SELECT id FROM channels WHERE id = ?').get(id);
  if (!row) return res.status(404).json({ error: '频道不存在' });

  if (!isMemberOf(id, req.userId)) {
    return res.status(404).json({ error: '你不在该频道中' });
  }

  db.prepare('DELETE FROM channel_members WHERE channel_id = ? AND user_id = ?').run(id, req.userId);
  return res.json({ ok: true, channelId: id });
});

// ---------- DELETE /api/channels/:id/members/:userId 踢出成员（仅创建者） ----------
router.delete('/:id/members/:userId', requireAuth, (req, res) => {
  const id = parseChannelId(req.params.id);
  const targetUserId = parseChannelId(req.params.userId);
  if (!id || !targetUserId) return res.status(400).json({ error: '参数非法' });

  const row = db.prepare('SELECT * FROM channels WHERE id = ?').get(id);
  if (!row) return res.status(404).json({ error: '频道不存在' });

  if (row.creator_id !== req.userId) {
    return res.status(403).json({ error: '只有创建者可以踢人' });
  }
  if (targetUserId === req.userId) {
    return res.status(400).json({ error: '不能踢出自己' });
  }

  const member = db
    .prepare('SELECT id FROM channel_members WHERE channel_id = ? AND user_id = ?')
    .get(id, targetUserId);
  if (!member) return res.status(404).json({ error: '该用户不在频道中' });

  db.prepare('DELETE FROM channel_members WHERE channel_id = ? AND user_id = ?').run(id, targetUserId);

  const io = req.app.get('io');
  if (io) {
    removeUserFromChannel(io, id, targetUserId);
    emitToUser(io, targetUserId, 'member:kicked', {
      channelId: id,
      channelName: row.name,
      byUserId: req.userId,
    });
  }

  return res.json({ ok: true, channelId: id, userId: targetUserId });
});

// ---------- DELETE /api/channels/:id 删除频道（仅创建者） ----------
router.delete('/:id', requireAuth, (req, res) => {
  const id = parseChannelId(req.params.id);
  if (!id) return res.status(400).json({ error: '频道 id 非法' });

  const row = db.prepare('SELECT * FROM channels WHERE id = ?').get(id);
  if (!row) return res.status(404).json({ error: '频道不存在' });

  if (row.creator_id !== req.userId) {
    return res.status(403).json({ error: '只有创建者可以删除频道' });
  }

  const remove = db.transaction(() => {
    db.prepare('DELETE FROM channel_members WHERE channel_id = ?').run(id);
    db.prepare('DELETE FROM channels WHERE id = ?').run(id);
  });
  remove();

  const io = req.app.get('io');
  if (io) {
    clearChannelState(io, id);
    io.emit('channel:deleted', { channelId: id });
  }

  return res.json({ ok: true, channelId: id });
});

module.exports = router;
