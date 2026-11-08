# CUSO 语音 — Web 客户端（M9：认证 UI）

## 技术栈

React 19 + Vite 8 + React Router 7，像素暗色风（与 `UI设计.md` 一致）。

## 运行

先启动后端（另开一个终端）：

```powershell
cd server
node src/index.js
```

再启动 Web：

```powershell
cd web
npm run dev
```

打开浏览器访问 **http://localhost:5173/**（Vite 默认绑定 localhost）。

> 注意：你的 PowerShell 禁止运行 npm.ps1，如果 `npm` 报执行策略错误，改用 `npm.cmd run dev`。

## 目录

```
web/
├── index.html
├── vite.config.js        # 开发代理：/api 与 /uploads → http://127.0.0.1:3000
└── src/
    ├── main.jsx
    ├── App.jsx           # 路由与登录态守卫
    ├── api.js            # fetch 封装 + token 存储
    ├── styles.css        # 像素风样式
    ├── auth/AuthContext.jsx
    └── pages/
        ├── LoginPage.jsx
        ├── RegisterPage.jsx
        └── ProfilePage.jsx
```

## 说明

- 登录 token 存在 `localStorage`（key: `cuso_token`），刷新页面会自动恢复登录态。
- 开发环境通过 Vite 代理访问后端；生产环境可通过 `VITE_API_BASE` 指定 API 地址。
- 像素字体文件暂未内置，当前用系统字体兜底；后续可把 Fusion Pixel / Zpix 字体文件放入并接入。

## 语音房（M11）

进入方式：频道详情页 →「进入语音」→ `/channels/:id/voice`。

### 已实现

- Agora Web SDK 进房/退房（Token 由后端 `/api/rtc/token` 签发）
- 开麦 / 闭麦（自己控制）
- 说话高亮（Agora 音量指示 + 呼吸光晕）
- 单人音量调节（点击成员头像 → 0~100 滑条，0 即单独静音）
- 降噪（麦克风轨开启 `AEC / AGC / ANS`）
- 管理员（频道创建者）强制闭麦 / 解除：被闭麦者**无法自己开麦**，需管理员解除
- 被踢出频道时全局提示并自动返回「我的频道」
- Token 到期前自动续期（`token-privilege-will-expire`）
- 语音页**按需加载**（Agora SDK 单独分包，不拖慢首屏）
- **语音会话全局化**：切到「大厅 / 我的 / 我的频道」不会断开语音；页面顶部出现「正在语音 · 频道 · 返回」横条，点一下回到语音房
- 「返回频道」只离开页面，语音继续；只有「退出语音」才真正离开

### 注意

- 浏览器麦克风需要**安全上下文**：请用 `http://localhost:5173` 访问（不要用局域网 IP 的 http）。
- 首次进入会请求麦克风权限；拒绝后仍可进入但为「仅收听」模式。
- 如果刷新后没有声音，点一下页面（浏览器自动播放策略限制）。
- 管理员强制闭麦的状态保存在服务端内存，重启后端会重置。
- Agora 单频道并发有上限；若接近 20 人出现问题，可切换 `mode: 'live'` 或拆频道。
