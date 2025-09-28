#!/bin/bash

# BrilliantTavern Release打包脚本
# 用于生成包含所有部署文件的完整发布包

set -e  # 遇到错误立即退出

# 配置参数
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
RELEASE_DIR="$PROJECT_ROOT/release"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RELEASE_NAME="brilliant-tavern-release-$TIMESTAMP"
RELEASE_PATH="$RELEASE_DIR/$RELEASE_NAME"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查依赖
check_dependencies() {
    log_info "检查构建依赖..."
    
    # 检查Java环境
    if ! command -v java &> /dev/null; then
        log_error "Java未安装，请安装Java 17+"
        exit 1
    fi
    
    # 检查Maven
    if ! command -v mvn &> /dev/null; then
        log_error "Maven未安装，请安装Maven 3.6+"
        exit 1
    fi
    
    # 检查Node.js
    if ! command -v node &> /dev/null; then
        log_error "Node.js未安装，请安装Node.js 18+"
        exit 1
    fi
    
    # 检查npm
    if ! command -v npm &> /dev/null; then
        log_error "npm未安装，请安装npm"
        exit 1
    fi
    
    log_success "所有依赖检查通过"
}

# 清理旧的构建文件
clean_build() {
    log_info "清理旧的构建文件..."
    
    # 清理后端构建文件
    if [ -d "$PROJECT_ROOT/backend/brilliant-tavern/target" ]; then
        rm -rf "$PROJECT_ROOT/backend/brilliant-tavern/target"
        log_info "已清理后端构建文件"
    fi
    
    # 清理前端构建文件
    if [ -d "$PROJECT_ROOT/frontend/dist" ]; then
        rm -rf "$PROJECT_ROOT/frontend/dist"
        log_info "已清理前端构建文件"
    fi
    
    # 清理旧的发布目录
    if [ -d "$RELEASE_DIR" ]; then
        rm -rf "$RELEASE_DIR"
        log_info "已清理旧的发布目录"
    fi
    
    log_success "构建文件清理完成"
}

# 构建后端
build_backend() {
    log_info "开始构建后端应用..."
    
    cd "$PROJECT_ROOT/backend/brilliant-tavern"
    
    # 使用Maven构建
    log_info "执行Maven构建..."
    mvn clean package -DskipTests -B -q
    
    # 检查JAR文件是否生成
    JAR_FILE=$(find target -name "*.jar" | grep -v original | head -n 1)
    if [ ! -f "$JAR_FILE" ]; then
        log_error "后端构建失败：未找到JAR文件"
        exit 1
    fi
    
    log_success "后端构建完成：$JAR_FILE"
    cd "$PROJECT_ROOT"
}

# 构建前端
build_frontend() {
    log_info "开始构建前端应用..."
    
    cd "$PROJECT_ROOT/frontend"
    
    # 安装依赖
    log_info "安装前端依赖..."
    npm ci --silent
    
    # 构建生产版本
    log_info "构建前端生产版本..."
    npm run build
    
    # 检查dist目录是否生成
    if [ ! -d "dist" ]; then
        log_error "前端构建失败：未找到dist目录"
        exit 1
    fi
    
    log_success "前端构建完成"
    cd "$PROJECT_ROOT"
}

# 创建发布目录结构
create_release_structure() {
    log_info "创建发布目录结构..."
    
    mkdir -p "$RELEASE_PATH"
    mkdir -p "$RELEASE_PATH/backend"
    mkdir -p "$RELEASE_PATH/frontend"
    mkdir -p "$RELEASE_PATH/config"
    mkdir -p "$RELEASE_PATH/scripts"
    mkdir -p "$RELEASE_PATH/docs"
    
    log_success "发布目录结构创建完成"
}

# 复制构建产物
copy_build_artifacts() {
    log_info "复制构建产物..."
    
    # 复制后端JAR文件
    JAR_FILE=$(find "$PROJECT_ROOT/backend/brilliant-tavern/target" -name "*.jar" | grep -v original | head -n 1)
    cp "$JAR_FILE" "$RELEASE_PATH/backend/app.jar"
    log_info "已复制后端JAR文件"
    
    # 复制前端构建文件
    cp -r "$PROJECT_ROOT/frontend/dist" "$RELEASE_PATH/frontend/"
    log_info "已复制前端构建文件"
    
    log_success "构建产物复制完成"
}

