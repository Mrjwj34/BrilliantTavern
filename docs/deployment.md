# BrilliantTavern 部署指南

本文档提供了 BrilliantTavern AI 角色扮演聊天应用的完整部署指南，支持 Docker Compose 一键部署。

## 目录

- [系统要求](#系统要求)
- [前置准备](#前置准备)
- [快速开始](#快速开始)
- [详细配置](#详细配置)
- [服务管理](#服务管理)
- [故障排除](#故障排除)
- [生产环境优化](#生产环境优化)

## 系统要求

### 最低配置
- **操作系统**: Linux (Ubuntu 20.04+), macOS, Windows with WSL2
- **CPU**: 2核心以上
- **内存**: 4GB RAM
- **存储**: 20GB 可用空间
- **网络**: 稳定的互联网连接

### 推荐配置
- **CPU**: 4核心以上
- **内存**: 8GB RAM
- **存储**: 50GB SSD
- **GPU**: 支持CUDA的GPU（用于本地TTS服务）

### 软件依赖
- Docker Engine 20.10+
- Docker Compose 2.0+
- Google Cloud CLI (gcloud)

## 前置准备

### 1. 安装 Docker

#### Ubuntu/Debian
```bash
# 更新包索引
sudo apt-get update

# 安装Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 添加用户到docker组
sudo usermod -aG docker $USER

# 安装Docker Compose
sudo apt-get install docker-compose-plugin
```

#### CentOS/RHEL
```bash
# 安装Docker
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo yum install docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 启动Docker服务
sudo systemctl start docker
sudo systemctl enable docker
```

#### macOS
```bash
# 使用Homebrew安装
brew install --cask docker

# 或下载Docker Desktop for Mac
# https://www.docker.com/products/docker-desktop
```

#### Windows
下载并安装 Docker Desktop for Windows:
https://www.docker.com/products/docker-desktop

### 2. 安装 Google Cloud CLI

#### Linux/macOS
```bash
# 下载并安装gcloud CLI
curl https://sdk.cloud.google.com | bash
exec -l $SHELL

# 或使用包管理器
# Ubuntu/Debian
sudo apt-get install google-cloud-cli

# macOS
brew install google-cloud-sdk
```

#### Windows
下载并安装 Google Cloud CLI:
https://cloud.google.com/sdk/docs/install

### 3. 配置 Google Cloud 认证

```bash
# 登录Google Cloud
gcloud auth login

# 设置应用默认凭据（重要！）
gcloud auth application-default login

# 设置项目ID
gcloud config set project YOUR_PROJECT_ID

# 验证认证状态
gcloud auth list
gcloud config list
```

## 快速开始

### 1. 获取代码
```bash
# 克隆或下载项目代码
git clone https://github.com/your-repo/brilliant-tavern.git
cd brilliant-tavern/app
```

### 2. 配置环境变量
```bash
# 复制环境变量模板
cp .env.example .env

# 编辑配置文件
nano .env
```

**必须配置的关键参数**:
```bash
# Google Cloud配置
VERTEX_AI_PROJECT_ID=your-gcp-project-id
VERTEX_AI_LOCATION=us-central1

# 数据库密码
POSTGRES_PASSWORD=your_secure_password
REDIS_PASSWORD=your_redis_password

# JWT密钥（生产环境必须更改）
JWT_SECRET=your_very_secure_jwt_secret_key_here_at_least_32_characters

# 域名配置（生产环境）
CORS_ALLOWED_ORIGINS=https://your-domain.com
WEBSOCKET_ALLOWED_ORIGINS=https://your-domain.com
```

### 3. 启动服务
```bash
# 构建并启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 4. 验证部署
```bash
# 检查所有服务是否健康
docker-compose ps

# 访问应用
# 前端: http://localhost:80
# 后端API: http://localhost:80/api
# API文档: http://localhost:80/api/swagger-ui.html
```

## 详细配置

### 数据库初始化

应用首次启动时会自动执行数据库初始化脚本 `scripts/init_database.sql`。

如需手动初始化：
```bash
# 进入数据库容器
docker-compose exec postgres psql -U postgres -d brilliant_tavern

# 或使用外部工具连接
# 主机: localhost
# 端口: 5432
# 数据库: brilliant_tavern
# 用户名: postgres
# 密码: （.env中配置的POSTGRES_PASSWORD）
```

### TTS服务配置

#### 使用外部TTS服务
```bash
# 在.env中配置外部TTS服务URL
TTS_SERVICE_URL=http://your-tts-server:9880
```

#### 启用内置Fish Speech TTS服务
```bash
# 启动包含TTS服务的完整栈
docker-compose --profile tts up -d
```

**注意**: 内置TTS服务需要GPU支持，请确保：
1. 安装了NVIDIA Docker运行时
2. 系统有可用的CUDA GPU

### 环境变量详解

参考 `.env.example` 文件中的详细说明，主要配置项包括：

- **服务器配置**: 端口、上下文路径
- **数据库配置**: 连接信息、凭据
- **Redis配置**: 缓存服务配置
- **认证配置**: JWT密钥和过期时间
- **AI服务配置**: Vertex AI配置
- **TTS配置**: 语音合成服务配置
- **CORS配置**: 跨域访问控制

## 服务管理

### 基本操作
```bash
# 启动所有服务
docker-compose up -d

# 停止所有服务
docker-compose down

# 重启特定服务
docker-compose restart backend

# 查看服务状态
docker-compose ps

# 查看实时日志
docker-compose logs -f backend

# 进入容器shell
docker-compose exec backend sh
```

### 数据备份与恢复

#### 数据库备份
```bash
# 创建备份
docker-compose exec postgres pg_dump -U postgres brilliant_tavern > backup_$(date +%Y%m%d_%H%M%S).sql

# 恢复备份
docker-compose exec -T postgres psql -U postgres brilliant_tavern < backup_file.sql
```

#### 用户数据备份
```bash
# 备份用户上传的文件
docker run --rm -v brilliant-tavern_app_uploads:/data -v $(pwd):/backup alpine tar czf /backup/uploads_backup_$(date +%Y%m%d_%H%M%S).tar.gz -C /data .
```

### 更新部署

```bash
# 停止服务
docker-compose down

# 拉取最新代码/镜像
git pull
docker-compose pull

# 重新构建（如果有代码更改）
docker-compose build

# 启动更新后的服务
docker-compose up -d
```

## 故障排除

### 常见问题

#### 1. Google Cloud认证失败
```bash
# 症状：GenAI预热失败，无法访问Vertex AI
# 解决：
gcloud auth application-default login
# 确保Docker可以访问认证文件
ls -la ~/.config/gcloud/
```

#### 2. 数据库连接失败
```bash
# 检查数据库服务状态
docker-compose logs postgres

# 检查网络连接
docker-compose exec backend nslookup postgres
```

#### 3. 前端无法访问后端API
```bash
# 检查nginx配置
docker-compose logs nginx

# 检查后端服务状态
docker-compose logs backend

# 检查端口映射
docker-compose ps
```

#### 4. TTS服务无法连接
```bash
# 检查TTS服务配置
echo $TTS_SERVICE_URL

# 测试TTS服务连通性
curl -X GET $TTS_SERVICE_URL/health
```

### 调试命令

```bash
# 查看详细服务状态
docker-compose ps -a

# 查看资源使用情况
docker stats

# 查看网络配置
docker network ls
docker network inspect brilliant-tavern_brilliant-tavern-network

# 查看卷挂载
docker volume ls
docker volume inspect brilliant-tavern_postgres_data
```

### 日志收集

```bash
# 收集所有服务日志
docker-compose logs --no-color > brilliant-tavern-logs.txt

# 持续监控特定服务
docker-compose logs -f --tail=100 backend
```

## 生产环境优化

### 1. 安全配置

#### 更新默认密码
```bash
# 生成强密码
openssl rand -base64 32

# 更新.env文件中的敏感信息
JWT_SECRET=新生成的强密钥
POSTGRES_PASSWORD=新生成的强密码
REDIS_PASSWORD=新生成的强密码
```

#### 配置HTTPS
```bash
# 获取SSL证书（Let's Encrypt）
sudo apt-get install certbot

# 为域名申请证书
sudo certbot certonly --standalone -d your-domain.com

# 更新nginx配置启用HTTPS
# 参考nginx.conf中的HTTPS配置模板
```

#### 限制网络访问
```bash
# 使用防火墙限制端口访问
sudo ufw allow 80
sudo ufw allow 443
sudo ufw deny 5432  # 禁止外部访问数据库
sudo ufw deny 6379  # 禁止外部访问Redis
```

### 2. 性能优化

#### 资源限制
在 `docker-compose.yml` 中添加资源限制：
```yaml
services:
  backend:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
```

#### 数据库优化
```bash
# 调整PostgreSQL配置
# 在postgres服务中添加环境变量：
environment:
  POSTGRES_SHARED_PRELOAD_LIBRARIES: 'pg_stat_statements'
  POSTGRES_MAX_CONNECTIONS: '200'
  POSTGRES_SHARED_BUFFERS: '256MB'
```

### 3. 监控配置

#### 健康检查
所有服务都配置了健康检查，可以通过以下方式监控：
```bash
# 检查服务健康状态
docker-compose ps

# 自定义监控脚本
cat > health_check.sh << 'EOF'
#!/bin/bash
services=("postgres" "redis" "backend" "nginx")
for service in "${services[@]}"; do
    status=$(docker-compose ps -q $service | xargs docker inspect --format='{{.State.Health.Status}}' 2>/dev/null)
    echo "$service: $status"
done
EOF

chmod +x health_check.sh
./health_check.sh
```

#### 日志管理
```bash
# 配置日志轮转
cat > /etc/logrotate.d/docker-compose << 'EOF'
/var/lib/docker/containers/*/*.log {
    rotate 7
    daily
    compress
    size=1M
    missingok
    delaycompress
    copytruncate
}
EOF
```

### 4. 备份策略

#### 自动备份脚本
```bash
cat > backup.sh << 'EOF'
#!/bin/bash
BACKUP_DIR="/opt/backups/brilliant-tavern"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

# 数据库备份
docker-compose exec -T postgres pg_dump -U postgres brilliant_tavern > $BACKUP_DIR/db_$DATE.sql

# 用户数据备份
docker run --rm -v brilliant-tavern_app_uploads:/data -v $BACKUP_DIR:/backup alpine tar czf /backup/uploads_$DATE.tar.gz -C /data .

# 清理旧备份（保留7天）
find $BACKUP_DIR -name "*.sql" -mtime +7 -delete
find $BACKUP_DIR -name "*.tar.gz" -mtime +7 -delete

echo "Backup completed: $DATE"
EOF

chmod +x backup.sh

# 添加到crontab（每天凌晨2点备份）
echo "0 2 * * * /path/to/backup.sh" | crontab -
```

## 支持与维护

- **文档**: 查看 `docs/` 目录获取更多详细文档
- **问题反馈**: 通过GitHub Issues报告问题
- **日志分析**: 使用 `docker-compose logs` 查看详细日志
- **性能监控**: 建议使用Prometheus + Grafana进行生产环境监控

---

**注意**: 这是一个完整的生产就绪部署方案，请根据实际需求调整配置参数。