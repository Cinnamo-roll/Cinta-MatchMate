# MatchMate 伙伴匹配系统

MatchMate 是一个移动端伙伴匹配全栈项目，支持用户基于资料与兴趣标签发现伙伴、通过即时聊天建立联系。项目在核心匹配业务之外，还内置了打牌记账本模块，用于线下多人打牌时的分数记录、资金平摊与撤销审批。

项目覆盖了用户体系、标签检索、推荐排序、即时通信、后台审核、单设备登录接管、Redis 缓存、WebSocket 实时推送、移动端交互优化和完整的数据库初始化数据，适合作为全栈项目展示或个人技术沉淀。

## 一句话概括

> 基于 Vue 3 + Spring Boot + MySQL + Redis + WebSocket 实现的移动端伙伴匹配系统，支持用户标签匹配、推荐排序、即时聊天、后台审核、单设备登录接管和打牌记账本等功能。

## 项目定位

项目定位为轻量级校园或兴趣社交工具：

- 用户注册账号、维护个人资料、选择兴趣标签。
- 首页展示伙伴列表，搜索页支持关键词与标签组合筛选。
- 推荐模块根据共同标签、在线状态和资料完整度进行可解释排序。
- 聊天模块提供会话列表、消息记录、未读数与实时推送。
- 管理员可管理用户状态、处理注册审核、控制每日注册限额。
- 打牌记账本支持创建带密码房间、加入房间、转账记账、资金平摊、撤销审批、房主踢人、重新加入审批、历史记录和牌友排名。

## 技术栈

| 模块 | 技术选型 |
| --- | --- |
| 前端 | Vue 3、Vite、TypeScript、Vue Router、Vant 4、Axios |
| 后端 | Java 17、Spring Boot 4、Spring Web MVC、Spring Validation |
| ORM | MyBatis-Plus、XML Mapper |
| 数据库 | MySQL 8 |
| 缓存与会话 | Redis、Spring Session、Spring Cache、Redisson |
| 实时通信 | Spring WebSocket |
| 文件存储 | 阿里云 OSS |
| API 文档 | springdoc-openapi |
| 测试 | JUnit、Spring Boot Test、Mockito |
| 构建 | npm、Maven Wrapper |

## 仓库结构

```text
MatchMate/
├── README.md                    # 项目总览
├── matchmate-mobile/            # 移动端前端
│   ├── README.md
│   ├── index.html
│   ├── package.json
│   ├── vite.config.ts
│   ├── public/
│   └── src/
├── matchmate-server/            # 后端服务
│   ├── README.md
│   ├── pom.xml
│   ├── src/main/java/
│   ├── src/main/resources/
│   └── src/test/java/
└── .gitignore
```

- `matchmate-mobile`：Vue 3 移动端前端，负责页面展示、路由、接口调用、WebSocket 连接与移动端交互。
- `matchmate-server`：Spring Boot 后端，负责业务接口、数据库访问、缓存、会话、权限、文件上传和实时推送。

## 核心功能

### 用户体系

用户模块包含注册、登录、退出、资料修改、标签维护、头像上传、修改密码和注销账号。密码经 BCrypt 加密存储，接口统一返回 `BaseResponse` 结构，前端统一处理成功、失败、登录过期和权限异常。

单设备登录接管：

- 同一账号在新设备登录时，检测是否存在其他活跃 Session。
- 存在冲突时后端返回登录冲突状态，前端提示是否接管。
- 用户确认后新设备登录成功，旧 Session 被清理。
- 旧设备 WebSocket 连接收到被接管下线通知。

### 伙伴匹配与推荐

标签按分类维护（性格特点、社交偏好、专业技能、学习成长、运动健身、游戏娱乐、影音文艺、户外旅行、生活兴趣）。用户可选择多个标签，搜索页支持关键词与标签组合筛选。

推荐算法采用规则打分，结果可解释：

- 共同标签越多，推荐分越高。
- 在线用户有额外加分。
- 有头像、有昵称、标签更完整有少量加分。
- 返回推荐分、共同标签和推荐理由。

### 即时聊天

聊天模块包含会话列表、消息记录、发送消息、未读数、已读状态和 WebSocket 实时推送。后端用 HTTP 接口处理会话与消息可靠写入，用 WebSocket 推送新消息、已读事件、封禁通知和登录接管通知。Redis 用于保存未读数、当前打开会话、最近在线时间和会话缓存。

### 管理后台

管理员可通过移动端进入用户管理和注册审核页面，支持：按关键词查询用户、封禁或解封用户、查看/修改每日注册限额、审核待注册用户。每日注册达到限额后新用户进入待审核状态。

