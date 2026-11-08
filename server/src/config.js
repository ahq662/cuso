const path = require('path');

module.exports = {
  port: Number(process.env.PORT) || 3000,
  jwtSecret: process.env.JWT_SECRET || 'cuso-dev-secret-change-in-production',
  jwtExpiresIn: process.env.JWT_EXPIRES_IN || '30d',
  dataDir: path.join(__dirname, '..', 'data'),
  uploadsDir: path.join(__dirname, '..', 'uploads'),
  // Agora 凭据（M3 才用，届时在 .env 或环境变量里配置）
  agora: {
    appId: process.env.AGORA_APP_ID || '',
    appCertificate: process.env.AGORA_APP_CERTIFICATE || '',
  },
};
