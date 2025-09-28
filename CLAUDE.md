# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目架构

BrilliantTavern 是一个 AI 角色扮演聊天应用，采用前后端分离架构：

### 后端 (Spring Boot)
- **技术栈**: Spring Boot 3.5.6, Java 17, PostgreSQL, Redis, WebSocket
- **AI 集成**: Google Vertex AI (Gemini) + Google Gen AI Java SDK v1.18.0
- **认证**: JWT + Spring Security
- **语音**: Fish Speech TTS 服务集成
- **API 文档**: SpringDoc OpenAPI 3 (访问 `/api/swagger-ui.html`)
- **数据访问**: Spring Data JPA + Hibernate 6
- **实时通信**: WebSocket + STOMP

### 前端 (Vue.js)
- **技术栈**: Vue 3, Vue Router 4, Element Plus, Axios
- **构建工具**: Vue CLI 5
- **样式**: SCSS + Element Plus UI 组件库
- **实时通信**: STOMP over WebSocket (@stomp/stompjs + sockjs-client)
- **开发端口**: 3000 (代理后端 API 到 8080)

## 开发命令

### 后端
```bash
# 在 backend/brilliant-tavern 目录下
mvn spring-boot:run          # 启动开发服务器 (端口 8080)
mvn clean compile           # 编译项目
mvn test                    # 运行测试
mvn clean package          # 构建 JAR 包
```

### 前端
```bash
# 在 frontend 目录下
npm run serve              # 启动开发服务器 (端口 3000)
npm run dev                # 同上 (别名)
npm run build              # 构建生产版本
npm install                # 安装依赖
```

### 数据库初始化
```bash
# 运行数据库初始化脚本
psql -U postgres -d brilliant_tavern -f scripts/init_database.sql
```

## 环境配置

项目需要以下环境变量：
- `REDIS_HOST`: Redis 服务器地址
- `REDIS_PASSWORD`: Redis 密码
- `VERTEX_AI_PROJECT_ID`: Google Cloud 项目 ID
- `VERTEX_AI_LOCATION`: Vertex AI 区域 (默认 us-central1)
- `VERTEX_AI_MODEL`: 使用的模型 (默认 gemini-2.5-flash)
- `GOOGLE_API_KEY`: Google API 密钥 (可选，用于 Gemini Developer API)
- `TTS_SERVICE_URL`: TTS 服务基础 URL
- `TTS_WARMUP_ENABLED`: 是否启用TTS预热 (默认 true)
- `TTS_WARMUP_TEXT`: 预热使用的文本 (默认 "你好，这是语音测试")
- `TTS_WARMUP_VOICE_IDS`: 预热的音色ID列表 (默认 "1,2,3")
- `TTS_WARMUP_TIMEOUT`: 预热超时时间 (默认 15s)
- `TTS_WARMUP_DELAY`: 启动后延迟预热时间 (默认 3s)
- `GENAI_WARMUP_ENABLED`: 是否启用GenAI预热 (默认 true)
- `GENAI_WARMUP_TEXT`: GenAI预热文本 (默认 "Hi")
- `GENAI_WARMUP_TIMEOUT`: GenAI预热超时时间 (默认 30s)
- `GENAI_WARMUP_DELAY`: GenAI启动后延迟预热时间 (默认 10s)
- `GENAI_WARMUP_MAINTAIN_INTERVAL`: GenAI维持预热间隔 (默认 1小时)

## 核心功能模块

### 1. 用户认证 (`AuthController`, `AuthService`)
- JWT 令牌认证
- 用户注册/登录功能

### 2. 角色卡管理 (`CharacterCardController`, `CharacterCardService`)
- 角色卡创建、编辑、市场展示
- 点赞系统和评论功能
- 游标分页支持

### 3. 语音对话 (`VoiceChatController`, `StreamingVoiceOrchestrator`)
- 实时语音对话流处理
- WebSocket 通信和 STOMP 消息传递
- TTS 音频生成和缓存策略
- 对话历史管理和会话状态追踪
- 流式事件处理架构 (事件驱动模式)

### 4. TTS 语音管理 (`TTSVoiceController`, `TTSManagerService`, `TTSWarmupService`)
- 语音模型管理
- Fish Speech TTS 集成
- 语音缓存策略
- **TTS服务预热机制**: 应用启动后自动预热常用音色，解决冷启动问题
- **连接池优化**: 配置HTTP连接池，提升并发性能和连接复用
- **健康检查**: 提供TTS服务状态监控和故障诊断

### 5. GenAI 预热管理 (`GenAIWarmupService`, `GenAIHealthController`)
- **智能预热机制**: 应用启动后自动预热Vertex AI模型，解决首次调用延迟
- **成本优化**: 使用极小请求（1输入+10输出token）进行预热，成本可忽略
- **维持策略**: 每小时自动维持预热，确保服务持续可用
- **健康监控**: 提供API查看预热状态和手动触发预热功能

