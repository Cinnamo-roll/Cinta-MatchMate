# MatchMate Ubuntu 手动部署指南

> 适用于 Ubuntu 服务器上的 JDK、MySQL、Redis、Nginx 手动部署。
>
> MatchMate 在线体验：[https://mate.cinoo.xyz](https://mate.cinoo.xyz)。网页不定期开放，可能暂时无法访问，请稍后再试。

## 部署前约定

文中的占位符必须替换为自己的值：

| 占位符 | 含义 |
| --- | --- |
| `SERVER_IP` | 服务器公网 IP |
| `DEPLOY_USER` | 用于 SSH/SCP 的服务器账号 |
| `YOUR_DOMAIN` | 自己解析到服务器的域名 |
| `REPLACE_WITH_*` | 需要自行生成并妥善保存的强密码或密钥 |

生产环境应遵循以下原则：

- 只对公网开放 80/443，MySQL、Redis 和后端端口仅允许本机访问。
- 不把 `.env`、数据库备份、日志或真实用户数据提交到 GitHub。
- 数据库使用独立的 `matchmate` 账号，应用不使用 MySQL `root`。
- 所有密码通过交互式提示或受权限保护的配置文件输入，避免出现在 Shell 历史中。

## 一、安装运行环境

### 1.1 更新系统

```bash
sudo apt update
sudo apt upgrade -y
```

### 1.2 安装 JDK 17

```bash
sudo apt install openjdk-17-jdk -y
java -version
```

### 1.3 安装并初始化 MySQL 8

```bash
sudo apt install mysql-server -y
sudo systemctl enable --now mysql
sudo mysql_secure_installation
sudo mysql
```

在 MySQL 控制台执行，先替换密码占位符：

```sql
CREATE DATABASE matchmate CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'matchmate'@'localhost' IDENTIFIED BY 'REPLACE_WITH_DB_PASSWORD';
GRANT ALL PRIVILEGES ON matchmate.* TO 'matchmate'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### 1.4 安装并配置 Redis

```bash
sudo apt install redis-server -y
sudo systemctl enable --now redis-server
sudoedit /etc/redis/redis.conf
```

在配置文件中设置强密码：

```text
requirepass REPLACE_WITH_REDIS_PASSWORD
```

保存后重启并通过交互式密码提示验证：

```bash
sudo systemctl restart redis-server
redis-cli --askpass ping
```

成功时返回 `PONG`。

### 1.5 安装 Nginx

```bash
sudo apt install nginx -y
sudo systemctl enable --now nginx
```

## 二、本地构建

在项目根目录执行。

### 2.1 构建后端

```powershell
cd matchmate-server
./mvnw.cmd clean package
```

构建产物为 `matchmate-server/target/matchmate-server-0.0.1-SNAPSHOT.jar`。

### 2.2 构建前端

```powershell
cd matchmate-mobile
npm.cmd ci
npm.cmd run build
tar -czf dist.tar.gz dist
```

## 三、上传部署文件

先在服务器创建目录：

```bash
sudo mkdir -p /opt/matchmate
```

从本地项目根目录上传文件。请替换 `DEPLOY_USER` 和 `SERVER_IP`：

```powershell
scp matchmate-server/target/matchmate-server-0.0.1-SNAPSHOT.jar DEPLOY_USER@SERVER_IP:/tmp/matchmate-server.jar
scp matchmate-mobile/dist.tar.gz DEPLOY_USER@SERVER_IP:/tmp/matchmate-dist.tar.gz
scp matchmate-server/src/main/resources/schema.sql DEPLOY_USER@SERVER_IP:/tmp/matchmate-schema.sql
```

在服务器上安装文件：

```bash
sudo install -m 640 /tmp/matchmate-server.jar /opt/matchmate/matchmate-server.jar
sudo tar -xzf /tmp/matchmate-dist.tar.gz -C /opt/matchmate
sudo install -m 600 /tmp/matchmate-schema.sql /opt/matchmate/schema.sql
rm -f /tmp/matchmate-server.jar /tmp/matchmate-dist.tar.gz /tmp/matchmate-schema.sql
```

## 四、初始化数据库

```bash
mysql -u matchmate -p matchmate < /opt/matchmate/schema.sql
```

命令会交互式询问密码。`schema.sql` 只包含表结构和标签基础数据，不包含用户、管理员、密码或线上业务数据。

首次部署后，通过网页注册自己的账号。如需管理员权限，在服务器进入数据库：

```bash
mysql -u matchmate -p matchmate
```

确认账号无误后执行：

```sql
UPDATE user SET userRole = 1 WHERE userAccount = 'YOUR_ADMIN_ACCOUNT';
```

## 五、配置环境变量

创建只允许 root 读取的环境文件：

```bash
sudo install -o root -g root -m 600 /dev/null /opt/matchmate/.env
sudoedit /opt/matchmate/.env
```

写入以下内容并替换所有 `REPLACE_WITH_*`：

```dotenv
DB_URL=jdbc:mysql://localhost:3306/matchmate
DB_USERNAME=matchmate
DB_PASSWORD=REPLACE_WITH_DB_PASSWORD
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=REPLACE_WITH_REDIS_PASSWORD
REDIS_DATABASE=0
SPRING_PROFILES_ACTIVE=prod
SESSION_COOKIE_SECURE=false
MATCHMATE_CORS_ALLOWED_ORIGINS=http://SERVER_IP
CACHE_ENABLED=true
CACHE_WARMUP_ENABLED=true
MANAGEMENT_ENDPOINTS=health

# OSS 可选；不使用头像上传时保持为空
OSS_ENDPOINT=
OSS_ACCESS_KEY_ID=
OSS_ACCESS_KEY_SECRET=
OSS_BUCKET_NAME=
OSS_PUBLIC_BASE_URL=
```

不要用 `cat .env` 检查内容，以免密钥出现在终端录屏或日志中。

## 六、创建 Systemd 服务

创建独立的低权限服务账号：

```bash
sudo useradd --system --home /opt/matchmate --shell /usr/sbin/nologin matchmate 2>/dev/null || true
sudo chown -R matchmate:matchmate /opt/matchmate
sudo chown root:root /opt/matchmate/.env
sudo chmod 600 /opt/matchmate/.env
```

`.env` 保持 `root:root` 和 `600` 权限。创建服务：

```bash
sudo tee /etc/systemd/system/matchmate-server.service > /dev/null << 'EOF'
[Unit]
Description=MatchMate Server
After=network.target mysql.service redis-server.service

[Service]
Type=simple
User=matchmate
WorkingDirectory=/opt/matchmate
EnvironmentFile=/opt/matchmate/.env
ExecStart=/usr/bin/java -jar /opt/matchmate/matchmate-server.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now matchmate-server
sudo systemctl status matchmate-server
```

## 七、配置 Nginx

```bash
sudo tee /etc/nginx/sites-available/matchmate > /dev/null << 'EOF'
server {
    listen 80;
    server_name _;

    root /opt/matchmate/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
EOF

sudo ln -sf /etc/nginx/sites-available/matchmate /etc/nginx/sites-enabled/matchmate
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

## 八、验证

```bash
curl -fsS http://127.0.0.1:8080/api/actuator/health
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1/
journalctl -u matchmate-server --no-pager -n 100
```

预期健康检查返回 `{"status":"UP"}`，前端返回 `200`。

## 九、配置域名与 HTTPS

先把 `YOUR_DOMAIN` 解析到服务器，然后修改 Nginx：

```bash
sudoedit /etc/nginx/sites-available/matchmate
```

把 `server_name _;` 改成 `server_name YOUR_DOMAIN;`，再申请证书：

```bash
sudo apt install certbot python3-certbot-nginx -y
sudo certbot --nginx -d YOUR_DOMAIN
```

随后修改 `/opt/matchmate/.env`：

```dotenv
SESSION_COOKIE_SECURE=true
MATCHMATE_CORS_ALLOWED_ORIGINS=https://YOUR_DOMAIN
```

重启后端并验证：

```bash
sudo systemctl restart matchmate-server
sudo nginx -t
sudo systemctl reload nginx
curl -fsS https://YOUR_DOMAIN/api/actuator/health
```

## 十、更新与运维

替换新构建的后端文件后：

```bash
sudo install -o matchmate -g matchmate -m 640 /tmp/matchmate-server.jar /opt/matchmate/matchmate-server.jar
sudo systemctl restart matchmate-server
```

替换前端：

```bash
sudo rm -rf /opt/matchmate/dist
sudo tar -xzf /tmp/matchmate-dist.tar.gz -C /opt/matchmate
sudo chown -R matchmate:matchmate /opt/matchmate/dist
sudo systemctl reload nginx
```

常用命令：

```bash
journalctl -u matchmate-server -f
sudo systemctl restart matchmate-server
sudo systemctl reload nginx
sudo systemctl status mysql redis-server matchmate-server nginx
```

数据库备份可能包含用户资料和聊天数据，应限制文件权限、加密保存，并且绝不能提交到公开仓库。
