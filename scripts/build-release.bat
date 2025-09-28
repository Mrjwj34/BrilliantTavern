@echo off
setlocal EnableDelayedExpansion

REM BrilliantTavern Release打包脚本 (Windows版本)
REM 用于生成包含所有部署文件的完整发布包

echo ==================================
echo BrilliantTavern Release 构建脚本
echo ==================================
echo.

REM 配置参数
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR:~0,-9%
set RELEASE_DIR=%PROJECT_ROOT%\release
for /f "tokens=2 delims==" %%a in ('wmic OS Get localdatetime /value') do set "dt=%%a"
set TIMESTAMP=%dt:~0,4%%dt:~4,2%%dt:~6,2%_%dt:~8,2%%dt:~10,2%%dt:~12,2%
set RELEASE_NAME=brilliant-tavern-release-%TIMESTAMP%
set RELEASE_PATH=%RELEASE_DIR%\%RELEASE_NAME%

REM 检查依赖
echo [INFO] 检查构建依赖...

java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java未安装，请安装Java 17+
    pause
    exit /b 1
)

mvn --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven未安装，请安装Maven 3.6+
    pause
    exit /b 1
)

node --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Node.js未安装，请安装Node.js 18+
    pause
    exit /b 1
)

npm --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] npm未安装，请安装npm
    pause
    exit /b 1
)

echo [SUCCESS] 所有依赖检查通过

REM 清理旧的构建文件
echo [INFO] 清理旧的构建文件...

if exist "%PROJECT_ROOT%\backend\brilliant-tavern\target" (
    rmdir /s /q "%PROJECT_ROOT%\backend\brilliant-tavern\target"
    echo [INFO] 已清理后端构建文件
)

if exist "%PROJECT_ROOT%\frontend\dist" (
    rmdir /s /q "%PROJECT_ROOT%\frontend\dist"
    echo [INFO] 已清理前端构建文件
)

if exist "%RELEASE_DIR%" (
    rmdir /s /q "%RELEASE_DIR%"
    echo [INFO] 已清理旧的发布目录
)

echo [SUCCESS] 构建文件清理完成

REM 构建后端
echo [INFO] 开始构建后端应用...

cd /d "%PROJECT_ROOT%\backend\brilliant-tavern"

echo [INFO] 执行Maven构建...
call mvn clean package -DskipTests -B -q
if errorlevel 1 (
    echo [ERROR] 后端构建失败
    pause
    exit /b 1
)

REM 检查JAR文件是否生成
dir target\*.jar >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 后端构建失败：未找到JAR文件
    pause
    exit /b 1
)

echo [SUCCESS] 后端构建完成

cd /d "%PROJECT_ROOT%"

REM 构建前端
echo [INFO] 开始构建前端应用...

cd /d "%PROJECT_ROOT%\frontend"

echo [INFO] 安装前端依赖...
call npm ci --silent
if errorlevel 1 (
    echo [ERROR] 前端依赖安装失败
    pause
    exit /b 1
)

echo [INFO] 构建前端生产版本...
call npm run build
if errorlevel 1 (
    echo [ERROR] 前端构建失败
    pause
    exit /b 1
)

if not exist "dist" (
    echo [ERROR] 前端构建失败：未找到dist目录
    pause
    exit /b 1
)

echo [SUCCESS] 前端构建完成

cd /d "%PROJECT_ROOT%"

REM 创建发布目录结构
echo [INFO] 创建发布目录结构...

mkdir "%RELEASE_PATH%"
mkdir "%RELEASE_PATH%\backend"
mkdir "%RELEASE_PATH%\frontend"
mkdir "%RELEASE_PATH%\config"
mkdir "%RELEASE_PATH%\scripts"
mkdir "%RELEASE_PATH%\docs"

echo [SUCCESS] 发布目录结构创建完成

REM 复制构建产物
echo [INFO] 复制构建产物...

REM 复制后端JAR文件
for %%f in ("%PROJECT_ROOT%\backend\brilliant-tavern\target\*.jar") do (
    if not "%%~nf"=="original" (
        copy "%%f" "%RELEASE_PATH%\backend\app.jar" >nul
        echo [INFO] 已复制后端JAR文件
        goto :jar_copied
    )
)
:jar_copied

REM 复制前端构建文件
xcopy "%PROJECT_ROOT%\frontend\dist" "%RELEASE_PATH%\frontend\dist" /e /i /q >nul
echo [INFO] 已复制前端构建文件

echo [SUCCESS] 构建产物复制完成

REM 复制配置文件
echo [INFO] 复制配置文件...

