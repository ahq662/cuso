const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const db = require('../db');
const config = require('../config');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

const USERNAME_RE = /^[A-Za-z0-9_\u4e00-\u9fa5]{3,20}$/;
const SALT_ROUNDS = 10;

function signToken(userId) {
  return jwt.sign({ sub: String(userId) }, config.jwtSecret, { expiresIn: config.jwtExpiresIn });
}

/** 对外返回的用户资料（不含密码哈希） */
function publicUser(row) {
  return {
    id: row.id,
    username: row.username,
    nickname: row.nickname,
    avatarUrl: row.avatar_url,
    createdAt: row.created_at,
  };
}

// ---------- 头像上传 ----------
fs.mkdirSync(config.uploadsDir, { recursive: true });

const ALLOWED_EXT = ['.png', '.jpg', '.jpeg', '.webp', '.gif'];

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, config.uploadsDir),
  filename: (req, file, cb) => {
    const ext = (path.extname(file.originalname) || '').toLowerCase();
    const safeExt = ALLOWED_EXT.includes(ext) ? ext : '.png';
    const rand = crypto.randomBytes(4).toString('hex');
    cb(null, `avatar_${req.userId}_${Date.now()}_${rand}${safeExt}`);
  },
});

const upload = multer({
  storage,
  limits: { fileSize: 10 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    if (/^image\//i.test(file.mimetype)) return cb(null, true);
    const err = new Error('只允许上传图片文件');
    err.status = 400;
    return cb(err);
  },
});

// ---------- POST /api/auth/register ----------
router.post('/register', (req, res) => {
  const body = req.body || {};
  const username = typeof body.username === 'string' ? body.username.trim() : '';
  const password = typeof body.password === 'string' ? body.password : '';
  const nickname = typeof body.nickname === 'string' ? body.nickname.trim() : '';

  if (!USERNAME_RE.test(username)) {
    return res.status(400).json({ error: '用户名需为 3-20 位字母、数字、下划线或中文' });
  }
  if (password.length < 6 || password.length > 64) {
    return res.status(400).json({ error: '密码长度需为 6-64 位' });
  }
  if (nickname && nickname.length > 20) {
    return res.status(400).json({ error: '昵称不能超过 20 个字符' });
  }

  const exists = db.prepare('SELECT id FROM users WHERE username = ?').get(username);
  if (exists) {
    return res.status(409).json({ error: '用户名已被占用' });
  }

  const hash = bcrypt.hashSync(password, SALT_ROUNDS);
  const info = db
    .prepare('INSERT INTO users (username, password_hash, nickname) VALUES (?, ?, ?)')
    .run(username, hash, nickname || username);

  const row = db.prepare('SELECT * FROM users WHERE id = ?').get(info.lastInsertRowid);
  return res.status(201).json({ token: signToken(row.id), user: publicUser(row) });
});

// ---------- POST /api/auth/login ----------
router.post('/login', (req, res) => {
  const body = req.body || {};
  const username = typeof body.username === 'string' ? body.username.trim() : '';
  const password = typeof body.password === 'string' ? body.password : '';

  if (!username || !password) {
    return res.status(400).json({ error: '用户名和密码不能为空' });
  }

  const row = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
  if (!row || !bcrypt.compareSync(password, row.password_hash)) {
    return res.status(401).json({ error: '用户名或密码错误' });
  }

  return res.json({ token: signToken(row.id), user: publicUser(row) });
});

// ---------- GET /api/auth/me ----------
router.get('/me', requireAuth, (req, res) => {
  const row = db.prepare('SELECT * FROM users WHERE id = ?').get(req.userId);
  if (!row) return res.status(404).json({ error: '用户不存在' });
  return res.json({ user: publicUser(row) });
});

// ---------- PUT /api/auth/profile ----------
router.put('/profile', requireAuth, (req, res) => {
  const nickname =
    req.body && typeof req.body.nickname === 'string' ? req.body.nickname.trim() : '';

  if (nickname.length < 1 || nickname.length > 20) {
    return res.status(400).json({ error: '昵称长度需为 1-20 个字符' });
  }

  const row = db.prepare('SELECT * FROM users WHERE id = ?').get(req.userId);
  if (!row) return res.status(404).json({ error: '用户不存在' });

  db.prepare('UPDATE users SET nickname = ? WHERE id = ?').run(nickname, req.userId);
  const updated = db.prepare('SELECT * FROM users WHERE id = ?').get(req.userId);
  return res.json({ user: publicUser(updated) });
});

// ---------- POST /api/auth/avatar ----------
router.post('/avatar', requireAuth, upload.single('avatar'), (req, res) => {
  if (!req.file) {
    return res.status(400).json({ error: '请上传头像文件（表单字段名：avatar）' });
  }

  const old = db.prepare('SELECT avatar_url FROM users WHERE id = ?').get(req.userId);
  const avatarUrl = `/uploads/${req.file.filename}`;
  db.prepare('UPDATE users SET avatar_url = ? WHERE id = ?').run(avatarUrl, req.userId);

  // 删除旧头像文件，避免磁盘堆积
  if (old && old.avatar_url && old.avatar_url !== avatarUrl) {
    const oldPath = path.join(config.uploadsDir, path.basename(old.avatar_url));
    try {
      fs.unlinkSync(oldPath);
    } catch (e) {
      // 旧文件不存在则忽略
    }
  }

  const row = db.prepare('SELECT * FROM users WHERE id = ?').get(req.userId);
  return res.json({ user: publicUser(row) });
});

module.exports = router;
