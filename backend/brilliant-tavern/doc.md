
### **BrilliantTavern: 技术设计文档 (MVP)**

**1. 整体架构 (Overall Architecture)**

我们将采用现代化的前后端分离架构，并通过 WebSocket 实现核心的实时语音通信。整个系统分为四个主要部分：**前端应用**、**后端服务**、**数据库**和**第三方 AI 服务**。


**组件 breakdown:**

*   **前端应用 (Client - SPA)**:
    *   **技术栈**: Vue 3 或 React。
    *   **核心职责**:
        1.  **UI/UX**: 提供用户注册/登录、角色卡市场、角色卡编辑器和核心的语音聊天界面。
        2.  **音频处理**: 使用 `MediaRecorder` API 实时捕获用户麦克风的音频流。
        3.  **实时通信**: 通过 WebSocket 将原始音频流（或经过轻度压缩的格式如 Opus）实时推送到后端。同时，接收后端推送的 AI 音频流。
        4.  **音频播放**: 使用 `MediaSource Extensions` API 将接收到的实时音频流送入播放缓冲区，实现无延迟、无中断的连续播放。
        5.  **状态管理**: 管理用户登录状态、当前选择的角色卡信息等。

*   **后端服务 (Backend - Spring Boot Monolith)**:
    *   **技术栈**: Java 17+, Spring Boot 3.x, Spring Security, Spring Data JPA, Project Reactor。
    *   **核心职责**:
        1.  **用户与角色卡管理 (REST API)**: 提供标准的 CRUD (增删改查) 接口，用于用户认证、角色卡的创建、分享、搜索、点赞和评论。
        2.  **实时通信网关 (WebSocket Gateway)**: 系统的核心。处理来自前端的 WebSocket 连接，管理每个用户的聊天会话 (Session)。
        3.  **AI 服务集成层**: 封装与 Google Gemini API 的所有交互。这是实现低延迟的关键。
        4.  **对话记忆管理**: 负责从数据库加载历史对话，并在对话过程中维护上下文，将其作为 Prompt 的一部分传递给 AI 模型。

*   **数据库 (Database)**:
    *   **技术选型**: PostgreSQL。
    *   **选择理由**:
        *   **成熟稳定**: 业界公认的强大、可靠的关系型数据库。
        *   **JSONB 支持**: 拥有强大的原生 JSONB 数据类型，这对于存储结构复杂且可能随时演进的角色卡数据至关重要。我们可以将整个角色卡的复杂设定（性格、场景、示例对话等）存为一个 JSONB 字段，兼具灵活性和查询性能。
        *   **Spring 生态完美支持**: Spring Data JPA 对其支持非常完善。

*   **第三方 AI 服务 (External AI Services)**:
    *   **核心模型 (Gemini 2.5 Pro/Flash)**:
        *   **关键特性**: **原生多模态输入**。它能直接接收音频流作为输入，省去了传统的“客户端/服务端 STT”这一步，极大地降低了延迟。这是实现“实时聊天模式”的技术基石。
        *   **交互模式**: 后端服务将与 Gemini API 建立**双向流式 (Bi-directional Streaming)** 连接。我们向其推送用户的音频流，它会实时地返回生成的文本流和/或音频流。
    *   **文本转语音服务 (TTS - HTTP API调用/Fish Speech API)**:
        *   **作用**: 提供丰富的音色选择。虽然 Gemini 可能也能直接输出音频，但独立的 TTS 服务通常提供更多高质量、不同风格的声线选项。
        *   **工作流**: 当用户为角色卡选择特定音色时，后端会从 Gemini 获取**文本流**，然后实时地将文本流送入选择的 TTS 服务中，并将生成的音频流转发给前端。按句分割

**2. 数据库设计 (Database Schema)**

我们将设计 5 个核心表来支撑 MVP 的所有功能。

**ER 图 (概念):**
```
+-----------+       +-------------------+       +----------------+
|   users   |-------| character_cards   |-------|  card_comments |
+-----------+       +-------------------+       +----------------+
      |                   |        |                   |
      |                   |        +-------------------| (author_id)
      |                   |                            |
      +-------------------| (creator_id)               |
      |                   |                            |
      |             +-------------+                    |
      +-------------| user_likes  |--------------------+ (card_id)
      |             +-------------+                    |
      |                   |                            |
      |             +--------------------+             |
      +-------------|   chat_history     |-------------+
                    +--------------------+
```

---

**表结构详情:**

