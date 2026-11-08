# CUSO 后端服务（M1：用户认证）

## 运行

```bash
cd server
npm install
npm start
```

开发模式（改代码自动重启）：

```bash
npm run dev
```

默认端口 3000。可通过环境变量 `PORT` 修改。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | /api/health | 否 | 健康检查 |
| POST | /api/auth/register | 否 | 注册 {username, password, nickname?} |
| POST | /api/auth/login | 否 | 登录 → {token, user} |
| GET | /api/auth/me | 是 | 当前用户 |
| PUT | /api/auth/profile | 是 | 改昵称 {nickname} |
| POST | /api/auth/avatar | 是 | 上传头像（multipart，字段名 avatar） |

鉴权头：`Authorization: Bearer <token>`

## 目录

```
server/
├── src/
│   ├── index.js          # 入口
│   ├── config.js         # 端口/JWT/路径/Agora 占位
│   ├── db.js             # SQLite 初始化 + 建表
│   ├── middleware/auth.js# JWT 校验
│   └── routes/auth.js    # 认证相关接口
├── data/                 # SQLite 文件（运行时生成）
└── uploads/              # 头像文件（运行时生成）
```

## 频道接口（M2）

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | /api/channels | 是 | 创建频道 {name}，创建者自动成为成员 |
| GET | /api/channels/mine | 是 | 我加入的频道 |
| GET | /api/channels/public | 是 | 公开大厅（全部频道） |
| POST | /api/channels/join | 是 | 加入频道 {inviteCode} 或 {channelId} |
| GET | /api/channels/:id | 是 | 频道详情 + 成员列表 |
| POST | /api/channels/:id/leave | 是 | 退出频道（移除成员关系） |
| DELETE | /api/channels/:id | 是 | 删除频道（仅创建者） |
| DELETE | /api/channels/:id/members/:userId | 是 | 踢出成员（仅创建者） |

频道对象字段：`id, name, inviteCode, creatorId, createdAt, memberCount, isMember`
详情接口额外返回 `members` 与 `creator`（创建者资料）。

## 实时与语音接口（M3）

### REST

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | /api/rtc/token | 是 | {channelId} → {appId, channelName, uid, token, expiresAt} |

> 需要后端配置 `AGORA_APP_ID` + `AGORA_APP_CERTIFICATE`（写在 `server/.env`）。
> 未配置时返回 503 并提示配置；Token 有效期 24 小时。

### Socket.IO

连接：`io(url, { auth: { token: <JWT> } })`，鉴权失败会收到 `connect_error`。

| 方向 | 事件 | 载荷 |
|------|------|------|
| 客户端→服务端 | channel:join | {channelId}（必须是频道成员） |
| 客户端→服务端 | channel:leave | {channelId} |
| 客户端→服务端 | admin:mute | {channelId, userId, muted}（仅创建者） |
| 服务端→客户端 | presence:update | {channelId, members:[{userId,username,nickname,avatarUrl,muted}]} |
| 服务端→客户端 | presence:error | {error} |
| 服务端→客户端 | force:mute | {channelId, muted, byUserId}（下发给被闭麦者） |
| 服务端→客户端 | member:kicked | {channelId, channelName, byUserId}（下发给被踢者） |
| 服务端→客户端 | admin:ok | {action:'mute', channelId, userId, muted} |
| 服务端→客户端 | channel:created | {channel}（大厅实时刷新用） |
| 服务端→客户端 | channel:deleted | {channelId}（大厅/列表实时刷新用） |

## 管理员权限（仅频道创建者）

- **踢人**：`DELETE /api/channels/:id/members/:userId`，移除成员关系并通知对方（`member:kicked`）。
- **强制闭麦**：`admin:mute`，服务端记录状态并通过 `force:mute` 下发；被闭麦者需管理员解除才能开麦。
- 非创建者调用会收到 403（REST）或 `presence:error`（Socket.IO）。
