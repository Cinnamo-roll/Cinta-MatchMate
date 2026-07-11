# MatchMate Docker 部署指南（实战记录）

> 服务器：阿里云 ECS Ubuntu | 用户：admin | 线上地址：[https://mate.cinoo.xyz](https://mate.cinoo.xyz)
> 最后更新：2026-07-11

---

## 零、为什么要用 Docker 部署

之前手动部署需要：安装 JDK、MySQL、Redis、Nginx，配置 systemd 服务，手动导入 SQL，每次更新都要 scp jar 包重启。一旦服务器重装，全部重来。

Docker 方案只需要一行 `docker compose up -d --build`，数据库自动初始化，所有服务一键启动，环境完全隔离，换服务器也只需要拷贝项目文件夹。

为此项目新增了 **5 个配置文件**：

| 文件 | 位置 | 作用 |
|------|------|------|
| `docker-compose.yml` | 项目根目录 | 编排 MySQL / Redis / 后端 / Nginx 四个容器 |
| `matchmate-server/Dockerfile` | 后端目录 | 将 Spring Boot 源码编译成镜像 |
| `matchmate-mobile/Dockerfile` | 前端目录 | 将 Vue 源码编译成静态文件，用 Nginx 托管 |
| `matchmate-mobile/nginx.conf` | 前端目录 | Nginx 配置：SPA 路由回退 + 反向代理 /api |
| `.env.example` | 项目根目录 | 环境变量模板，部署时复制为 `.env` 填入真实密码 |

下面逐个解释。

### 0.1 docker-compose.yml

编排 4 个容器，位于同一个 Docker 网络 `matchmate-net` 内，彼此通过服务名互相访问。

```
浏览器 → :80 → matchmate-nginx (Nginx)
                    ├─ /          → 静态文件 (Vue SPA)
                    └─ /api/*     → matchmate-server:8080 (Spring Boot)
                                     ├─ → matchmate-mysql:3306
                                     └─ → matchmate-redis:6379
```

关键设计：

- **MySQL 容器**：挂载 `schema.sql` 到 `/docker-entrypoint-initdb.d/01-schema.sql`，容器首次启动时自动执行，完成建库、建表、插入初始数据。Docker Volume `mysql-data` 持久化数据，删除容器不会丢库。
- **Redis 容器**：`command: redis-server --requirepass` 设置密码，Volume `redis-data` 持久化。
- **后端容器**：通过环境变量传入连接信息，`DB_URL=jdbc:mysql://mysql:3306/matchmate`（`mysql` 是服务名而非 localhost），`REDIS_HOST=redis`。
- **Nginx 容器**：反向代理 `/api/` 到后端，同时转发 WebSocket（`/api/ws/chat`、`/api/ws/card/`）。浏览器只访问 80 端口，前后端同源，不存在跨域问题。
- **healthcheck**：MySQL 和 Redis 配置了健康检查，后端 `depends_on` 确保两者 healthy 后才启动，避免数据库还没准备好就连不上。
- **环境变量**：所有机密（密码、OSS 密钥）通过 `${VAR}` 引用 `.env` 文件，不硬编码，不提交 Git。

### 0.2 matchmate-server/Dockerfile —— 后端镜像

```dockerfile
# 阶段 1：编译
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN chmod +x mvnw && sed -i 's/\r$//' mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests -B

# 阶段 2：运行
FROM eclipse-temurin:17-jre-alpine
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

为什么这样设计：

- **两阶段构建（multi-stage build）**：阶段 1 用带 Maven 的镜像编译，阶段 2 只复制 jar 到纯 JRE 镜像。最终镜像不包含 Maven、源码、编译中间产物，体积从 500MB+ 降到约 200MB。
- **先复制 pom.xml 再复制 src**：Docker 构建缓存机制。如果只有源码变了，pom.xml 没变，则 `dependency:go-offline` 那层可以直接用缓存，不需要重新下载依赖，大幅加快 rebuild 速度。
- **`sed -i 's/\r$//' mvnw`**：处理 Windows 系统 git clone 产生的 CRLF 换行符，避免 Linux 容器内 shell 脚本执行失败。
- **`-DskipTests`**：编译时跳过测试——测试在本地开发时跑，部署时不需要在容器里再跑一遍。

### 0.3 matchmate-mobile/Dockerfile —— 前端镜像

```dockerfile
# 阶段 1：编译
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm install
COPY . .
RUN npx vite build

# 阶段 2：Nginx 托管
FROM nginx:stable-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

为什么这样设计：

- **两阶段构建**：阶段 1 是 Node 环境执行 `vite build`，阶段 2 是 Nginx 托管静态文件。最终镜像只包含 Nginx + HTML/JS/CSS，不含 Node.js 和 node_modules。
- **`npm install` 而非 `npm ci`**：本项目 `package-lock.json` 与 `package.json` 的依赖版本不完全同步，`npm ci` 会严格校验并报错退出。使用 `npm install` 可自动修正 lock 文件。
- **先复制 package.json 再复制源码**：同后端一样利用 Docker 缓存。依赖没变时不需要重新 `npm install`。

### 0.4 matchmate-mobile/nginx.conf

```nginx
server {
    listen 80;
    server_name _;            # 匹配所有域名/IP
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;   # Vue SPA 路由回退
    }

    location /api/ {
        proxy_pass http://server:8080;      # 反向代理到后端
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";   # WebSocket 支持
        proxy_read_timeout 86400s;                # WebSocket 长连接不断开
    }
}
```

要点：

- `try_files $uri $uri/ /index.html`：Vue Router history 模式的必须配置，支持直接访问 `/user/profile` 等子路由。
- `proxy_set_header Upgrade` + `Connection "upgrade"`：WebSocket 升级必须的头部。聊天（`/api/ws/chat`）和记账本（`/api/ws/card/`）都依赖 WebSocket。
- `proxy_read_timeout 86400s`：WebSocket 长连接不能被 Nginx 默认 60s 超时断开。

### 0.5 .env.example

```
DB_PASSWORD=your_db_password          # 改成真实 MySQL 密码
REDIS_PASSWORD=your_redis_password    # 改成真实 Redis 密码
OSS_ENDPOINT=                         # 阿里云 OSS，留空则跳过头像上传
...
SESSION_COOKIE_SECURE=false           # 备案+HTTPS 后改为 true
MATCHMATE_CORS_ALLOWED_ORIGINS=...    # 跨域白名单（Nginx 同源部署基本用不到）
```

部署时复制为 `.env`，填入真实值即可。`docker-compose.yml` 中所有 `${VAR}` 都会从这个文件读取。

---

## 一、清空服务器旧数据（如之前手动部署过）

### 1.1 停旧 systemd 服务

```bash
sudo systemctl stop matchmate-server nginx mysql redis-server 2>/dev/null
sudo systemctl disable matchmate-server 2>/dev/null
```

### 1.2 确认端口释放

```bash
sudo ss -tlnp | grep -E ':(80|3306|6379|8080) '
```

如果还有输出，手动 kill 对应 PID。没有输出说明端口干净。

### 1.3 卸载旧 MySQL

```bash
# 如果之前停了 MySQL，先启动才能卸载
sudo systemctl start mysql 2>/dev/null

sudo apt purge mysql-server mysql-client mysql-common mysql-server-core-* mysql-client-core-* -y
sudo rm -rf /var/lib/mysql /etc/mysql /var/log/mysql
```

### 1.4 卸载旧 Redis

```bash
sudo apt purge redis-server -y
sudo rm -rf /var/lib/redis /etc/redis
```

### 1.5 删除旧项目文件

```bash
sudo rm -rf /opt/matchmate
```

---

## 二、安装 Docker

> 国内服务器无法直连 Docker 官方源（`get.docker.com` 会 Connection reset），需用阿里云镜像安装。

### 2.1 添加阿里云 Docker 源并安装

```bash
curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.aliyun.com/docker-ce/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install docker-ce docker-ce-cli containerd.io docker-compose-plugin -y
```

### 2.2 配置镜像加速器

> 阿里云的 registry-mirrors 实测也不稳定，改用 `docker.m.daocloud.io`。

```bash
sudo tee /etc/docker/daemon.json << 'EOF'
{
  "registry-mirrors": ["https://docker.m.daocloud.io"]
}
EOF

sudo systemctl restart docker
sudo systemctl enable docker
```

### 2.3 验证

```bash
docker --version          # 示例：Docker version 29.5.3
docker compose version    # 示例：Docker Compose version v5.1.4

# 拉镜像测试镜像源是否可用
sudo docker pull mysql:8.0
sudo docker pull redis:7-alpine
```

---

## 三、上传项目到服务器

### 3.1 本地打包（Windows PowerShell）

```powershell
cd "D:\Study Code\MatchMate"

tar --exclude='matchmate-mobile/node_modules' `
    --exclude='matchmate-mobile/dist' `
    --exclude='matchmate-server/target' `
    --exclude='matchmate-server/.m2' `
    --exclude='.git' `
    --exclude='.idea' `
    -czf matchmate-docker.tar.gz `
    docker-compose.yml .env.example .dockerignore `
    matchmate-server/Dockerfile matchmate-server/pom.xml `
    matchmate-server/mvnw matchmate-server/mvnw.cmd `
    matchmate-server/.mvn matchmate-server/src `
    matchmate-mobile/Dockerfile matchmate-mobile/nginx.conf `
    matchmate-mobile/package.json matchmate-mobile/package-lock.json `
    matchmate-mobile/index.html matchmate-mobile/vite.config.ts `
    matchmate-mobile/tsconfig.json matchmate-mobile/tsconfig.app.json `
    matchmate-mobile/tsconfig.node.json matchmate-mobile/components.d.ts `
    matchmate-mobile/public matchmate-mobile/src
```

### 3.2 上传

> `admin` 用户无权限直接写 `/opt/`，先传到 home 目录再 `sudo` 移动。

```powershell
# 本地 Windows
scp matchmate-docker.tar.gz admin@你的服务器:~/
```

```bash
# 服务器
sudo mkdir -p /opt/matchmate
sudo tar -xzf ~/matchmate-docker.tar.gz -C /opt/matchmate
ls /opt/matchmate
# 输出：docker-compose.yml  matchmate-mobile  matchmate-server  .env.example  .dockerignore
```

---

## 四、配置环境变量

```bash
cd /opt/matchmate

# 模板复制为正式文件
sudo cp .env.example .env

# 编辑
sudo nano .env
```

编辑后的 `.env` 内容（密码请使用独立强密码；OSS 留空时不启用头像上传）：

```
DB_PASSWORD=<你的 MySQL 强密码>
REDIS_PASSWORD=<你的 Redis 强密码>
OSS_ENDPOINT=
OSS_ACCESS_KEY_ID=
OSS_ACCESS_KEY_SECRET=
OSS_BUCKET_NAME=
OSS_PUBLIC_BASE_URL=
SESSION_COOKIE_SECURE=true
MATCHMATE_CORS_ALLOWED_ORIGINS=https://mate.cinoo.xyz
```

> `Ctrl+O` 回车保存，`Ctrl+X` 退出。`cat .env` 确认。

---

## 五、修改前端 Dockerfile（解决 npm ci 报错）

> `npm ci` 要求 lock 文件与 package.json 完全一致，本项目不完全同步，直接构建会失败。需改为 `npm install`。

```bash
sudo tee /opt/matchmate/matchmate-mobile/Dockerfile << 'EOF'
# Stage 1: Build
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm install
COPY . .
RUN npx vite build

# Stage 2: Serve with Nginx
FROM nginx:stable-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
EOF
```

---

## 六、启动服务

```bash
cd /opt/matchmate
sudo docker compose up -d --build
```

首次构建约 5-10 分钟（下载 Maven 依赖 + npm 包）。成功输出：

```
✔ Container matchmate-mysql   Healthy
✔ Container matchmate-redis   Healthy
✔ Container matchmate-server  Started
✔ Container matchmate-nginx   Started
```

### 6.1 查看容器状态

```bash
sudo docker compose ps
```

| 容器 | 镜像 | 端口 |
|------|------|------|
| matchmate-mysql | mysql:8.0 | 3306 |
| matchmate-redis | redis:7-alpine | 6379 |
| matchmate-server | matchmate-server (自构建) | 8080 |
| matchmate-nginx | matchmate-nginx (自构建) | 80 |

四个全 `Up` 且 `healthy` 即部署成功。

---

## 七、验证

```bash
# 后端健康检查
curl -s http://127.0.0.1:8080/api/actuator/health
# → {"status":"UP"}

# 前端页面 HTTP 状态码
curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/
# → 200
```

本地验证通过后，可访问线上环境：[https://mate.cinoo.xyz](https://mate.cinoo.xyz)。

---

## 八、踩坑记录

| # | 现象 | 原因 | 解决 |
|---|------|------|------|
| 1 | `Connection reset by peer` 安装 Docker | 国内墙 `get.docker.com` | 用阿里云 `mirrors.aliyun.com` 镜像源安装 |
| 2 | `i/o timeout` 拉取 `redis:7-alpine` | `registry-1.docker.io` 不通 | 配 `docker.m.daocloud.io` 镜像加速器 |
| 3 | `npm ci` 报 `Missing: axios@1.18.0 from lock file` | lock 文件与 package.json 不同步 | 改 `npm ci` 为 `npm install` |
| 4 | `scp` 到 `/opt/` 报 Permission denied | admin 用户无 `/opt/` 写权限 | 先传 `~/` 再 `sudo mv` |
| 5 | `docker compose up` 提示 `DB_PASSWORD is not set` | 文件名是 `.env.example` 不是 `.env` | `cp .env.example .env` |
| 6 | 打牌记账本「网络异常」| 后端报 `Table 'matchmate.cardRoom' doesn't exist`，Linux MySQL 默认大小写敏感，表名是 `cardroom` | MySQL 容器加 `command: --lower_case_table_names=1`，删卷重建 |

---

## 九、常用运维命令

```bash
cd /opt/matchmate

# ── 日志 ──
sudo docker compose logs -f              # 全部容器实时日志
sudo docker compose logs -f server       # 只看后端
sudo docker compose logs --tail=100      # 最近 100 行

# ── 重启 ──
sudo docker compose restart server       # 只重启后端
sudo docker compose down && sudo docker compose up -d   # 全部重建

# ── 更新代码后重新部署 ──
git pull
sudo docker compose up -d --build        # --build 强制重新编译

# ── 进入容器 ──
sudo docker exec -it matchmate-mysql mysql -u root -p
sudo docker exec -it matchmate-redis redis-cli -a '<你的 Redis 密码>'
sudo docker exec -it matchmate-server sh

# ── 查看资源占用 ──
sudo docker stats

# ── 停止 ──
sudo docker compose down                 # 停容器，保留数据卷
sudo docker compose down -v              # 停容器 + 删除数据卷（数据库清空！慎用）
```

---

## 十、生产域名与 HTTPS

```bash
# 1. 修改 nginx.conf 的 server_name
sudo nano /opt/matchmate/matchmate-mobile/nginx.conf
# 把 server_name _; 改为 server_name mate.cinoo.xyz;

# 2. 修改 .env 开启 HTTPS Cookie
sudo nano /opt/matchmate/.env
# SESSION_COOKIE_SECURE=true
# MATCHMATE_CORS_ALLOWED_ORIGINS=https://mate.cinoo.xyz

# 3. 重新构建 Nginx 容器
sudo docker compose up -d --build nginx

# 4. 申请 SSL 证书（certbot 需占用 80 端口，先停 nginx）
sudo docker compose stop nginx
sudo apt install certbot -y
sudo certbot certonly --standalone -d mate.cinoo.xyz
sudo docker compose start nginx

# 5. 将证书挂载进 Nginx（需修改 nginx.conf 添加 443 server 块 + ssl_certificate）
```

---

## 十一、服务器目录结构

```
/opt/matchmate/
├── .env                          # 环境变量（密码、OSS 等）【不入 Git】
├── .env.example                  # 环境变量模板
├── .dockerignore                 # Docker 构建排除规则
├── docker-compose.yml            # 容器编排文件
│
├── matchmate-server/             # 后端
│   ├── Dockerfile                # 后端镜像构建文件
│   ├── pom.xml                   # Maven 依赖
│   ├── mvnw / mvnw.cmd           # Maven Wrapper
│   ├── .mvn/                     # Maven Wrapper 配置
│   └── src/                      # Java 源码 + 配置 + schema.sql
│
├── matchmate-mobile/             # 前端
│   ├── Dockerfile                # 前端镜像构建文件
│   ├── nginx.conf                # Nginx 配置
│   ├── package.json / package-lock.json
│   ├── index.html / vite.config.ts
│   ├── tsconfig*.json
│   ├── public/                   # 静态资源
│   └── src/                      # Vue 源码
│
└── docs/
    ├── deploy.md                 # 旧手动部署文档
    └── deploy-docker.md          # 本文档
```

---

## 十二、完整部署流程回顾

从头到尾，在全新服务器上部署只需要这几步：

```bash
# 1. 装 Docker（见第二章）
# 2. 上传项目（见第三章）
# 3. 配 .env（见第四章）
# 4. 改前端 Dockerfile（见第五章）
# 5. 启动
cd /opt/matchmate
sudo docker compose up -d --build

# 6. 验证
curl http://127.0.0.1:8080/api/actuator/health
curl http://127.0.0.1/
```

10 分钟内从零到上线。
