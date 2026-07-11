# MatchMate 轻量应用服务器部署文档

> 从零开始手动部署 | 系统：Ubuntu | 线上地址：[https://mate.cinoo.xyz](https://mate.cinoo.xyz)

---

## 项目架构

| 组件 | 技术栈 | 端口 |
|------|--------|------|
| 前端 | Vue 3 + Vite + Vant | Nginx:80 |
| 后端 | Spring Boot 4.0.6 + Java 17 + Maven | 8080 |
| 数据库 | MySQL 8 | 3306 |
| 缓存 | Redis | 6379 |
| 文件存储 | 阿里云 OSS | — |
| 反向代理 | Nginx | 80 |

---

## 一、服务器环境安装

### 1.1 更新系统

```bash
sudo apt update && sudo apt upgrade -y
```

### 1.2 安装 JDK 17

```bash
sudo apt install openjdk-17-jdk -y
java -version
```

### 1.3 安装 MySQL

```bash
sudo apt install mysql-server -y
sudo systemctl start mysql
sudo systemctl enable mysql
sudo mysql_secure_installation
```

安全初始化选项：密码策略选 0（LOW），全部选 Y。

```bash
# 进入 MySQL 改 root 密码认证方式
sudo mysql
```

```sql
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '你的密码';
CREATE DATABASE matchmate CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'matchmate'@'localhost' IDENTIFIED BY '你的密码';
GRANT ALL PRIVILEGES ON matchmate.* TO 'matchmate'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### 1.4 安装 Redis

```bash
sudo apt install redis-server -y
sudo systemctl start redis-server
sudo systemctl enable redis-server
sudo sed -i 's/# requirepass foobared/requirepass 你的密码/' /etc/redis/redis.conf
sudo systemctl restart redis-server
redis-cli -a 你的密码 ping   # 应返回 PONG
```

### 1.5 安装 Nginx

```bash
sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx
```

---

## 二、本地构建

### 2.1 后端

```powershell
cd matchmate-server
.\mvnw.cmd clean package -DskipTests
# 产物：target/matchmate-server-0.0.1-SNAPSHOT.jar
```

### 2.2 前端

```powershell
cd matchmate-mobile
npm run build
# 产物：dist/
```

---

## 三、上传到服务器

### 3.1 创建部署目录

```bash
sudo mkdir -p /opt/matchmate
sudo chown $USER:$USER /opt/matchmate
```

### 3.2 上传文件

```powershell
# 后端 jar
scp "matchmate-server\target\matchmate-server-0.0.1-SNAPSHOT.jar" admin@你的IP:/opt/matchmate/

# 前端（先打包）
cd matchmate-mobile
tar -czf dist.tar.gz dist
scp dist.tar.gz admin@你的IP:/opt/matchmate/

# 数据库导出文件
scp local-dump.sql admin@你的IP:/opt/matchmate/
```

### 3.3 服务器解压前端

```bash
cd /opt/matchmate
tar -xzf dist.tar.gz
```

---

## 四、导入数据库

### 4.1 本地导出

```powershell
mysqldump -u root -p -r local-dump.sql matchmate
```

> 用 `-r` 避免 PowerShell 编码问题

### 4.2 服务器导入

```bash
mysql -u root -p你的密码 matchmate < /opt/matchmate/local-dump.sql
```

验证：
```bash
mysql -u root -p你的密码 -e "SHOW TABLES;" matchmate
```

---

## 五、环境变量配置

创建 `/opt/matchmate/.env`：

```bash
cat > /opt/matchmate/.env << 'EOF'
DB_URL=jdbc:mysql://localhost:3306/matchmate
DB_USERNAME=root
DB_PASSWORD=你的数据库密码
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=你的Redis密码
REDIS_DATABASE=0
SPRING_PROFILES_ACTIVE=prod
SESSION_COOKIE_SECURE=false
MATCHMATE_CORS_ALLOWED_ORIGINS=http://你的服务器IP
CACHE_ENABLED=true
CACHE_WARMUP_ENABLED=true
MANAGEMENT_ENDPOINTS=health
EOF
```

### OSS 配置（追加到 .env）

```bash
cat >> /opt/matchmate/.env << 'EOF'
OSS_ENDPOINT=oss-cn-shenzhen.aliyuncs.com
OSS_ACCESS_KEY_ID=你的AccessKeyID
OSS_ACCESS_KEY_SECRET=你的AccessKeySecret
OSS_BUCKET_NAME=你的Bucket名称
OSS_PUBLIC_BASE_URL=https://你的Bucket名称.oss-cn-shenzhen.aliyuncs.com
EOF
```

> OSS 需要先在阿里云控制台创建 Bucket（公共读权限）和 AccessKey

---

## 六、Systemd 服务

创建 `/etc/systemd/system/matchmate-server.service`：

```bash
sudo tee /etc/systemd/system/matchmate-server.service << 'EOF'
[Unit]
Description=MatchMate Server
After=network.target mysql.service redis-server.service

[Service]
Type=simple
User=admin
WorkingDirectory=/opt/matchmate
EnvironmentFile=/opt/matchmate/.env
ExecStart=/usr/bin/java -jar /opt/matchmate/matchmate-server-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
```

启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable matchmate-server
sudo systemctl start matchmate-server
```

---

## 七、Nginx 配置

```bash
sudo tee /etc/nginx/sites-available/matchmate << 'EOF'
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
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
EOF
```

启用：

```bash
sudo ln -sf /etc/nginx/sites-available/matchmate /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl restart nginx
```

---

## 八、防火墙开放端口

轻量应用服务器控制台 → 防火墙 → 添加规则：

| 应用类型 | 协议 | 端口 | 来源 |
|----------|------|------|------|
| 自定义 | TCP | 80 | 0.0.0.0/0 |

> 如果用的是 ECS，在安全组里添加入方向规则。

---

## 九、验证

```bash
# 后端健康检查
curl -s http://127.0.0.1:8080/api/actuator/health
# → {"status":"UP"}

# Nginx + 前端
curl -s http://127.0.0.1/ | head -5
# → <!doctype html>...

# 查看日志
journalctl -u matchmate-server -f
```

本地验证通过后，可访问线上环境：[https://mate.cinoo.xyz](https://mate.cinoo.xyz)。

---

## 十、常用运维命令

```bash
# 查看后端日志
journalctl -u matchmate-server -f

# 重启后端
sudo systemctl restart matchmate-server

# 查看后端状态
sudo systemctl status matchmate-server

# 重载 Nginx
sudo systemctl reload nginx

# 查看 MySQL 状态
sudo systemctl status mysql

# 查看 Redis 状态
sudo systemctl status redis-server

# 更新部署（jar 包替换后）
sudo systemctl restart matchmate-server

# 更新前端（dist 替换后无需重启）
sudo systemctl reload nginx
```

---

## 十一、生产域名与 HTTPS

```bash
# 1. 修改 Nginx server_name
sudo nano /etc/nginx/sites-available/matchmate
# 把 server_name _; 改为 server_name mate.cinoo.xyz;

# 2. 修改 CORS
sudo nano /opt/matchmate/.env
# MATCHMATE_CORS_ALLOWED_ORIGINS=https://mate.cinoo.xyz
# SESSION_COOKIE_SECURE=true

# 3. 申请免费 SSL 证书
sudo apt install certbot python3-certbot-nginx -y
sudo certbot --nginx -d mate.cinoo.xyz

# 4. 防火墙开放 443 端口

# 5. 重启服务
sudo systemctl restart matchmate-server
sudo systemctl reload nginx
```

---

## 十二、环境变量速查

| 变量 | 说明 | 示例 |
|------|------|------|
| DB_URL | 数据库连接 | `jdbc:mysql://localhost:3306/matchmate` |
| DB_USERNAME | 数据库用户 | `root` |
| DB_PASSWORD | 数据库密码 | `your_password` |
| REDIS_HOST | Redis 地址 | `localhost` |
| REDIS_PASSWORD | Redis 密码 | `your_password` |
| SPRING_PROFILES_ACTIVE | 激活配置 | `prod` |
| SESSION_COOKIE_SECURE | HTTPS Cookie | 备案前 `false`，后 `true` |
| MATCHMATE_CORS_ALLOWED_ORIGINS | 跨域白名单 | IP 或域名 |
| OSS_ENDPOINT | OSS 地域节点 | `oss-cn-shenzhen.aliyuncs.com` |
| OSS_ACCESS_KEY_ID | 阿里云 AK | `LTAI5t...` |
| OSS_ACCESS_KEY_SECRET | 阿里云 SK | `your_secret` |
| OSS_BUCKET_NAME | OSS Bucket 名 | `matchmate-xxx` |
| OSS_PUBLIC_BASE_URL | OSS 公开访问地址 | `https://xxx.oss-cn-shenzhen.aliyuncs.com` |

---

## 目录结构（服务器）

```
/opt/matchmate/
  ├── .env                          # 环境变量(DB/Redis/OSS/CORS 等)
  ├── matchmate-server-0.0.1-SNAPSHOT.jar  # 后端 Spring Boot 可执行 jar
  ├── local-dump.sql                # 全量备份(mysqldump 导出: 表结构+数据)
  └── dist/                         # 前端静态文件(Vite 构建产物)
       ├── index.html               # SPA 入口
       ├── assets/                  # JS/CSS/图片 等静态资源
       └── ...
```