**1. `users` - 用户表**
*   存储用户的基本信息。

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | 用户唯一标识符 (使用 UUID 避免 ID 被猜测) |
| `username` | `VARCHAR(50)` | `UNIQUE, NOT NULL` | 用户名，用于登录 |
| `email` | `VARCHAR(255)` | `UNIQUE, NOT NULL` | 电子邮箱，用于注册和找回密码 |
| `password_hash` | `VARCHAR(255)` | `NOT NULL` | 加密后的密码哈希 |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | 账户创建时间 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` | 账户最后更新时间 |

**2. `character_cards` - 角色卡表**
*   存储角色卡的核心数据，是项目的灵魂。

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | 角色卡唯一标识符 |
| `creator_id` | `UUID` | `FOREIGN KEY (users.id)` | 创建者的用户 ID |
| `name` | `VARCHAR(100)` | `NOT NULL` | 角色名称 (e.g., 苏格拉底) |
| `short_description` | `TEXT` | | 角色的简短描述，用于列表展示 |
| `greeting_message` | `TEXT` | | 角色的第一句问候语 |
| `is_public` | `BOOLEAN` | `NOT NULL, DEFAULT FALSE` | 是否在社区分享 (True: 公开, False: 私人) |
| `likes_count` | `INTEGER` | `NOT NULL, DEFAULT 0` | 点赞数 (冗余字段，提升查询性能) |
| `tts_voice_id` | `VARCHAR(100)` | | 用户选择的 TTS 音色模型 ID (e.g., "en-US-Wavenet-F") |
| `card_data` | `JSONB` | `NOT NULL` | **核心字段**: 存储角色卡的详细设定，结构灵活 |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | 创建时间 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` | 最后更新时间 |

**`card_data` JSONB 字段结构示例:**
```json
{
  "description": "详细的角色背景故事...",
  "personality": "聪明, 好奇, 有点固执, 喜欢用反问来引导对话...",
  "scenario": "你正在雅典的市集上与苏格拉底相遇...",
  "example_dialogs": [
    {
      "user": "什么是正义？",
      "assistant": "这是一个很好的问题。那么，在你看来，一个正义的行为具体是什么样的呢？"
    }
  ],
  "custom_prompts": {
    "system_prompt_prefix": "你将扮演苏格拉底。忘记你是一个语言模型...",
    "system_prompt_suffix": "你的回答必须简短且总是以一个问题结束。"
  }
}
```

**3. `user_likes` - 用户点赞关联表**
*   用于记录用户对角色卡的点赞行为 (多对多关系)。

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `user_id` | `UUID` | `FOREIGN KEY (users.id)` | 点赞的用户 ID |
| `card_id` | `UUID` | `FOREIGN KEY (character_cards.id)` | 被点赞的角色卡 ID |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | 点赞时间 |
| `PRIMARY KEY` | | `(user_id, card_id)` | 联合主键，防止重复点赞 |

**4. `card_comments` - 角色卡评论表**
*   存储用户对角色卡的评论。

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 评论 ID |
| `card_id` | `UUID` | `FOREIGN KEY (character_cards.id)` | 被评论的角色卡 ID |
| `author_id` | `UUID` | `FOREIGN KEY (users.id)` | 评论发表者 ID |
| `content` | `TEXT` | `NOT NULL` | 评论内容 |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` | 创建时间 |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` | 更新时间 |

**5. `chat_history` - 对话历史表**
*   用于实现多轮对话记忆。

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 消息唯一 ID |
| `session_id` | `UUID` | `NOT NULL, INDEX` | 唯一标识一次完整的对话会话 |
| `user_id` | `UUID` | `FOREIGN KEY (users.id)` | 参与对话的用户 ID |
| `card_id` | `UUID` | `FOREIGN KEY (character_cards.id)` | 对话的角色卡 ID |
| `role` | `VARCHAR(20)` | `NOT NULL` | 角色 ('user' 或 'assistant') |
| `content` | `TEXT` | `NOT NULL` | 对话的文本内容 |
| `timestamp` | `TIMESTAMPTZ` | `NOT NULL` | 消息时间戳 |

**工作流简述：**
1.  用户登录后，通过 REST API 获取角色卡列表。
2.  用户选择一张角色卡，点击“开始聊天”。
3.  前端建立到后端 WebSocket Gateway 的连接，并开始推送音频流。
4.  后端收到连接请求，生成一个 `session_id`，并从 `chat_history` 表中加载该用户与该角色的最近 N 条对话记录。
5.  后端将历史记录和从 `character_cards` 表中读取的 `card_data` (格式化成一个巨大的 System Prompt) 一同构建初始上下文。
6.  后端与 Gemini API 建立双向流式连接，将用户音频流转发过去。
7.  后端接收 Gemini 返回的文本流，经过 TTS 服务，最终将音频流通过 WebSocket 推送回前端。
8.  对话结束后，后端将本次会话 (`session_id`) 的所有交流内容异步存入 `chat_history` 表。

这份设计为你提供了一个清晰、可扩展且技术上可行的蓝图。它优先保证了 MVP 核心功能的实现，同时也为未来的功能迭代留出了空间。