### 打牌记账本

与匹配业务相对独立，但复用用户登录、权限校验、WebSocket 和数据库基础设施。核心能力：

- 创建房间并生成 6 位房间码和 4 位房间密码，加入房间时同时校验房间码与密码。
- 记录转账型输赢和资金平摊；单人房间禁止记账和平摊，避免生成无意义记录。
- 记一笔时支持批量给其他成员填入相同金额，适配线下快速结算场景。
- 房间内实时推送新记录、成员离开、房间关闭等事件，被踢用户会收到提示并自动返回记账本页。
- 房主可踢出成员，被踢成员再次加入时进入“申请加入”状态，需房主同意后恢复为活跃成员。
- 发起撤销申请，其他成员审批。
- 房主结束房间并沉淀历史；没有任何收支记录的空房间结束后不会进入最近记录。
- 查询历史房间和牌友排名。

## 数据库

数据库初始化脚本位于 `matchmate-server/src/main/resources/schema.sql`，包含建库、建表、标签基础数据和脱敏示例数据。导入方式：

```bash
mysql -u root -p < matchmate-server/src/main/resources/schema.sql
```

后端默认连接 `jdbc:mysql://localhost:3306/matchmate`。用户名、密码和地址可通过环境变量覆盖，详见 `matchmate-server/README.md`。

## 本地运行

启动后端：

```bash
cd matchmate-server
# Windows
mvnw.cmd spring-boot:run
# macOS / Linux
./mvnw spring-boot:run
```

默认接口地址 `http://localhost:8080/api`。

启动前端：

```bash
cd matchmate-mobile
npm install
npm run dev
```

默认前端地址 `http://localhost:5173`。Vite 已配置 `/api` 代理到 `http://localhost:8080`，本地开发时前端可直接调用 `/api/...`。

## 常用命令

```bash
# 前端构建
cd matchmate-mobile && npm run build

# 后端全量测试
cd matchmate-server && mvnw.cmd test

# 后端指定测试
cd matchmate-server && mvnw.cmd -Dtest=UserServiceTest test
```

## 技术亮点

- **启动缓存预热与分布式锁**：应用启动后异步预热标签分类等热点数据到 Redis，通过 Redisson 分布式锁保证多实例集群安全；定时刷新缓存同样加锁避免重复执行。
- **Cache-Aside 防击穿与自动降级**：缓存未命中时通过 Redisson 分布式锁 + 双重检查 + 回源数据库，防止缓存击穿；Redis 不可用时静默降级到直接查 DB，不影响业务。
- **事务感知的缓存失效与锁释放**：缓存清理和 Redisson 锁释放均注册到 Spring 事务回调，仅在 DB 事务提交后执行，避免并发读到脏数据或锁提前释放。
- **前端请求去重与短期缓存**：多个组件并发请求当前用户时自动合并为一次 HTTP 调用（Promise 缓存），用户信息 30 秒 TTL 避免重复请求。
- **双通道 WebSocket 会话管理**：全局聊天通道与按房间隔离的打牌记账通道，分别维护 userId → WebSocket 一对一映射，旧连接自动替换。
- **结构化错误码与全局异常处理**：枚举错误码关联 HTTP 状态码 + 英文内部消息 + 中文用户提示；8 类全局异常处理覆盖参数校验、业务异常、文件大小、404/405 等场景。
- **标签匹配与可解释推荐**：按 9 大分类维护兴趣标签，推荐采用规则打分（共同标签、在线状态、资料完整度），返回推荐分、共同标签和推荐理由。
- **单设备登录接管**：HTTP Session 注册表（ConcurrentHashMap 双向映射）检测冲突 → 提示确认 → 清理旧会话 → WebSocket 通知旧设备下线，形成完整闭环。
- **即时聊天**：HTTP 负责消息落库与可靠性，WebSocket 负责新消息、已读、封禁、接管通知实时推送，Redis 管理未读数与在线状态。
- **注册审核与每日限额**：每日注册人数上限可动态调整，超额后新用户进入待审核状态，管理员可审批通过或拒绝。
- **打牌记账本完整闭环**：6 位房间码 + 4 位密码、成员 5 状态机（在房间/已退出/已结算/已踢出/申请重新加入）、撤销申请审批、资金平摊、房主踢人与重新加入审批、牌友排名。
- **测试覆盖**：14 个后端测试类覆盖 Controller、Service、Mapper、WebSocket、分布式缓存和 OSS，确保核心链路回归验证。
- **工程化**：前后端独立 README、环境变量说明、数据库初始化脚本、配置分离（application.yaml / application-prod.yaml）和 springdoc-openapi 文档，便于复现与交接。
