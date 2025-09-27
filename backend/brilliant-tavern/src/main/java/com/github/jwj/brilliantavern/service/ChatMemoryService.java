package com.github.jwj.brilliantavern.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话记忆管理服务，使用Redis存储对话历史
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String CHAT_HISTORY_PREFIX = "chat:history:";
    private static final long DEFAULT_EXPIRE_MINUTES = 120; // 2小时过期

    /**
     * 简化的消息类，用于序列化
     */
    public static class SerializableMessage {
        public String role;
        public String text;
        
        public SerializableMessage() {}
        
        public SerializableMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    /**
     * 添加用户消息到对话历史
     */
    public void addUserMessage(String conversationId, String message) {
        SerializableMessage userMessage = new SerializableMessage("user", message);
        addToHistory(conversationId, userMessage);
    }

    /**
     * 添加助手消息到对话历史
     */
    public void addAssistantMessage(String conversationId, String message) {
        SerializableMessage assistantMessage = new SerializableMessage("model", message);
        addToHistory(conversationId, assistantMessage);
    }

    /**
     * 获取对话历史
     */
    public List<Content> getHistory(String conversationId) {
        String key = CHAT_HISTORY_PREFIX + conversationId;
        String historyJson = redisTemplate.opsForValue().get(key);
        
        List<Content> history = new ArrayList<>();
        if (historyJson != null) {
            try {
                List<SerializableMessage> messages = objectMapper.readValue(
                        historyJson, new TypeReference<List<SerializableMessage>>() {});
                
                for (SerializableMessage msg : messages) {
                    Content content = Content.newBuilder()
                            .setRole(msg.role)
                            .addParts(Part.newBuilder().setText(msg.text))
                            .build();
                    history.add(content);
                }
            } catch (Exception e) {
                log.error("解析对话历史失败: conversationId={}", conversationId, e);
            }
        }
        
        log.debug("获取对话历史: conversationId={}, 消息数量={}", conversationId, history.size());
        return history;
    }

    /**
     * 添加消息到对话历史
     */
    private void addToHistory(String conversationId, SerializableMessage message) {
        String key = CHAT_HISTORY_PREFIX + conversationId;
        
        try {
            // 获取现有历史
            List<SerializableMessage> history = new ArrayList<>();
            String existingJson = redisTemplate.opsForValue().get(key);
            if (existingJson != null) {
                history = objectMapper.readValue(existingJson, new TypeReference<List<SerializableMessage>>() {});
            }
            
            // 添加新消息
            history.add(message);
            
            // 限制历史长度
            if (history.size() > 50) { // 最多保留50条消息
                history = history.subList(history.size() - 50, history.size());
            }
            
            // 保存更新后的历史
            String updatedJson = objectMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(key, updatedJson, Duration.ofMinutes(DEFAULT_EXPIRE_MINUTES));
            
            log.debug("添加消息到对话历史: conversationId={}, role={}", conversationId, message.role);
            
        } catch (Exception e) {
            log.error("保存对话历史失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 清除对话历史
     */
    public void clearHistory(String conversationId) {
        String key = CHAT_HISTORY_PREFIX + conversationId;
        redisTemplate.delete(key);
        log.debug("清除对话历史: conversationId={}", conversationId);
    }

    /**
     * 限制对话历史长度，保留最近的消息
     */
    public void limitHistory(String conversationId, int maxMessages) {
        String key = CHAT_HISTORY_PREFIX + conversationId;
        String historyJson = redisTemplate.opsForValue().get(key);
        
        if (historyJson != null) {
            try {
                List<SerializableMessage> history = objectMapper.readValue(
                        historyJson, new TypeReference<List<SerializableMessage>>() {});
                
                if (history.size() > maxMessages) {
                    List<SerializableMessage> limitedHistory = history.subList(
                            history.size() - maxMessages, history.size());
                    
                    String updatedJson = objectMapper.writeValueAsString(limitedHistory);
                    redisTemplate.opsForValue().set(key, updatedJson, Duration.ofMinutes(DEFAULT_EXPIRE_MINUTES));
                    
                    log.debug("限制对话历史长度: conversationId={}, 保留消息数={}", conversationId, maxMessages);
                }
            } catch (Exception e) {
                log.error("限制对话历史失败: conversationId={}", conversationId, e);
            }
        }
    }
}