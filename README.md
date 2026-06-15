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
│   ├── package.json
│   ├── vite.config.ts
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

- **前后端分离**：前端专注移动端体验，后端提供统一 REST API 和 WebSocket 通道。
- **标签匹配与推荐**：通过标签分类、用户标签关系和分页搜索支撑伙伴发现，推荐采用规则打分可解释。
- **单设备登录接管**：通过 Session 注册表、旧会话失效和 WebSocket 下线通知实现。
- **即时聊天**：HTTP 负责消息落库，WebSocket 负责实时通知，Redis 辅助未读数和在线状态。
- **注册审核机制**：每日注册限额与待审核状态，具备用户增长后台控制能力。
- **打牌记账本**：房间密码、成员状态机、局数、分数、资金平摊、撤销审批、踢人和重新加入审批形成完整业务闭环。
- **缓存设计**：标签、用户视图、推荐结果等使用 Redis 缓存，数据变更时主动失效。
- **工程化**：前后端独立 README、测试用例、环境变量说明和数据库初始化脚本，便于复现和交接。
