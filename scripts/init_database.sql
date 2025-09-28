-- =====================================
-- BrilliantTavern Database Initialization Script
-- =====================================

-- 创建数据库扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS vector;

-- =====================================
-- 1. 用户表 (users)
-- =====================================
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 用户表索引
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);

-- =====================================
-- 2. 角色卡表 (character_cards)
-- =====================================
CREATE TABLE IF NOT EXISTS character_cards (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    creator_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    short_description TEXT,
    greeting_message TEXT,
    avatar_url TEXT,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    likes_count INTEGER NOT NULL DEFAULT 0,
    comments_count INTEGER NOT NULL DEFAULT 0,
    tts_voice_id VARCHAR(100),
    voice_language VARCHAR(10) NOT NULL DEFAULT 'zh',
    subtitle_language VARCHAR(10) NOT NULL DEFAULT 'zh',
    card_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 角色卡表索引
CREATE INDEX IF NOT EXISTS idx_character_cards_creator_id ON character_cards(creator_id);
CREATE INDEX IF NOT EXISTS idx_character_cards_is_public ON character_cards(is_public);
CREATE INDEX IF NOT EXISTS idx_character_cards_likes_count ON character_cards(likes_count DESC);
CREATE INDEX IF NOT EXISTS idx_character_cards_comments_count ON character_cards(comments_count DESC);
CREATE INDEX IF NOT EXISTS idx_character_cards_created_at ON character_cards(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_character_cards_name ON character_cards USING gin(name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_character_cards_card_data ON character_cards USING gin(card_data);
CREATE INDEX IF NOT EXISTS idx_character_cards_voice_language ON character_cards(voice_language);
CREATE INDEX IF NOT EXISTS idx_character_cards_subtitle_language ON character_cards(subtitle_language);

-- =====================================
-- 3. 用户点赞关联表 (user_likes)
-- =====================================
CREATE TABLE IF NOT EXISTS user_likes (
    user_id UUID NOT NULL,
    card_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, card_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (card_id) REFERENCES character_cards(id) ON DELETE CASCADE
);

-- 用户点赞表索引
CREATE INDEX IF NOT EXISTS idx_user_likes_card_id ON user_likes(card_id);
CREATE INDEX IF NOT EXISTS idx_user_likes_created_at ON user_likes(created_at DESC);

-- =====================================
-- 4. 角色卡评论表 (card_comments)
-- =====================================
CREATE TABLE IF NOT EXISTS card_comments (
    id BIGSERIAL PRIMARY KEY,
    card_id UUID NOT NULL,
    author_id UUID NOT NULL,
    content TEXT NOT NULL,
    likes_count INTEGER NOT NULL DEFAULT 0,
    parent_comment_id BIGINT,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    pinned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (card_id) REFERENCES character_cards(id) ON DELETE CASCADE,
    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_comment_id) REFERENCES card_comments(id) ON DELETE CASCADE
);

-- 评论表索引
CREATE INDEX IF NOT EXISTS idx_card_comments_card_id ON card_comments(card_id);
CREATE INDEX IF NOT EXISTS idx_card_comments_author_id ON card_comments(author_id);
CREATE INDEX IF NOT EXISTS idx_card_comments_created_at ON card_comments(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_card_comments_likes_count ON card_comments(likes_count DESC);
CREATE INDEX IF NOT EXISTS idx_card_comments_parent_id ON card_comments(parent_comment_id);
CREATE INDEX IF NOT EXISTS idx_card_comments_is_pinned ON card_comments(is_pinned, pinned_at DESC);
CREATE INDEX IF NOT EXISTS idx_card_comments_card_created ON card_comments(card_id, created_at DESC);

-- =====================================
-- 4.1. 评论点赞关联表 (comment_likes)
-- =====================================
CREATE TABLE IF NOT EXISTS comment_likes (
    user_id UUID NOT NULL,
    comment_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, comment_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (comment_id) REFERENCES card_comments(id) ON DELETE CASCADE
);

-- 评论点赞表索引
CREATE INDEX IF NOT EXISTS idx_comment_likes_comment_id ON comment_likes(comment_id);
CREATE INDEX IF NOT EXISTS idx_comment_likes_created_at ON comment_likes(created_at DESC);

-- =====================================
-- 5. TTS语音表 (tts_voices)
-- =====================================
CREATE TABLE IF NOT EXISTS tts_voices (
    id BIGSERIAL PRIMARY KEY,
    reference_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    creator_id UUID NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    reference_text TEXT,
    likes_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE
);

-- TTS语音表索引
CREATE INDEX IF NOT EXISTS idx_tts_voices_reference_id ON tts_voices(reference_id);
CREATE INDEX IF NOT EXISTS idx_tts_voices_creator_id ON tts_voices(creator_id);
CREATE INDEX IF NOT EXISTS idx_tts_voices_is_public ON tts_voices(is_public);
CREATE INDEX IF NOT EXISTS idx_tts_voices_deleted ON tts_voices(deleted);
CREATE INDEX IF NOT EXISTS idx_tts_voices_created_at ON tts_voices(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tts_voices_name ON tts_voices USING gin(name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_tts_voices_likes_count ON tts_voices(likes_count DESC);

-- =====================================
-- 7. TTS语音点赞表 (tts_voice_likes)
-- =====================================
CREATE TABLE IF NOT EXISTS tts_voice_likes (
    user_id UUID NOT NULL,
    voice_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, voice_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (voice_id) REFERENCES tts_voices(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tts_voice_likes_voice_id ON tts_voice_likes(voice_id);
CREATE INDEX IF NOT EXISTS idx_tts_voice_likes_created_at ON tts_voice_likes(created_at DESC);

-- =====================================
-- 6. 对话历史表 (chat_history)
-- =====================================
CREATE TABLE IF NOT EXISTS chat_history (
    id BIGSERIAL PRIMARY KEY,
    history_id UUID NOT NULL,
    session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    card_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant')),
    content TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    title VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (card_id) REFERENCES character_cards(id) ON DELETE CASCADE
);

-- 对话历史表索引
CREATE INDEX IF NOT EXISTS idx_chat_history_history_id ON chat_history(history_id);
CREATE INDEX IF NOT EXISTS idx_chat_history_session_id ON chat_history(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_history_user_id ON chat_history(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_history_card_id ON chat_history(card_id);
CREATE INDEX IF NOT EXISTS idx_chat_history_timestamp ON chat_history(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_chat_history_user_history ON chat_history(user_id, history_id);
CREATE INDEX IF NOT EXISTS idx_chat_history_history_timestamp ON chat_history(history_id, timestamp);

-- =====================================
-- 触发器函数：自动更新 updated_at 字段
-- =====================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 为相关表添加 updated_at 触发器
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_character_cards_updated_at BEFORE UPDATE ON character_cards
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_card_comments_updated_at BEFORE UPDATE ON card_comments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_tts_voices_updated_at BEFORE UPDATE ON tts_voices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =====================================
-- 触发器函数：自动更新角色卡点赞数
-- =====================================
CREATE OR REPLACE FUNCTION update_likes_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE character_cards SET likes_count = likes_count + 1 WHERE id = NEW.card_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE character_cards SET likes_count = likes_count - 1 WHERE id = OLD.card_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ language 'plpgsql';

-- 为点赞表添加触发器
CREATE TRIGGER update_character_cards_likes_count 
    AFTER INSERT OR DELETE ON user_likes
    FOR EACH ROW EXECUTE FUNCTION update_likes_count();

-- =====================================
-- 触发器函数：自动更新TTS语音点赞数
-- =====================================
CREATE OR REPLACE FUNCTION update_voice_likes_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE tts_voices SET likes_count = likes_count + 1 WHERE id = NEW.voice_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE tts_voices SET likes_count = GREATEST(likes_count - 1, 0) WHERE id = OLD.voice_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_tts_voice_likes_count
    AFTER INSERT OR DELETE ON tts_voice_likes
    FOR EACH ROW EXECUTE FUNCTION update_voice_likes_count();

-- =====================================
-- 7.1. 触发器函数：自动更新评论点赞数
-- =====================================
CREATE OR REPLACE FUNCTION update_comment_likes_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE card_comments SET likes_count = likes_count + 1 WHERE id = NEW.comment_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE card_comments SET likes_count = GREATEST(likes_count - 1, 0) WHERE id = OLD.comment_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ language 'plpgsql';

-- 为评论点赞表添加触发器
CREATE TRIGGER update_comment_likes_count_trigger
    AFTER INSERT OR DELETE ON comment_likes
    FOR EACH ROW EXECUTE FUNCTION update_comment_likes_count();

-- =====================================
-- 7.2. 触发器函数：自动设置置顶时间
-- =====================================
CREATE OR REPLACE FUNCTION update_pinned_at()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.is_pinned = TRUE AND OLD.is_pinned = FALSE THEN
        NEW.pinned_at = CURRENT_TIMESTAMP;
    ELSIF NEW.is_pinned = FALSE THEN
        NEW.pinned_at = NULL;
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 为评论表添加置顶时间触发器
CREATE TRIGGER update_pinned_at_trigger
    BEFORE UPDATE ON card_comments
    FOR EACH ROW EXECUTE FUNCTION update_pinned_at();

-- =====================================
-- 8. 角色全局记忆表 (character_memories)
-- =====================================
CREATE TABLE IF NOT EXISTS character_memories (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    character_card_id UUID NOT NULL,
    memory_content TEXT NOT NULL,
    embedding vector(1536) NOT NULL, -- 限制为1536维以支持HNSW索引(最大2000维)
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (character_card_id) REFERENCES character_cards(id) ON DELETE CASCADE
);

-- 角色记忆表索引
CREATE INDEX IF NOT EXISTS idx_character_memories_user_id ON character_memories(user_id);
CREATE INDEX IF NOT EXISTS idx_character_memories_character_card_id ON character_memories(character_card_id);
CREATE INDEX IF NOT EXISTS idx_character_memories_user_card ON character_memories(user_id, character_card_id);
CREATE INDEX IF NOT EXISTS idx_character_memories_created_at ON character_memories(created_at DESC);

-- 向量相似度搜索索引 (使用 HNSW 算法，适合高维向量搜索)
CREATE INDEX IF NOT EXISTS idx_character_memories_embedding ON character_memories 
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- 为角色记忆表添加 updated_at 触发器
CREATE TRIGGER update_character_memories_updated_at BEFORE UPDATE ON character_memories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();


