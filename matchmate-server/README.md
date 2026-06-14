# 后端 (matchmate-server)

## 技术栈

| 组件 | 版本/工具 |
|------|-----------|
| 框架 | Spring Boot 4.0.6 |
| 语言 | Java 17 |
| 构建 | Maven |
| ORM | MyBatis-Plus 3.5.15 |
| 数据库 | MySQL 8 |
| 缓存 | Redis（Spring Session + Redisson） |
| 安全 | Spring Security Crypto (BCrypt) |
| API 文档 | SpringDoc OpenAPI |
| 存储 | 阿里云 OSS |

## 模块结构

```
src/main/java/com/cinoo/matchmateserver/
├── MatchmateServerApplication.java   # 启动类
├── card/                             # 打牌记账模块
│   ├── controller/CardRoomController.java
│   ├── service/
│   ├── mapper/
│   ├── model/entity/                 # 8 个实体
│   ├── model/request/                # 请求 DTO
│   ├── model/vo/                     # 响应 VO
│   ├── websocket/                    # 房间 WebSocket
│   └── constant/
├── chat/                             # 聊天模块
│   ├── controller/ChatController.java
│   ├── service/
│   ├── mapper/
│   ├── model/entity/                 # Conversation, Message
│   ├── model/request/
│   ├── model/vo/
│   └── websocket/                    # 聊天 WebSocket
├── tag/                              # 标签模块
│   ├── controller/TagController.java
│   ├── service/
│   ├── mapper/
│   ├── model/entity/                 # Tag, UserTag
│   └── model/vo/
├── user/                             # 用户模块
│   ├── controller/UserController.java
│   ├── service/
│   ├── mapper/
│   ├── model/entity/User.java
│   ├── model/request/
│   ├── model/vo/
│   └── constant/
├── common/                           # 公共类 (BaseResponse, ErrorCode, ResultUtils)
├── config/                           # 配置类 (CORS, Cache, OSS, MyBatis-Plus)
├── exception/                        # 全局异常处理
├── infrastructure/                   # 基础设施
│   ├── cache/                        # 分布式缓存服务
│   ├── oss/OssUtils.java             # OSS 上传工具
│   └── websocket/                    # WebSocket 配置与会话管理
└── retention/                        # 数据保留与清理
```

## API 接口

> 统一前缀：`/api`，所有响应格式：`{ "code": 0, "data": ..., "description": "ok" }`

### 用户管理 `/api/user`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/register` | 用户注册 | 公开 |
| POST | `/login` | 用户登录 | 公开 |
| POST | `/logout` | 登出 | 登录 |
| GET | `/current` | 获取当前用户 | 登录 |
| PUT | `/current` | 更新个人资料 | 登录 |
| PUT | `/password` | 修改密码 | 登录 |
| DELETE | `/current` | 注销账户 | 登录 |
| POST | `/avatar` | 上传头像(OSS) | 登录 |
| PUT | `/tags` | 更新个人标签(最多3个) | 登录 |
| GET | `/search/tags` | 按关键词+标签搜索用户 | 登录 |
| GET | `/recommend` | 随机推荐用户 | 登录 |
| GET | `/search` | 管理员搜索全部用户 | 管理员 |
| DELETE | `/{id}` | 删除用户 | 管理员 |
| PUT | `/{id}/status` | 封/解封用户 | 管理员 |
| GET | `/registration/policy` | 注册审核策略 | 管理员 |
| PUT | `/registration/policy` | 调整注册限额 | 管理员 |
| GET | `/registration/pending` | 待审核注册列表 | 管理员 |
| PUT | `/registration/{id}/approve` | 同意注册 | 管理员 |
| PUT | `/registration/{id}/reject` | 拒绝注册 | 管理员 |

### 标签管理 `/api/tag`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/categories` | 按分类查询全部标签 | 登录 |

### 聊天管理 `/api/chat`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/message/send` | 发送消息 | 登录 |
| GET | `/messages` | 查询消息历史(分页) | 登录 |
| GET | `/conversations` | 会话列表+未读数 | 登录 |
| GET | `/conversation/{id}` | 单个会话详情 | 登录 |
| PUT | `/conversation/{id}/read` | 已读标记 | 登录 |
| PUT | `/conversation/{id}/close` | 关闭会话 | 登录 |
| GET | `/conversation/with/{userId}` | 查找与某用户的会话ID | 登录 |

### 打牌记账 `/api/card-room`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/create` | 创建房间(6位房间号) | 登录 |
| POST | `/join` | 加入房间 | 登录 |
| GET | `/{roomId}` | 房间详情 | 登录 |
| GET | `/active-room` | 当前活跃房间 | 登录 |
| GET | `/history` | 历史房间记录 | 登录 |
| GET | `/ranking` | 牌友排名 | 登录 |
| POST | `/{roomId}/leave` | 退出房间 | 登录 |
| POST | `/{roomId}/transfer` | 记一笔收支 | 登录 |
| POST | `/{roomId}/fund` | 资金平摊 | 登录 |
| POST | `/{roomId}/end` | 房主结束房间 | 登录 |
| POST | `/{roomId}/round/{roundId}/undo` | 申请撤销收支 | 登录 |
| POST | `/{roomId}/fund/{fundId}/undo` | 申请撤销资金 | 登录 |
| POST | `/{roomId}/undo/{undoId}/approve` | 同意撤销 | 登录 |

## 配置说明

| 文件 | 用途 |
|------|------|
| `application.yaml` | 主配置，所有敏感值使用 `${ENV_VAR}` 占位 |
| `application-prod.yaml` | 生产环境覆盖（禁用 Swagger、启用 secure cookie） |

关键环境变量见 [部署文档](../docs/deploy.md)。

## 数据库

- 13 个业务实体 + 1 个 `app_setting` 表
- MyBatis-Plus 逻辑删除（@TableLogic）
- `map-underscore-to-camel-case: false`，列名与 Java 字段名一致

## 启动

```bash
./mvnw spring-boot:run
# 或
java -jar target/matchmate-server-0.0.1-SNAPSHOT.jar
```

Swagger UI（开发环境）：`http://localhost:8080/api/swagger-ui.html`
