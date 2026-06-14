# 前端 (matchmate-mobile)

## 技术栈

| 组件 | 版本/工具 |
|------|-----------|
| 框架 | Vue 3.5 |
| 构建 | Vite 8 |
| 语言 | TypeScript 6.0 |
| UI 组件库 | Vant 4.9 |
| 路由 | Vue Router 5 |
| HTTP | Axios |

## 目录结构

```
src/
├── api/                  # API 请求层 (axios)
│   ├── matchmate.ts      # 用户/标签/推荐 API
│   ├── chat.ts            # 聊天 API
│   └── card.ts            # 打牌记账 API
├── components/
│   └── UserCard.vue       # 用户卡片组件
├── composables/           # 组合式函数
├── config/
│   └── route.ts           # 路由配置 (13 条路由)
├── layouts/               # 布局组件 (NavBar, Tabbar)
├── models/                # TypeScript 接口/类型
├── pages/                 # 13 个页面
│   ├── Index.vue          # 首页（推荐）
│   ├── Discover.vue       # 发现页（打牌记账入口）
│   ├── Team.vue           # 消息列表
│   ├── User.vue           # 我的（个人中心）
│   ├── Search.vue         # 搜索伙伴
│   ├── Login.vue / Register.vue  # 登录/注册
│   ├── ChatDetail.vue     # 聊天详情
│   ├── CardLedger.vue     # 打牌记账列表页
│   ├── CardRoom.vue       # 房间详情页（核心页面 ~40KB）
│   ├── AdminUsers.vue     # 管理员-用户管理
│   ├── AdminRegistrations.vue  # 管理员-注册审核
│   └── NotFound.vue       # 404
├── plugins/
│   └── myAxios.ts         # Axios 实例 (baseURL=/api)
├── styles/                # 全局样式 (theme.css 等)
├── utils/                 # 工具函数
├── App.vue                # 根组件
└── main.ts                # 入口 (Vue + Router)
```

## 导航结构

```
Tabbar 导航:
  ├── 首页 → /
  ├── 发现 → /discover
  ├── 消息 → /team
  └── 我的 → /user

栈内导航 (hideTabbar):
  /search          → 搜索伙伴
  /chat/:id        → 聊天详情
  /card-room/:id   → 打牌记账房间
  /discover/card-ledger → 记账本
  /admin/users     → 用户管理
  /admin/registrations → 注册审核
  /register        → 注册
  /login           → 登录
```

## 开发

```bash
npm install
npm run dev        # http://localhost:5173
```

开发代理：`/api` → `http://localhost:8080`（见 vite.config.ts）

## 构建

```bash
npm run build      # 产物在 dist/
npm run preview    # 本地预览构建产物
```

构建产物通过 Nginx 托管，API 通过 Nginx 反向代理到后端 8080 端口。
