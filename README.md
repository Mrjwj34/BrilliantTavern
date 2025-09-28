# BrilliantTavern

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3.3.0-4FC08D.svg)](https://vuejs.org/)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.java.net/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-blue.svg)](https://github.com/pgvector/pgvector)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://docs.docker.com/compose/)

BrilliantTavern是一个基于AI的智能角色扮演聊天平台，集成了语音交互、记忆检索、实时TTS和多模态对话等先进技术，为用户提供沉浸式的虚拟角色交流体验。

## 文档导航

| 文档 | 说明 |
|------|------|
| [部署指南](docs/deployment.md) | Docker Compose部署说明和环境配置 |
| [设计文档](docs/design.md) | 系统架构设计和技术选型说明 |
| [常见问题](docs/QA.md) | 选题问题解答 |

## 演示视频

**完整功能演示**: [[演示视频链接](https://www.bilibili.com/video/BV1NVnfz3EKW)]

## 技术架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   前端 (Vue.js)  │────│  后端 (Spring)   │────│  模型服务       │
│                 │    │                 │    │                 │
│ • Vue 3 + Router│    │ • Spring Boot   │    │ • Gemini 2.5    │
│ • Element Plus  │    │ • Spring Security│    │• nano banana   │
│ • WebSocket     │    │ • JPA + Redis   │    │ • 向量检索       │
│ • 音频处理       │    │ • 流式处理       │    │ • FishSpeech TTS │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
         ┌─────────────────────────────────────────────────┐
         │           数据层 (PostgreSQL + Redis)            │
         │                                               │
         │ • pgvector 向量数据库  • Redis 缓存和会话      │
         │ • 用户和角色数据      • 对话历史存储            │
         │ • 评论和社交功能      • 实时通信支持            │
         └─────────────────────────────────────────────────┘
```

## 后端技术栈

### 核心框架
- **Spring Boot 3.5.6**: 基于最新稳定版本，充分利用Java 17的新特性
- **Spring Security 6**: 提供现代化的安全框架，实现JWT无状态认证
- **Spring Data JPA**: 数据访问层抽象，支持动态查询和声明式事务管理
- **Spring WebFlux**: 响应式编程模型，处理高并发的流式数据传输

### 数据存储
- **PostgreSQL + pgvector**: 关系型数据库集成向量扩展，支持高性能的相似度搜索
- **Redis**: 内存数据库用于缓存和会话管理，提升系统响应性能
- **向量数据库**: 支持1536维嵌入向量存储，兼容HNSW索引算法

## 前端技术栈

### 核心技术
- **Vue.js 3.3.0**: 采用组合式API和响应式系统，提供现代化的前端开发体验
- **Vue Router 4.2.0**: 单页应用路由管理，支持懒加载和动态路由配置
- **Element Plus 2.4.0**: 企业级UI组件库，确保界面一致性和可访问性

### 特色功能
- **实时语音处理**: 基于WebRTC的音频采集和WebSocket传输，实现低延迟语音通信
- **动态字幕系统**: 支持多语言字幕显示和个性化样式配置
- **响应式设计**: 适配多种设备尺寸，支持桌面端和移动端操作
- **状态管理**: 使用组合式API进行响应式状态管理，无需额外的状态管理库

## 核心功能

### 智能角色系统
- **角色卡片管理**: 支持详细的角色人设配置、性格特征定义和对话示例设置
- **角色社区**: 提供角色分享平台，支持角色卡片的发布、浏览、收藏和评论
- **语音个性化**: 集成语音克隆技术，为每个角色配置独特的语音特征

### 记忆与上下文管理
- **向量记忆检索**: 基于语义相似度的智能记忆搜索，使用pgvector实现高效查询
- **对话历史管理**: 自动维护长期对话上下文，支持跨会话的记忆持久化
- **情感状态跟踪**: 记录并分析角色的情感变化和关系发展轨迹

### 多模态交互
- **语音识别处理**: 支持多种音频格式的实时语音转文字功能
- **FishSpeech TTS**: 集成自建的FishSpeech语音合成服务，支持情感表达和语调控制
- **实时字幕显示**: 多语言字幕系统，提升可访问性和用户体验

### AI能力集成
- **图像生成**: 集成Imagen模型，支持角色形象和场景图像的AI生成
- **智能对话**: 基于上下文的动态回复生成，保持角色一致性
- **多语言支持**: 支持中文、英语、日语等多种语言的界面和对话

## 项目结构

```
BrilliantTavern/
├── backend/brilliant-tavern/        # Spring Boot 后端服务
│   ├── src/main/java/
│   │   └── com/github/jwj/brilliantavern/
│   │       ├── controller/          # REST API控制器层
│   │       ├── service/             # 业务逻辑服务层
│   │       ├── repository/          # 数据访问层
│   │       ├── entity/              # JPA实体类
│   │       ├── config/              # 配置类
│   │       └── security/            # 安全相关组件
│   ├── src/main/resources/
│   │   ├── application.yml          # 应用配置文件
│   │   └── prompts/                 # AI提示词模板
│   └── pom.xml                      # Maven项目配置
├── frontend/                        # Vue.js 前端应用
│   ├── src/
│   │   ├── components/              # Vue组件
│   │   ├── views/                   # 页面视图
│   │   ├── router/                  # 路由配置
│   │   ├── api/                     # API接口调用
│   │   └── utils/                   # 工具函数
│   ├── public/                      # 静态资源
│   └── package.json                 # npm项目配置
├── docs/                            # 技术文档
├── scripts/                         # 构建和部署脚本
├── docker-compose.yml               # 容器编排配置
└── nginx.conf                       # 反向代理配置
```


## 技术特点

### 系统架构
- **微服务设计**: 模块化的服务架构，支持独立部署和水平扩展
- **容器化部署**: 基于Docker Compose的容器编排，简化部署流程
- **安全设计**: 多层安全防护机制，包括身份认证、授权控制和数据验证

### 性能优化
- **异步处理**: 采用响应式编程模型，提高系统并发处理能力
- **缓存策略**: 多级缓存设计，包括Redis缓存和应用级缓存
- **数据库优化**: 向量索引优化和查询性能调优

### 开发体验
- **代码质量**: 遵循企业级开发规范，包含完整的错误处理和日志记录
- **监控体系**: 集成健康检查和性能监控机制
- **扩展性**: 插件化架构设计，便于功能模块的扩展和维护
