require('dotenv').config();
const http = require('http');
const express = require('express');
const cors = require('cors');
const config = require('./config');
const { initSocket } = require('./socket');
require('./db'); // 初始化数据库
const authRoutes = require('./routes/auth');
const channelRoutes = require('./routes/channels');
const rtcRoutes = require('./routes/rtc');

const app = express();

app.use(cors());
app.use(express.json({ limit: '1mb' }));
app.use('/uploads', express.static(config.uploadsDir));

app.get('/api/health', (req, res) => {
  res.json({ ok: true, name: 'cuso-server', time: Date.now() });
});

app.use('/api/auth', authRoutes);
app.use('/api/channels', channelRoutes);
app.use('/api/rtc', rtcRoutes);

// 404
app.use((req, res) => {
  res.status(404).json({ error: '接口不存在' });
});

// 统一错误处理
app.use((err, req, res, next) => {
  if (err && err.name === 'MulterError') {
    const msg = err.code === 'LIMIT_FILE_SIZE' ? '图片不能超过 10MB' : `上传失败：${err.message}`;
    return res.status(400).json({ error: msg });
  }
  if (err && err.type === 'entity.parse.failed') {
    return res.status(400).json({ error: '请求体不是合法的 JSON' });
  }
  if (err && err.type === 'entity.too.large') {
    return res.status(413).json({ error: '请求体过大（超过 1MB）' });
  }
  const status = Number(err && err.status) || 500;
  if (status >= 500) console.error(err);
  return res.status(status).json({ error: (err && err.message) || '服务器内部错误' });
});

if (!process.env.JWT_SECRET) {
  console.warn('[warn] 正在使用默认 JWT_SECRET，仅用于本地开发；上线前请设置环境变量 JWT_SECRET');
}

const server = http.createServer(app);
const io = initSocket(server);
app.set('io', io);

server.listen(config.port, '0.0.0.0', () => {
  console.log(`CUSO server listening on http://0.0.0.0:${config.port}`);
  console.log('Socket.IO ready (auth via handshake.auth.token)');
});
