-- =====================================
-- 添加 attachments 列到 chat_history 表
-- 用于存储图像生成等附件信息
-- =====================================

-- 检查列是否已存在，如果不存在则添加
DO $$ 
BEGIN 
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name='chat_history' 
        AND column_name='attachments'
    ) THEN
        ALTER TABLE chat_history ADD COLUMN attachments TEXT;
        
        -- 添加注释
        COMMENT ON COLUMN chat_history.attachments IS 'JSON格式附件信息，存储图片URI等数据';
        
        RAISE NOTICE 'Successfully added attachments column to chat_history table';
    ELSE
        RAISE NOTICE 'Column attachments already exists in chat_history table';
    END IF;
END $$;