# BrilliantTavern 部署指南(注: 时间原因未经测试)

本文档提供了 BrilliantTavern AI 角色扮演聊天应用的完整部署指南，支持 Docker Compose 一键部署。

## 目录

- [系统要求](#系统要求)
- [前置准备](#前置准备)
- [快速开始](#快速开始)
- [详细配置](#详细配置)


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
cd brilliant-tavern
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
