const jwt = require('jsonwebtoken');
const config = require('../config');

/** 校验 Authorization: Bearer <token>，通过后把用户 id 挂到 req.userId */
function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const match = /^Bearer\s+(.+)$/i.exec(header);
  if (!match) {
    return res.status(401).json({ error: '未登录：缺少 Authorization Bearer Token' });
  }

  try {
    const payload = jwt.verify(match[1].trim(), config.jwtSecret);
    const userId = Number(payload.sub);
    if (!Number.isInteger(userId) || userId <= 0) {
      return res.status(401).json({ error: 'Token 内容无效' });
    }
    req.userId = userId;
    return next();
  } catch (err) {
    return res.status(401).json({ error: 'Token 无效或已过期' });
  }
}

module.exports = { requireAuth };
