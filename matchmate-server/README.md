# MatchMate Server 后端说明

`matchmate-server` 是 MatchMate 的后端服务，基于 Java 17、Spring Boot 4、MyBatis-Plus、MySQL、Redis 和 WebSocket 构建，为移动端提供用户、标签、推荐、聊天、管理员审核和打牌记账本相关接口。

后端围绕业务需求做了登录态管理、权限校验、缓存失效、单设备登录、WebSocket 实时推送、分布式锁、文件上传和数据清理等工程化处理。

## 技术栈

| 类型 | 技术 |
| --- | --- |
| 语言 | Java 17 |
| 框架 | Spring Boot 4、Spring Web MVC |
| 参数校验 | Spring Validation |
| ORM | MyBatis-Plus、XML Mapper |
| 数据库 | MySQL 8 |
| 缓存 | Redis、Spring Cache |
| 会话 | Spring Session Redis |
| 分布式能力 | Redisson |
| 实时通信 | Spring WebSocket |
| 文件存储 | 阿里云 OSS |
| API 文档 | springdoc-openapi |
| 测试 | Spring Boot Test、JUnit、Mockito |

## 目录结构

```text
matchmate-server/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── src/
│   ├── main/
│   │   ├── java/com/cinoo/matchmateserver/
│   │   │   ├── user/          # 用户、登录、注册审核、推荐、资料维护
│   │   │   ├── tag/           # 标签分类、用户标签关联
│   │   │   ├── chat/          # 聊天会话、消息、在线状态、WebSocket
│   │   │   ├── card/          # 打牌记账本：房间、成员、局数、资金、撤销
│   │   │   ├── common/        # 统一响应、分页响应、错误码
│   │   │   ├── exception/     # 业务异常和全局异常处理
│   │   │   ├── config/        # CORS、缓存、MyBatis-Plus、OpenAPI、OSS、数据清理
│   │   │   ├── infrastructure/ # Redis 缓存、OSS、WebSocket 会话管理
│   │   │   └── retention/     # 数据保留和定时清理
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── application-prod.yaml
│   │       ├── schema.sql
│   │       └── mapper/
│   └── test/
└── README.md
```

## 配置说明

默认配置位于 `src/main/resources/application.yaml`，后端上下文路径为 `/api`。

常用环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/matchmate` | MySQL 连接地址 |
| `DB_USERNAME` | `root` | MySQL 用户名 |
| `DB_PASSWORD` | — | MySQL 密码 |
| `REDIS_HOST` | `localhost` | Redis 地址 |
| `REDIS_PASSWORD` | — | Redis 密码 |
| `OSS_ACCESS_KEY_ID` | — | 阿里云 OSS AccessKey |
| `OSS_ACCESS_KEY_SECRET` | — | 阿里云 OSS Secret |
| `OSS_BUCKET_NAME` | — | OSS Bucket 名称 |
| `OSS_ENDPOINT` | — | OSS Endpoint |

## 用户模块

用户模块位于 `user` 包，核心接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/user/register` | 注册 |
| `POST` | `/user/login` | 登录 |
| `POST` | `/user/logout` | 退出登录 |
| `GET` | `/user/current` | 获取当前用户 |
| `PUT` | `/user/current` | 更新个人资料 |
| `PUT` | `/user/tags` | 更新标签 |
| `POST` | `/user/avatar` | 上传头像 |
| `PUT` | `/user/password` | 修改密码 |
| `DELETE` | `/user/current` | 注销账号 |
| `GET` | `/user/recommend` | 推荐伙伴 |
| `GET` | `/user/search/tags` | 搜索用户 |

管理员相关接口（复用 UserController，需管理员权限）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/user/search` | 查询用户列表 |
| `DELETE` | `/user/{id}` | 删除用户 |
| `PUT` | `/user/{id}/status` | 封禁或解封用户 |
| `GET` | `/user/registration/policy` | 查看注册策略 |
| `PUT` | `/user/registration/policy` | 修改注册限额 |
| `GET` | `/user/registration/pending` | 查看待审核用户 |
| `PUT` | `/user/registration/{id}/approve` | 同意注册 |
| `PUT` | `/user/registration/{id}/reject` | 拒绝注册 |

## 标签模块

标签模块位于 `tag` 包，核心接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/tag/categories` | 获取标签分类列表 |

## 聊天模块