copy "%PROJECT_ROOT%\docker-compose.yml" "%RELEASE_PATH%\" >nul
copy "%PROJECT_ROOT%\backend\brilliant-tavern\Dockerfile" "%RELEASE_PATH%\backend\" >nul
copy "%PROJECT_ROOT%\frontend\Dockerfile" "%RELEASE_PATH%\frontend\" >nul
copy "%PROJECT_ROOT%\nginx.conf" "%RELEASE_PATH%\config\" >nul
copy "%PROJECT_ROOT%\.env.example" "%RELEASE_PATH%\" >nul

if exist "%PROJECT_ROOT%\scripts\*.sql" (
    copy "%PROJECT_ROOT%\scripts\*.sql" "%RELEASE_PATH%\scripts\" >nul
    echo [INFO] 已复制数据库脚本
)

echo [SUCCESS] 配置文件复制完成

REM 复制文档
echo [INFO] 复制文档文件...

if exist "%PROJECT_ROOT%\docs" (
    xcopy "%PROJECT_ROOT%\docs" "%RELEASE_PATH%\docs" /e /i /q >nul
    echo [INFO] 已复制文档目录
)

if exist "%PROJECT_ROOT%\README.md" (
    copy "%PROJECT_ROOT%\README.md" "%RELEASE_PATH%\" >nul
)

if exist "%PROJECT_ROOT%\CLAUDE.md" (
    copy "%PROJECT_ROOT%\CLAUDE.md" "%RELEASE_PATH%\" >nul
)

echo [SUCCESS] 文档文件复制完成

REM 生成部署指南
echo [INFO] 生成部署指南...

(
echo # BrilliantTavern 快速部署指南 (Windows^)
echo.
echo ## 1. 前置准备
echo.
echo ### 安装Docker Desktop
echo 1. 下载并安装 Docker Desktop for Windows
echo 2. 启动 Docker Desktop
echo 3. 确保 Docker 正常运行 (运行 `docker --version` 测试^)
echo.
echo ### 安装Google Cloud CLI
echo 1. 下载并安装 Google Cloud CLI
echo 2. 打开命令提示符，执行以下命令：
echo ```
echo gcloud auth login
echo gcloud auth application-default login
echo gcloud config set project YOUR_PROJECT_ID
echo ```
echo.
echo ## 2. 快速部署
echo.
echo ### 配置环境变量
echo 1. 复制环境变量模板：`copy .env.example .env`
echo 2. 编辑 .env 文件，修改以下关键参数：
echo    - VERTEX_AI_PROJECT_ID=your-gcp-project-id
echo    - POSTGRES_PASSWORD=your_secure_password
echo    - REDIS_PASSWORD=your_redis_password
echo    - JWT_SECRET=your_very_secure_jwt_secret_key
echo.
echo ### 启动服务
echo 1. 双击运行 `start.bat`
echo 2. 或在命令提示符中运行：`docker-compose up -d`
echo.
echo ### 访问应用
echo - 前端应用: http://localhost
echo - API文档: http://localhost/api/swagger-ui.html
echo - 健康检查: http://localhost/health
echo.
echo ## 3. 故障排除
echo.
echo ### 查看日志
echo ```
echo docker-compose logs
echo docker-compose logs backend
echo ```
echo.
echo ### 重启服务
echo ```
echo docker-compose restart
echo ```
echo.
echo 详细部署文档请查看: docs\deployment.md
) > "%RELEASE_PATH%\DEPLOYMENT_QUICK_START.md"

echo [SUCCESS] 部署指南生成完成

REM 生成版本信息
echo [INFO] 生成版本信息...

(
echo BrilliantTavern Release Information
echo ==================================
echo.
echo 构建时间: %date% %time%
echo 构建主机: %COMPUTERNAME%
echo 构建用户: %USERNAME%
echo.
echo 组件版本:
echo --------
) > "%RELEASE_PATH%\VERSION.txt"

java -version 2>> "%RELEASE_PATH%\VERSION.txt"
mvn --version | findstr "Apache Maven" >> "%RELEASE_PATH%\VERSION.txt"
node --version >> "%RELEASE_PATH%\VERSION.txt"
npm --version >> "%RELEASE_PATH%\VERSION.txt"

echo [SUCCESS] 版本信息生成完成

REM 创建启动脚本
echo [INFO] 创建启动脚本...

REM Windows启动脚本
(
echo @echo off
echo echo 启动 BrilliantTavern...
echo.
echo :: 检查环境变量文件
echo if not exist ".env" ^(
echo     echo 错误: 未找到.env文件，请先复制.env.example为.env并配置
echo     pause
echo     exit /b 1
echo ^)
echo.
echo :: 启动服务
echo docker-compose up -d
echo.
echo echo 服务启动完成！
echo echo 前端地址: http://localhost
echo echo API文档: http://localhost/api/swagger-ui.html
echo echo.
echo echo 查看服务状态: docker-compose ps
echo echo 查看日志: docker-compose logs -f
echo pause
) > "%RELEASE_PATH%\start.bat"

