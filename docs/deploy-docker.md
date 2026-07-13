# MatchMate Docker 部署指南

> 适用于使用 Docker Compose 部署 MatchMate 的 Ubuntu 服务器。
>
> MatchMate 在线体验：[https://mate.cinoo.xyz](https://mate.cinoo.xyz)。网页不定期开放，可能暂时无法访问，请稍后再试。

## 一、部署架构

```text
浏览器
  └─ 80/443 → Nginx（matchmate-mobile）
                 └─ /api/* → Spring Boot（matchmate-server）
                                  ├─ MySQL
                                  └─ Redis
```

仓库中的 `docker-compose.yml` 采用以下网络边界：

- Nginx 的 80 端口对外提供产品页面。
- 后端仅绑定宿主机 `127.0.0.1:8080`，用于本机健康检查。
- MySQL 和 Redis 不发布宿主机端口，只允许 Compose 内部网络访问。
- MySQL 使用独立的 `matchmate` 应用账号，root 密码只用于数据库容器管理。

## 二、准备服务器

推荐使用受支持的 Ubuntu LTS，并按照 [Docker 官方 Ubuntu 安装文档](https://docs.docker.com/engine/install/ubuntu/) 安装 Docker Engine 与 Compose 插件。

验证安装：

```bash
docker --version
docker compose version
```

防火墙或云安全组只需要开放：

| 端口 | 用途 |
| --- | --- |
| 22 | SSH，建议限制来源地址 |
| 80 | HTTP |
| 443 | HTTPS，配置证书后开放 |

不要对公网开放 3306、6379 或 8080。

## 三、获取项目

推荐直接克隆公开仓库：

```bash
sudo mkdir -p /opt/matchmate
sudo chown "$USER":"$USER" /opt/matchmate
git clone https://github.com/Cinnamo-roll/Cinta-MatchMate.git /opt/matchmate
cd /opt/matchmate
```

如果通过压缩包上传，应确保包中不包含以下内容：

- `.env`、数据库备份和真实日志。
- `node_modules`、`target`、`.m2`、`.git`、`.idea`。
- 任何真实用户资料、访问密钥或服务器配置。

## 四、配置环境变量

复制模板并限制权限：

```bash
cd /opt/matchmate
cp .env.example .env
chmod 600 .env
sudoedit .env
```

至少填写三个独立强密码：

```dotenv
DB_ROOT_PASSWORD=REPLACE_WITH_MYSQL_ROOT_PASSWORD
DB_USERNAME=matchmate
DB_PASSWORD=REPLACE_WITH_MATCHMATE_DB_PASSWORD
REDIS_PASSWORD=REPLACE_WITH_REDIS_PASSWORD

# OSS 可选；不使用头像上传时保持为空
OSS_ENDPOINT=
OSS_ACCESS_KEY_ID=
OSS_ACCESS_KEY_SECRET=
OSS_BUCKET_NAME=
OSS_PUBLIC_BASE_URL=

# 只有确认 HTTPS 可用后才改为 true
SESSION_COOKIE_SECURE=false
MATCHMATE_CORS_ALLOWED_ORIGINS=http://SERVER_IP
```

不要复用数据库 root、应用数据库和 Redis 的密码，也不要使用 `cat .env` 输出配置。

## 五、构建并启动

```bash
cd /opt/matchmate
sudo docker compose build --pull
sudo docker compose up -d
sudo docker compose ps
```

预期状态：

- `matchmate-mysql`、`matchmate-redis` 为 `healthy`。
- `matchmate-server`、`matchmate-nginx` 为 `running` 或 `Up`。

前端镜像使用锁文件和 `npm ci` 构建，后端镜像使用 Maven Wrapper 构建；部署时无需在服务器上临时改写 Dockerfile。

## 六、验证部署

```bash
# 后端仅允许本机访问
curl -fsS http://127.0.0.1:8080/api/actuator/health

# 前端入口
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1/

# 容器状态与最近日志
sudo docker compose ps
sudo docker compose logs --tail=100 server nginx
```

预期健康检查返回 `{"status":"UP"}`，前端返回 `200`。

## 七、创建首个管理员

初始化脚本只包含表结构和标签基础数据，不分发默认用户、管理员或密码。

先通过产品注册自己的账号，然后进入 MySQL 容器：

```bash
cd /opt/matchmate
sudo docker compose exec mysql sh -lc 'mysql -u "$MYSQL_USER" -p "$MYSQL_DATABASE"'
```

输入 `.env` 中的应用数据库密码，确认账号后执行：

```sql
UPDATE user SET userRole = 1 WHERE userAccount = 'YOUR_ADMIN_ACCOUNT';
```

管理员权限应只授予受控账号，不要把管理员账号或密码写入文档、初始化脚本或 GitHub Actions 日志。

## 八、域名与 HTTPS

先把 `YOUR_DOMAIN` 解析到服务器。当前容器内 Nginx 默认提供 HTTP，生产环境可在其前面使用云负载均衡、CDN 或独立反向代理终止 HTTPS。

确认 HTTPS 可用后修改 `.env`：

```dotenv
SESSION_COOKIE_SECURE=true
MATCHMATE_CORS_ALLOWED_ORIGINS=https://YOUR_DOMAIN
```

重新创建后端容器使配置生效：

```bash
sudo docker compose up -d --force-recreate server
curl -fsS https://YOUR_DOMAIN/api/actuator/health
```

不要直接为并不归自己控制的域名申请证书。仓库中的正式体验域名仅作为产品入口，不是部署占位符。

## 九、更新项目

### 9.1 Git 克隆方式

```bash
cd /opt/matchmate
git pull --ff-only
sudo docker compose up -d --build
sudo docker compose ps
```

### 9.2 压缩包方式

重新上传不含 `.env` 和数据文件的新版本，覆盖源码后执行：

```bash
cd /opt/matchmate
sudo docker compose up -d --build
sudo docker compose ps
```

压缩包部署目录没有 `.git` 时不要执行 `git pull`。

## 十、常用运维命令

```bash
cd /opt/matchmate

# 查看状态
sudo docker compose ps

# 查看日志
sudo docker compose logs -f
sudo docker compose logs -f server
sudo docker compose logs --tail=100

# 重启服务
sudo docker compose restart server
sudo docker compose restart nginx

# 查看资源占用
sudo docker stats

# 停止容器但保留数据卷
sudo docker compose down
```

删除数据卷会清空数据库和 Redis 数据。除非已经完成并验证备份，不要执行 `docker compose down -v`。

## 十一、数据库备份安全

数据库备份可能包含用户资料、聊天内容和业务记录，应使用 `600` 权限保存，并按实际安全要求加密和设置保留期限。

备份文件不得：

- 提交到 Git 或上传到公开网盘。
- 放入前端静态目录或 Nginx 可访问目录。
- 通过聊天、Issue、日志或截图公开传递。

恢复备份前应先在隔离环境验证，并明确区分生产数据与演示数据。

## 十二、常见问题

### Compose 提示必填变量未设置

确认已从 `.env.example` 复制出 `.env`，并填写 `DB_ROOT_PASSWORD`、`DB_PASSWORD` 和 `REDIS_PASSWORD`。不要把 `.env` 提交到仓库。

### 前端构建在 `npm ci` 阶段失败

应在本地同步并提交 `package-lock.json`，不要在服务器上把 Dockerfile 临时改成 `npm install`。

### MySQL 或 Redis 无法从公网连接

这是预期行为。数据库与缓存只在 Compose 内部网络开放；应用分别通过服务名 `mysql` 和 `redis` 访问。

### 修改 MySQL 表名大小写设置后仍未生效

`lower_case_table_names` 只会在新数据目录初始化时生效。处理已有数据卷前必须先备份；不要为了排障直接删除生产数据卷。
