const express = require('express');
const db = require('../db');
const config = require('../config');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

const TOKEN_TTL_SECONDS = 24 * 3600; // 24 小时（agora-token 007 用「距今秒数」）

function loadBuilder() {
  try {
    const agora = require('agora-token');
    return { RtcTokenBuilder: agora.RtcTokenBuilder, RtcRole: agora.RtcRole };
  } catch (e) {
    return null;
  }
}

// ---------- POST /api/rtc/token ----------
router.post('/token', requireAuth, (req, res) => {
  const body = req.body || {};
  const raw = body.channelId !== undefined ? body.channelId : body.channel_id;
  const channelId = Number(raw);

  if (!Number.isInteger(channelId) || channelId <= 0) {
    return res.status(400).json({ error: '请提供合法的 channelId' });
  }

  const channel = db.prepare('SELECT id FROM channels WHERE id = ?').get(channelId);
  if (!channel) return res.status(404).json({ error: '频道不存在' });

  const member = db
    .prepare('SELECT id FROM channel_members WHERE channel_id = ? AND user_id = ?')
    .get(channelId, req.userId);
  if (!member) {
    return res.status(403).json({ error: '你不是该频道成员，无法获取语音 Token' });
  }

  const appId = config.agora.appId;
  const appCertificate = config.agora.appCertificate;
  if (!appId || !appCertificate) {
    return res.status(503).json({
      error: 'Agora 凭据未配置：请在 server/.env 中设置 AGORA_APP_ID 与 AGORA_APP_CERTIFICATE',
    });
  }

  const builder = loadBuilder();
  if (!builder) {
    return res.status(500).json({ error: '服务端缺少 agora-token 依赖' });
  }

  const channelName = String(channel.id);
  const uid = req.userId; // 用用户 id 作为 Agora uid，保证同一用户每次进房 uid 一致
  const expiresAt = Math.floor(Date.now() / 1000) + TOKEN_TTL_SECONDS;

  let token;
  try {
    token = builder.RtcTokenBuilder.buildTokenWithUid(
      appId,
      appCertificate,
      channelName,
      uid,
      builder.RtcRole.PUBLISHER,
      TOKEN_TTL_SECONDS,
      TOKEN_TTL_SECONDS
    );
  } catch (e) {
    return res.status(500).json({ error: 'Token 生成失败：' + e.message });
  }

  return res.json({ appId, channelName, uid, token, tokenExpire: TOKEN_TTL_SECONDS, expiresAt });
});

module.exports = router;