REM Linux/macOS启动脚本
(
echo #!/bin/bash
echo.
echo echo "启动 BrilliantTavern..."
echo.
echo # 检查Docker是否运行
echo if ! docker info ^>/dev/null 2^>^&1; then
echo     echo "错误: Docker未运行，请先启动Docker"
echo     exit 1
echo fi
echo.
echo # 检查环境变量文件
echo if [ ! -f ".env" ]; then
echo     echo "错误: 未找到.env文件，请先复制.env.example为.env并配置"
echo     exit 1
echo fi
echo.
echo # 启动服务
echo docker-compose up -d
echo.
echo echo "服务启动完成！"
echo echo "前端地址: http://localhost"
echo echo "API文档: http://localhost/api/swagger-ui.html"
echo echo ""
echo echo "查看服务状态: docker-compose ps"
echo echo "查看日志: docker-compose logs -f"
) > "%RELEASE_PATH%\start.sh"

REM 停止脚本
(
echo @echo off
echo echo 停止 BrilliantTavern 服务...
echo docker-compose down
echo echo 服务已停止
echo pause
) > "%RELEASE_PATH%\stop.bat"

(
echo #!/bin/bash
echo echo "停止 BrilliantTavern 服务..."
echo docker-compose down
echo echo "服务已停止"
) > "%RELEASE_PATH%\stop.sh"

echo [SUCCESS] 启动脚本创建完成

REM 创建压缩包
echo [INFO] 创建发布压缩包...

cd /d "%RELEASE_DIR%"

REM 使用PowerShell创建ZIP压缩包
powershell -command "Compress-Archive -Path '%RELEASE_NAME%' -DestinationPath '%RELEASE_NAME%.zip' -Force"

if exist "%RELEASE_NAME%.zip" (
    echo [SUCCESS] 发布压缩包创建完成
    for %%A in ("%RELEASE_NAME%.zip") do set "ZIP_SIZE=%%~zA"
    echo [INFO] 压缩包路径: %RELEASE_DIR%\%RELEASE_NAME%.zip
    echo [INFO] 压缩包大小: !ZIP_SIZE! 字节
) else (
    echo [ERROR] 压缩包创建失败
    pause
    exit /b 1
)

REM 生成发布信息文件
(
echo BrilliantTavern Release Package
echo ==============================
echo.
echo 文件名: %RELEASE_NAME%.zip
echo 大小: !ZIP_SIZE! 字节
echo 生成时间: %date% %time%
echo.
echo 内容清单:
echo --------
echo - backend\app.jar                 # 后端应用JAR包
echo - frontend\dist\                  # 前端构建文件
echo - docker-compose.yml             # Docker Compose配置
echo - backend\Dockerfile             # 后端Docker镜像配置
echo - frontend\Dockerfile            # 前端Docker镜像配置
echo - config\nginx.conf              # Nginx配置
echo - .env.example                   # 环境变量模板
echo - scripts\                       # 数据库脚本
echo - docs\                          # 文档目录
echo - start.bat / start.sh           # 启动脚本
echo - stop.bat / stop.sh             # 停止脚本
echo - DEPLOYMENT_QUICK_START.md      # 快速部署指南
echo - VERSION.txt                    # 版本信息
echo.
echo 部署说明:
echo --------
echo 1. 解压发布包
echo 2. 复制 .env.example 为 .env 并配置
echo 3. 执行 start.bat (Windows^) 或 ./start.sh (Linux/macOS^)
echo 4. 访问 http://localhost
echo.
echo 详细文档请查看 docs\deployment.md
) > "%RELEASE_NAME%_RELEASE_INFO.txt"

cd /d "%PROJECT_ROOT%"

REM 显示完成信息
echo.
echo [SUCCESS] 🎉 BrilliantTavern 发布包构建完成！
echo.
echo 📦 发布包信息:
echo    路径: %RELEASE_DIR%\%RELEASE_NAME%.zip
echo    大小: !ZIP_SIZE! 字节
echo.
echo 🚀 部署步骤:
echo    1. 将发布包传输到目标服务器
echo    2. 解压ZIP文件
echo    3. 进入目录
echo    4. 配置环境: copy .env.example .env 并编辑
echo    5. 启动服务: start.bat
echo.
echo 📖 更多信息请查看发布包中的 DEPLOYMENT_QUICK_START.md
echo.
pause