聊天模块位于 `chat` 包，核心接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/chat/message/send` | 发送消息 |
| `GET` | `/chat/messages` | 分页读取消息 |
| `GET` | `/chat/conversations` | 会话列表 |
| `GET` | `/chat/conversation/{conversationId}` | 会话详情 |
| `PUT` | `/chat/conversation/{conversationId}/read` | 标记已读 |
| `PUT` | `/chat/conversation/{conversationId}/close` | 关闭当前会话 |
| `GET` | `/chat/conversation/with/{targetUserId}` | 查询与某用户的会话 |

WebSocket 路径：`/api/ws/chat`

推送事件包括：新消息、消息已读、账号封禁和登录被接管。

## 打牌记账本模块

打牌记账本位于 `card` 包，是业务复杂度最高的模块。

核心对象：

- `CardRoom`：房间，包含房间码、房间密码、房主、状态。
- `CardRoomMember`：房间成员，包含总分、结算分、胜负统计和成员状态。
- `CardRound`：一局转账记录。
- `CardRoundScore`：一局中每个用户的分数变化。
- `CardFundRecord`：资金平摊记录。
- `CardFundParticipant`：资金平摊参与者。
- `CardUndoRequest`：撤销申请。
- `CardUndoApproval`：撤销审批。

核心接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/card-room/create` | 创建房间 |
| `POST` | `/card-room/join` | 加入房间 |
| `GET` | `/card-room/{roomId}` | 房间详情 |
| `GET` | `/card-room/active-room` | 当前活跃房间 |
| `GET` | `/card-room/history` | 房间历史 |
| `GET` | `/card-room/ranking` | 牌友排名 |
| `POST` | `/card-room/{roomId}/leave` | 退出房间 |
| `POST` | `/card-room/{roomId}/member/{userId}/kick` | 房主踢出成员 |
| `POST` | `/card-room/{roomId}/member/{userId}/approve` | 房主同意被踢成员重新加入 |
| `POST` | `/card-room/{roomId}/transfer` | 添加转账记录 |
| `POST` | `/card-room/{roomId}/fund` | 添加资金平摊 |
| `POST` | `/card-room/{roomId}/end` | 结束房间 |
| `POST` | `/card-room/{roomId}/round/{roundId}/undo` | 申请撤销一局 |
| `POST` | `/card-room/{roomId}/fund/{fundId}/undo` | 申请撤销资金记录 |
| `POST` | `/card-room/{roomId}/undo/{undoRequestId}/approve` | 同意撤销 |

WebSocket 路径：`/api/ws/card/{roomId}`

模块设计要点：

- 房间操作校验登录用户是否为成员。
- 创建房间时生成 6 位房间码和 4 位房间密码，加入房间时同时校验两者。
- 成员状态覆盖在房间、已退出、已结算、已踢出和申请重新加入，支撑房主踢人和重新加入审批流程。
- 结束房间、离开房间等操作校验房间状态。
- 转账记录要求收支总和为 0。
- 资金平摊要求参与者是当前房间成员。
- 房间只有单人时，前端禁止写入记录；后端历史查询会过滤无任何收支记录的已结束空房间。
- 写入局数和资金记录时使用 Redisson 锁避免并发导致序号或分数异常。
- 业务提交成功后再推送 WebSocket 事件。
- WebSocket 握手允许活跃成员和申请重新加入成员连接，以便成员状态变更后能够实时刷新。
- 撤销操作通过申请和审批完成，避免单人误删影响其他成员。

## 缓存设计

### 缓存预热与定时刷新

应用启动后通过 `ApplicationReadyEvent` 异步预热标签分类等热点数据到 Redis；定时刷新标签缓存（默认 1 小时间隔）。预热与刷新均使用 Redisson 分布式锁，防止多实例集群中重复执行。

### 缓存读写策略

采用 Cache-Aside 模式，通过 `DistributedCacheService` 统一管理：

- 读：命中直接返回；未命中 → Redisson 分布式锁 → 双重检查 → 回源数据库 → 回写 Redis。
- 写：数据库更新后，通过 `CacheInvalidationService` 注册 Spring 事务回调，仅在事务提交后删除相关缓存，避免并发读到脏数据。
- 降级：Redis 不可用时静默降级到直接查 DB，不影响业务。

### 缓存数据

- 标签分类与用户标签。
- 用户展示视图。
- 用户推荐结果与搜索结果。
- 聊天未读数与当前打开会话。
- 最近在线时间与会话列表缓存。

缓存失效由 `CacheInvalidationService` 统一处理：用户资料、标签、头像、状态变化 → 事务提交后 → 删除对应视图缓存并清空搜索/推荐集合缓存。

## 文件上传

头像上传通过 `OssUtils` 对接阿里云 OSS：

- 校验文件大小和类型。
- 按用户 ID 组织头像路径。
- 更新头像失败时删除新上传文件。
- 更新成功后尝试删除旧头像。

OSS 配置通过环境变量传入，避免密钥提交到仓库。

## 数据保留

`retention` 包包含数据清理逻辑：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `CHAT_MESSAGE_AGE` | `1d` | 聊天消息保留时长 |
| `DATA_RETENTION_CLEANUP_CRON` | `0 5 * * * *` | 清理任务 cron |

## 本地运行

导入数据库：

```bash
mysql -u root -p < src/main/resources/schema.sql
```

启动服务：

```bash
# Windows
mvnw.cmd spring-boot:run
# macOS / Linux
./mvnw spring-boot:run
```

默认接口地址 `http://localhost:8080/api`，Swagger UI 地址 `http://localhost:8080/api/swagger-ui.html`。

运行测试：

```bash
# 全量测试
mvnw.cmd test

# 指定测试类
mvnw.cmd -Dtest=UserServiceTest test
```
