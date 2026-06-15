# MatchMate Mobile 前端说明

`matchmate-mobile` 是 MatchMate 的移动端前端项目，基于 Vue 3、Vite、TypeScript 和 Vant 4 开发，承担用户可见的主要交互，包括首页伙伴列表、发现页、搜索推荐、聊天、个人中心、管理员页面和打牌记账本。

前端按移动端 Web App 体验设计，重点处理了首屏加载、分页列表、固定导航、滚动区域、表单反馈、登录态缓存、WebSocket 连接和异常提示等细节。

## 技术栈

| 类型 | 技术 |
| --- | --- |
| 框架 | Vue 3 |
| 构建 | Vite |
| 语言 | TypeScript |
| 路由 | Vue Router |
| UI | Vant 4 |
| 请求 | Axios |
| 组件自动导入 | unplugin-vue-components、VantResolver |

## 目录结构

```text
matchmate-mobile/
├── index.html
├── package.json
├── vite.config.ts
├── src/
│   ├── api/          # 后端接口封装，按业务拆分
│   ├── components/   # 可复用组件
│   ├── composables/  # 组合式逻辑（WebSocket、标签选择、通知）
│   ├── config/       # 路由与标签配置
│   ├── layouts/      # 全局基础布局
│   ├── models/       # TypeScript 类型定义
│   ├── pages/        # 页面组件
│   ├── plugins/      # Axios 实例等插件
│   ├── styles/       # 全局样式、主题、认证页样式
│   ├── utils/        # 工具函数
│   ├── App.vue
│   └── main.ts
└── public/
```

| 目录 | 作用 |
| --- | --- |
| `src/api` | 后端接口封装，按业务拆分为用户、聊天、打牌记账等模块 |
| `src/models` | TypeScript 类型定义，保证接口数据结构清晰 |
| `src/pages` | 页面组件，如首页、搜索页、聊天页、打牌房间页 |
| `src/components` | 可复用组件，包括用户卡片等 |
| `src/composables` | 组合式逻辑，如 WebSocket 连接、标签选择、通知 |
| `src/config` | 路由与标签配置 |
| `src/layouts` | 全局基础布局，承载导航栏和底部栏 |
| `src/plugins` | Axios 实例等插件配置 |
| `src/styles` | 全局样式、主题样式、认证页样式和标签样式 |
| `src/utils` | 用户状态、本地工具函数 |

## 页面与路由

| 路由 | 页面 | 功能 |
| --- | --- | --- |
| `/` | `Index.vue` | 首页伙伴列表 |
| `/discover` | `Discover.vue` | 发现页入口 |
| `/team` | `Team.vue` | 聊天会话列表 |
| `/user` | `User.vue` | 个人中心 |
| `/search` | `Search.vue` | 搜索伙伴和推荐伙伴 |
| `/chat/:id` | `ChatDetail.vue` | 聊天详情 |
| `/discover/card-ledger` | `CardLedger.vue` | 打牌记账本首页 |
| `/card-room/:id` | `CardRoom.vue` | 打牌房间详情 |
| `/admin/users` | `AdminUsers.vue` | 管理员用户管理 |
| `/admin/registrations` | `AdminRegistrations.vue` | 注册审核 |
| `/login` | `Login.vue` | 登录 |
| `/register` | `Register.vue` | 注册 |

路由元信息中维护了页面标题、是否展示返回按钮、是否隐藏底部栏、是否锁定滚动等状态，基础布局根据这些元信息统一控制导航结构。

## 接口封装

前端统一使用 `src/plugins/myAxios.ts` 创建 Axios 实例：

```ts
const myAxios = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true,
});
```

Vite 代理配置将 `/api` 转发到后端：

```ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      ws: true,
    },
  },
}
```

接口按业务拆分：

- `api/matchmate.ts`：登录、注册、当前用户、资料更新、标签、推荐、管理员审核。
- `api/chat.ts`：会话列表、消息分页、发送消息、已读、查找会话。
- `api/card.ts`：创建房间、加入房间、房间详情、转账、平摊、踢出成员、同意重新加入、撤销、历史和排名。

每个 API 模块使用 `unwrap` 提取后端统一响应里的 `data` 字段。

## 登录态与前端缓存

前端对当前用户做了短期缓存：

- 避免同一页面多个组件重复请求 `/user/current`。
- `CURRENT_USER_CACHE_TTL` 为 30 秒。
- 登录、退出、资料更新和标签更新时同步维护缓存。
- 需要强制刷新时调用 `getCurrentUser(true)`。

标签分类同样做了前端缓存，减少标签弹窗或标签选择组件的重复请求。

## WebSocket

项目使用两个 WebSocket 组合式逻辑：

- `useWebSocket.ts`：聊天、登录接管、封禁通知等全局实时事件。
- `useCardWebSocket.ts`：打牌房间事件，如新记账、新平摊、撤销审批、房间成员变化和房间关闭。

WebSocket 路径：

- `/api/ws/chat`
- `/api/ws/card/{roomId}`

前端通过实时事件刷新当前页面数据，避免用户手动刷新。

## 核心页面

### 首页伙伴列表

- 首屏分页加载用户，滚动到底部继续加载。
- 处理 loading、finished、empty、error 和 retry 状态。
- 避免并发请求导致重复数据。
- 用户卡片展示头像、昵称、标签、在线状态和基础信息。

### 搜索与推荐

搜索页支持关键词和标签筛选，同时展示推荐伙伴。推荐数据来自后端规则打分接口，前端展示推荐理由、共同标签和用户卡片。

### 聊天

- 会话列表展示最近消息和未读数。
- 聊天详情支持分页加载历史消息。
- 发送消息后立即更新本地视图。
- WebSocket 新消息到达时刷新会话状态。
- 打开会话后调用已读接口清理未读。

### 管理员页面

- 用户管理支持搜索、封禁、解封。
- 注册审核支持查看待审核用户、同意、拒绝。
- 注册策略支持每日注册限额配置。
- 危险操作使用确认弹窗。

### 打牌记账本

- 入口页支持创建房间、加入房间、查看活跃房间、历史房间和排名；从房间主动返回时可跳过自动进入活跃房间，避免用户无法停留在记账本首页。
- 加入房间需要输入 6 位房间号和 4 位房间密码，房间页展示当前房间号、密码和活跃人数。
- 房间页展示成员、总分、最近局数、资金平摊和撤销状态；成员状态区分在房间、已退出、已结算、已踢出和申请加入。
- 房主可踢出成员；被踢成员再次加入会进入申请状态，房主可在成员列表中点击“同意”恢复其成员资格。
- 被踢成员会收到实时提示并自动返回打牌记账本页，避免继续停留在无权限房间。
- 记账操作支持转账和资金平摊两种形式；只有当前活跃成员且房间至少 2 人时才能记账或发起平摊。
- “记一笔”提供批量填入金额能力，输入一个金额后可快速填给其他所有成员。
- 房间内通过 WebSocket 接收实时变更；进行中的房间顶部返回到发现页，已结束房间顶部返回到打牌记账本页。

## 本地运行

```bash
npm install
npm run dev       # 开发服务
npm run build     # 生产构建
npm run preview   # 预览构建结果
```

默认开发地址 `http://localhost:5173`。
