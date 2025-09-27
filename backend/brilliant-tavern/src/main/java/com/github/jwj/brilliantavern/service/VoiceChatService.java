package com.github.jwj.brilliantavern.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jwj.brilliantavern.dto.voice.VoiceChatSessionRequest;
import com.github.jwj.brilliantavern.dto.voice.VoiceChatSessionResponse;
import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.github.jwj.brilliantavern.entity.ChatHistory;
import com.github.jwj.brilliantavern.entity.User;
import com.github.jwj.brilliantavern.exception.BusinessException;
import com.github.jwj.brilliantavern.repository.CharacterCardRepository;
import com.github.jwj.brilliantavern.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 语音聊天服务
 * 负责语音会话管理、对话历史处理等核心功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceChatService {
    
    private final ChatHistoryRepository chatHistoryRepository;
    private final CharacterCardRepository characterCardRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // Redis键前缀
    private static final String SESSION_KEY_PREFIX = "voice_chat_session:";
    private static final String USER_SESSION_KEY_PREFIX = "user_session:";
    private static final int SESSION_EXPIRE_HOURS = 2; // 会话过期时间2小时

    /**
     * 创建语音聊天会话
     */
    public VoiceChatSessionResponse createSession(VoiceChatSessionRequest request, User user) {
        log.info("用户 {} 创建语音聊天会话，角色卡ID: {}", user.getUsername(), request.getCharacterCardId());
        
        // 验证角色卡存在性
        CharacterCard characterCard = characterCardRepository.findById(request.getCharacterCardId())
                .orElseThrow(() -> new BusinessException("角色卡不存在"));
        
        // 检查角色卡访问权限
        if (!characterCard.getIsPublic() && !characterCard.getCreatorId().equals(user.getId())) {
            throw new BusinessException("无权访问此角色卡");
        }
        
        // 生成或使用现有会话ID
        UUID sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID();
        
        // 构建会话信息
        VoiceChatSessionResponse session = VoiceChatSessionResponse.builder()
                .sessionId(sessionId)
                .characterCardId(characterCard.getId())
                .characterName(characterCard.getName())
                .greetingMessage(characterCard.getGreetingMessage())
                .ttsVoiceId(characterCard.getTtsVoiceId())
                .createdAt(OffsetDateTime.now())
                .websocketEndpoint("/ws/voice-chat")
                .build();
        
        // 将会话信息存储到Redis
        String sessionKey = SESSION_KEY_PREFIX + sessionId.toString();
    SessionInfo sessionInfo = SessionInfo.builder()
                .sessionId(sessionId)
                .userId(user.getId())
                .characterCardId(characterCard.getId())
        .characterCard(cloneCharacterCard(characterCard))
                .user(user)
                .createdAt(OffsetDateTime.now())
                .isActive(true)
                .build();
        
        redisTemplate.opsForValue().set(sessionKey, sessionInfo, SESSION_EXPIRE_HOURS, TimeUnit.HOURS);
        
        // 记录用户活跃会话
        String userSessionKey = USER_SESSION_KEY_PREFIX + user.getId().toString();
        redisTemplate.opsForSet().add(userSessionKey, sessionId.toString());
        redisTemplate.expire(userSessionKey, SESSION_EXPIRE_HOURS, TimeUnit.HOURS);
        
        log.info("语音聊天会话创建成功，会话ID: {}", sessionId);
        return session;
    }

    /**
     * 获取会话信息
     */
    public SessionInfo getSession(UUID sessionId) {
        String sessionKey = SESSION_KEY_PREFIX + sessionId.toString();
        SessionInfo sessionInfo = mapToSessionInfo(redisTemplate.opsForValue().get(sessionKey));
        
        if (sessionInfo == null) {
            throw new BusinessException("会话不存在或已过期");
        }
        
        return sessionInfo;
    }

    /**
     * 保存对话历史
     */
    public void saveChatHistory(UUID sessionId, UUID userId, UUID cardId, 
                               ChatHistory.Role role, String content) {
        ChatHistory chatHistory = ChatHistory.builder()
                .sessionId(sessionId)
                .userId(userId)
                .cardId(cardId)
                .role(role)
                .content(content)
                .timestamp(OffsetDateTime.now())
                .build();
        
        chatHistoryRepository.save(chatHistory);
        log.debug("保存对话历史: sessionId={}, role={}, content length={}", 
                sessionId, role, content.length());
    }

    /**
     * 获取历史对话
     */
    public List<ChatHistory> getChatHistory(UUID userId, UUID cardId, int limit) {
        return chatHistoryRepository.findRecentChatHistory(userId, cardId, 
                PageRequest.of(0, limit));
    }

    /**
     * 关闭会话
     */
    public void closeSession(UUID sessionId, UUID userId) {
        String sessionKey = SESSION_KEY_PREFIX + sessionId.toString();
        SessionInfo sessionInfo = mapToSessionInfo(redisTemplate.opsForValue().get(sessionKey));
        
        if (sessionInfo != null) {
            sessionInfo.setIsActive(false);
            sessionInfo.setClosedAt(OffsetDateTime.now());
            redisTemplate.opsForValue().set(sessionKey, sessionInfo, 30, TimeUnit.MINUTES); // 保留30分钟用于清理
        }
        
        // 从用户活跃会话中移除
        String userSessionKey = USER_SESSION_KEY_PREFIX + userId.toString();
        redisTemplate.opsForSet().remove(userSessionKey, sessionId.toString());
        
        log.info("语音聊天会话关闭: sessionId={}, userId={}", sessionId, userId);
    }

    /**
     * 检查会话是否活跃
     */
    public boolean isSessionActive(UUID sessionId) {
        SessionInfo sessionInfo = getSession(sessionId);
        return sessionInfo.getIsActive();
    }

    /**
     * 延长会话过期时间
     */
    public void extendSession(UUID sessionId) {
        String sessionKey = SESSION_KEY_PREFIX + sessionId.toString();
        redisTemplate.expire(sessionKey, SESSION_EXPIRE_HOURS, TimeUnit.HOURS);
    }

    private SessionInfo mapToSessionInfo(Object cacheValue) {
        if (cacheValue == null) {
            return null;
        }
        if (cacheValue instanceof SessionInfo sessionInfo) {
            return sessionInfo;
        }
        try {
            return objectMapper.convertValue(cacheValue, SessionInfo.class);
        } catch (IllegalArgumentException ex) {
            log.error("会话信息反序列化失败, 类型={}", cacheValue.getClass(), ex);
            throw new BusinessException("会话数据已失效，请重新创建会话");
        }
    }

    private CharacterCard cloneCharacterCard(CharacterCard original) {
        if (original == null) {
            return null;
        }
        return CharacterCard.builder()
                .id(original.getId())
                .creatorId(original.getCreatorId())
                .name(original.getName())
                .shortDescription(original.getShortDescription())
                .greetingMessage(original.getGreetingMessage())
                .isPublic(original.getIsPublic())
                .likesCount(original.getLikesCount())
                .ttsVoiceId(original.getTtsVoiceId())
                .avatarUrl(original.getAvatarUrl())
                .cardData(original.getCardData())
                .createdAt(original.getCreatedAt())
                .updatedAt(original.getUpdatedAt())
                .build();
    }

    /**
     * 会话信息内部类
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SessionInfo implements java.io.Serializable {
        private UUID sessionId;
        private UUID userId;
        private UUID characterCardId;
        private CharacterCard characterCard;
        private User user;
        private OffsetDateTime createdAt;
        private OffsetDateTime closedAt;
        private Boolean isActive;
    }
}