# 复制配置文件
copy_config_files() {
    log_info "复制配置文件..."
    
    # Docker相关文件
    cp "$PROJECT_ROOT/docker-compose.yml" "$RELEASE_PATH/"
    cp "$PROJECT_ROOT/backend/brilliant-tavern/Dockerfile" "$RELEASE_PATH/backend/"
    cp "$PROJECT_ROOT/frontend/Dockerfile" "$RELEASE_PATH/frontend/"
    cp "$PROJECT_ROOT/nginx.conf" "$RELEASE_PATH/config/"
    
    # 环境变量文件
    cp "$PROJECT_ROOT/.env.example" "$RELEASE_PATH/"
    
    # 如果存在.env文件，复制为.env.sample（移除敏感信息）
    if [ -f "$PROJECT_ROOT/.env" ]; then
        # 创建清理后的环境变量示例
        sed 's/=.*/=/' "$PROJECT_ROOT/.env" > "$RELEASE_PATH/.env.sample"
        log_info "已创建清理后的.env.sample文件"
    fi
    
    # 数据库脚本
    if [ -d "$PROJECT_ROOT/scripts" ]; then
        cp -r "$PROJECT_ROOT/scripts"/*.sql "$RELEASE_PATH/scripts/" 2>/dev/null || true
        log_info "已复制数据库脚本"
    fi
    
    log_success "配置文件复制完成"
}

# 复制文档
copy_documentation() {
    log_info "复制文档文件..."
    
    # 复制docs目录
    if [ -d "$PROJECT_ROOT/docs" ]; then
        cp -r "$PROJECT_ROOT/docs"/* "$RELEASE_PATH/docs/"
        log_info "已复制文档目录"
    fi
    
    # 复制README和其他markdown文件
    if [ -f "$PROJECT_ROOT/README.md" ]; then
        cp "$PROJECT_ROOT/README.md" "$RELEASE_PATH/"
    fi
    
    if [ -f "$PROJECT_ROOT/CLAUDE.md" ]; then
        cp "$PROJECT_ROOT/CLAUDE.md" "$RELEASE_PATH/"
    fi
    
    log_success "文档文件复制完成"
}

# 生成部署指南
generate_deployment_guide() {
    log_info "生成部署指南..."
    
    cat > "$RELEASE_PATH/DEPLOYMENT_QUICK_START.md" << 'EOF'
# BrilliantTavern 快速部署指南

## 1. 前置准备

### 安装Docker和Docker Compose
```bash
# Ubuntu/Debian
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo apt-get install docker-compose-plugin

# 添加用户到docker组
sudo usermod -aG docker $USER
```

### 安装Google Cloud CLI
```bash
# 安装gcloud CLI
curl https://sdk.cloud.google.com | bash
exec -l $SHELL

# 登录并设置认证
gcloud auth login
gcloud auth application-default login
gcloud config set project YOUR_PROJECT_ID
```

## 2. 快速部署

### 配置环境变量
```bash
# 复制环境变量模板
cp .env.example .env

# 编辑配置文件，修改以下关键参数：
# - VERTEX_AI_PROJECT_ID=your-gcp-project-id
# - POSTGRES_PASSWORD=your_secure_password  
# - REDIS_PASSWORD=your_redis_password
# - JWT_SECRET=your_very_secure_jwt_secret_key
nano .env
```

### 启动服务
```bash
# 构建并启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 访问应用
- 前端应用: http://localhost
- API文档: http://localhost/api/swagger-ui.html
- 健康检查: http://localhost/health

## 3. 故障排除

### 常见问题
1. **Google Cloud认证失败**: 确保执行了 `gcloud auth application-default login`
2. **数据库连接失败**: 检查 `.env` 中的数据库配置
3. **端口冲突**: 修改 `.env` 中的端口配置

### 查看日志
```bash
# 查看所有服务日志
docker-compose logs

# 查看特定服务日志
docker-compose logs backend
docker-compose logs postgres
```

### 重启服务
```bash
# 重启所有服务
docker-compose restart

# 重启特定服务
docker-compose restart backend
```

## 4. 更多信息

详细部署文档请查看: `docs/deployment.md`
EOF
    
    log_success "部署指南生成完成"
}

# 生成版本信息
generate_version_info() {
    log_info "生成版本信息..."
    
    cat > "$RELEASE_PATH/VERSION.txt" << EOF
BrilliantTavern Release Information
==================================

构建时间: $(date '+%Y-%m-%d %H:%M:%S %Z')
构建主机: $(hostname)
构建用户: $(whoami)

Git信息:
--------
EOF

    # 如果是Git仓库，添加Git信息
    if [ -d "$PROJECT_ROOT/.git" ]; then
        cd "$PROJECT_ROOT"
        echo "Commit Hash: $(git rev-parse HEAD)" >> "$RELEASE_PATH/VERSION.txt"
        echo "Branch: $(git rev-parse --abbrev-ref HEAD)" >> "$RELEASE_PATH/VERSION.txt"
        echo "Last Commit: $(git log -1 --pretty=format:'%h - %s (%an, %ar)')" >> "$RELEASE_PATH/VERSION.txt"
        echo "Git Status:" >> "$RELEASE_PATH/VERSION.txt"
        git status --porcelain >> "$RELEASE_PATH/VERSION.txt"
    else
        echo "非Git仓库" >> "$RELEASE_PATH/VERSION.txt"
    fi
    
    echo "" >> "$RELEASE_PATH/VERSION.txt"
    echo "组件版本:" >> "$RELEASE_PATH/VERSION.txt"
    echo "--------" >> "$RELEASE_PATH/VERSION.txt"
    echo "Java: $(java -version 2>&1 | head -1)" >> "$RELEASE_PATH/VERSION.txt"
    echo "Maven: $(mvn --version | head -1)" >> "$RELEASE_PATH/VERSION.txt"
    echo "Node.js: $(node --version)" >> "$RELEASE_PATH/VERSION.txt"
    echo "npm: $(npm --version)" >> "$RELEASE_PATH/VERSION.txt"
    
    log_success "版本信息生成完成"
}

# 创建启动脚本
create_startup_scripts() {
    log_info "创建启动脚本..."
    
    # Linux/macOS启动脚本
    cat > "$RELEASE_PATH/start.sh" << 'EOF'
#!/bin/bash

echo "启动 BrilliantTavern..."

# 检查Docker是否运行
if ! docker info >/dev/null 2>&1; then
    echo "错误: Docker未运行，请先启动Docker"
    exit 1
fi

# 检查环境变量文件
if [ ! -f ".env" ]; then
    echo "错误: 未找到.env文件，请先复制.env.example为.env并配置"
    exit 1
fi

# 启动服务
docker-compose up -d

echo "服务启动完成！"
echo "前端地址: http://localhost"
echo "API文档: http://localhost/api/swagger-ui.html"
echo ""
echo "查看服务状态: docker-compose ps"
echo "查看日志: docker-compose logs -f"
EOF

    # Windows启动脚本
    cat > "$RELEASE_PATH/start.bat" << 'EOF'
@echo off
echo 启动 BrilliantTavern...

:: 检查环境变量文件
if not exist ".env" (
    echo 错误: 未找到.env文件，请先复制.env.example为.env并配置
    pause
    exit /b 1
)

:: 启动服务
docker-compose up -d

echo 服务启动完成！
echo 前端地址: http://localhost
echo API文档: http://localhost/api/swagger-ui.html
echo.
echo 查看服务状态: docker-compose ps
echo 查看日志: docker-compose logs -f
pause
EOF

    # 停止脚本
    cat > "$RELEASE_PATH/stop.sh" << 'EOF'
#!/bin/bash
echo "停止 BrilliantTavern 服务..."
docker-compose down
echo "服务已停止"
EOF

    cat > "$RELEASE_PATH/stop.bat" << 'EOF'
@echo off
echo 停止 BrilliantTavern 服务...
docker-compose down
echo 服务已停止
pause
EOF

    # 设置执行权限
    chmod +x "$RELEASE_PATH/start.sh"
    chmod +x "$RELEASE_PATH/stop.sh"
    
    log_success "启动脚本创建完成"
}

# 创建压缩包
create_archive() {
    log_info "创建发布压缩包..."
    
    cd "$RELEASE_DIR"
    
    # 创建tar.gz压缩包
    tar -czf "${RELEASE_NAME}.tar.gz" "$RELEASE_NAME/"
    
    # 创建zip压缩包（Windows兼容）
    if command -v zip &> /dev/null; then
        zip -r "${RELEASE_NAME}.zip" "$RELEASE_NAME/" > /dev/null
        log_info "已创建ZIP压缩包"
    fi
    
    # 计算文件大小和校验和
    TARBALL_SIZE=$(du -h "${RELEASE_NAME}.tar.gz" | cut -f1)
    TARBALL_SHA256=$(sha256sum "${RELEASE_NAME}.tar.gz" | cut -d' ' -f1)
    
    # 生成发布信息文件
    cat > "${RELEASE_NAME}_RELEASE_INFO.txt" << EOF
BrilliantTavern Release Package
==============================

文件名: ${RELEASE_NAME}.tar.gz
大小: $TARBALL_SIZE
SHA256: $TARBALL_SHA256
生成时间: $(date)

内容清单:
--------
- backend/app.jar                 # 后端应用JAR包
- frontend/dist/                  # 前端构建文件
- docker-compose.yml             # Docker Compose配置
- backend/Dockerfile             # 后端Docker镜像配置
- frontend/Dockerfile            # 前端Docker镜像配置
- config/nginx.conf              # Nginx配置
- .env.example                   # 环境变量模板
- scripts/                       # 数据库脚本
- docs/                          # 文档目录
- start.sh / start.bat           # 启动脚本
- stop.sh / stop.bat             # 停止脚本
- DEPLOYMENT_QUICK_START.md      # 快速部署指南
- VERSION.txt                    # 版本信息

部署说明:
--------
1. 解压发布包
2. 复制 .env.example 为 .env 并配置
3. 执行 ./start.sh (Linux/macOS) 或 start.bat (Windows)
4. 访问 http://localhost

详细文档请查看 docs/deployment.md
EOF
    
    log_success "发布压缩包创建完成"
    log_info "压缩包路径: $RELEASE_DIR/${RELEASE_NAME}.tar.gz"
    log_info "压缩包大小: $TARBALL_SIZE"
    
    cd "$PROJECT_ROOT"
}

# 清理临时文件
cleanup() {
    log_info "清理临时文件..."
    
    # 可选：删除解压的发布目录，只保留压缩包
    # rm -rf "$RELEASE_PATH"
    
    log_success "清理完成"
}

# 显示完成信息
show_completion_info() {
    echo ""
    log_success "🎉 BrilliantTavern 发布包构建完成！"
    echo ""
    echo "📦 发布包信息:"
    echo "   路径: $RELEASE_DIR/${RELEASE_NAME}.tar.gz"
    echo "   大小: $(du -h "$RELEASE_DIR/${RELEASE_NAME}.tar.gz" | cut -f1)"
    echo ""
    echo "🚀 部署步骤:"
    echo "   1. 将发布包传输到目标服务器"
    echo "   2. 解压: tar -xzf ${RELEASE_NAME}.tar.gz"
    echo "   3. 进入目录: cd $RELEASE_NAME"
    echo "   4. 配置环境: cp .env.example .env && nano .env"
    echo "   5. 启动服务: ./start.sh"
    echo ""
    echo "📖 更多信息请查看发布包中的 DEPLOYMENT_QUICK_START.md"
    echo ""
}

# 主函数
main() {
    echo "=================================="
    echo "BrilliantTavern Release 构建脚本"
    echo "=================================="
    echo ""
    
    check_dependencies
    clean_build
    build_backend
    build_frontend
    create_release_structure
    copy_build_artifacts
    copy_config_files
    copy_documentation
    generate_deployment_guide
    generate_version_info
    create_startup_scripts
    create_archive
    cleanup
    show_completion_info
}

# 执行主函数
main "$@"