### 6. AI 服务 (`AIService`)
- Google Vertex AI 集成 (Gemini 2.5 Flash)
- 对话生成和流式处理
- 思考模式支持 (可配置 think-budget)
- 反应式编程 (WebFlux/Reactor) 支持

## 数据库架构

**PostgreSQL 数据库** (brilliant_tavern)，使用 UUID 主键和扩展：
- **PostgreSQL 扩展**: uuid-ossp, pg_trgm (全文搜索)
- **JPA 配置**: ddl-auto: validate (手动管理数据库结构)

### 主要数据表：
- `users`: 用户信息 (UUID主键)
- `character_cards`: 角色卡数据 (JSONB 格式存储)
- `chat_history`: 对话历史记录
- `tts_voices`: TTS 语音模型
- `user_likes`, `tts_voice_likes`: 点赞关联表 (复合主键)
- `card_comments`: 角色卡评论系统
- 索引优化: 用户名、邮箱、创建时间等关键字段

## WebSocket 通信

### 后端端点
- `/ws`: WebSocket 连接端点
- `/app/voice-chat`: 语音对话消息发送
- `/topic/voice-chat/{sessionId}`: 订阅语音对话响应

### 前端集成
- 使用 STOMP 协议
- `RoundVoiceChat.vue` 组件处理实时对话

## 关键架构模式

### 1. 流式语音处理架构
- **StreamingVoiceOrchestrator**: 核心流式语音协调器
- **事件驱动模式**: 使用 Handler 模式处理不同类型事件 (ASR, TTS, Subtitle)
- **反应式编程**: 使用 Reactor 的 Flux/Mono 处理异步流
- **会话状态管理**: ConcurrentHashMap 管理多会话状态

### 2. 分层架构设计
- **Controller Layer**: REST API 控制器 + WebSocket 控制器
- **Service Layer**: 业务逻辑服务 (AI, Voice, Character 等)
- **Repository Layer**: Spring Data JPA 数据访问
- **Entity Layer**: JPA 实体 + 自定义转换器

### 3. 配置管理
- **分离配置**: application.yml 中使用环境变量占位符
- **安全配置**: JWT 密钥、数据库密码等敏感信息外部化
- **模块化配置**: 分离的配置类 (WebSocket, Security, Redis 等)

## 测试和调试

### 测试架构
- **后端测试**: 目前项目未包含测试文件，建议在 `backend/brilliant-tavern/src/test/java/` 目录创建
- **测试框架**: Spring Boot Test (JUnit 5 + Mockito)
- **推荐测试类型**: 音频处理、AI 集成、WebSocket 通信、Controller 单元测试

### 调试工具
- **API 测试**: Swagger UI (`/api/swagger-ui.html`)
- **日志配置**: 详细的包级别日志 (com.github.jwj.brilliantavern: DEBUG)
- **SQL 调试**: Hibernate SQL 日志开启

### 运行测试
```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=ClassName

# 运行特定测试方法
mvn test -Dtest=ClassName#methodName

# 跳过测试
mvn clean package -DskipTests
```

## 开发注意事项

1. **环境配置**: 确保 PostgreSQL、Redis 服务运行，并配置相应环境变量
2. **AI 配置**: 需要有效的 Google Cloud 项目和 Vertex AI 访问权限
3. **TTS 服务**: 需要独立部署的 Fish Speech TTS 服务
4. **CORS 配置**: 前端开发时使用代理访问后端 API (`/api` 代理到 `http://localhost:8080`)
5. **数据库迁移**: 使用 `ddl-auto: validate` 模式，需要手动管理数据库结构
6. **文件上传**: 头像等文件存储在 `backend/brilliant-tavern/uploads/` 目录
7. **WebSocket 调试**: 注意前端 STOMP 连接和后端消息订阅路径匹配
8. **流式处理**: 语音对话使用流式架构，注意异步处理和错误处理
9. **TTS 预热**: 应用启动后会自动预热TTS服务，可通过环境变量控制预热行为
10. **TTS 监控**: 使用 `/api/tts/health` 检查服务状态，`/api/tts/warmup` 手动触发预热
11. **GenAI 预热**: 应用启动后自动预热Vertex AI模型，解决首次调用冷启动问题
12. **GenAI 监控**: 使用 `/api/genai/health` 检查预热状态，`/api/genai/warmup` 手动触发预热

## 代码检查和构建

### 代码质量检查
项目未配置 lint 工具，建议添加：
```bash
# 格式化代码 (使用 IDE 格式化功能)
# IntelliJ IDEA: Ctrl+Alt+L (Windows/Linux) 或 Cmd+Option+L (Mac)
```

### 构建流程
```bash
# 完整构建流程
cd backend/brilliant-tavern
mvn clean compile          # 清理并编译
mvn test                   # 运行测试 (如果有)
mvn package               # 打包应用

cd ../../frontend
npm install               # 安装依赖
npm run build            # 构建前端
```