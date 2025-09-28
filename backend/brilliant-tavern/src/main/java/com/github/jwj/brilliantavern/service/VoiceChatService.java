package com.github.jwj.brilliantavern.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jwj.brilliantavern.dto.voice.ChatSessionSummaryDTO;
import com.github.jwj.brilliantavern.dto.voice.VoiceChatSessionRequest;
import com.github.jwj.brilliantavern.dto.voice.VoiceChatSessionResponse;
import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.github.jwj.brilliantavern.entity.ChatHistory;
import com.github.jwj.brilliantavern.entity.User;
import com.github.jwj.brilliantavern.exception.BusinessException;
import com.github.jwj.brilliantavern.repository.CharacterCardRepository;
import com.github.jwj.brilliantavern.repository.ChatHistoryRepository;
import com.github.jwj.brilliantavern.repository.TTSVoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
    private final TTSVoiceRepository ttsVoiceRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AIService aiService;
    private final SimpMessagingTemplate messagingTemplate;
    
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
        
        // 获取TTS音色的reference_id
        String ttsReferenceId = null;
        if (characterCard.getTtsVoiceId() != null) {
            try {
                Long voiceId = Long.parseLong(characterCard.getTtsVoiceId());
                ttsReferenceId = ttsVoiceRepository.findById(voiceId)
                        .map(voice -> voice.getReferenceId())
                        .orElse(null);
            } catch (NumberFormatException e) {
                log.warn("TTS音色ID格式错误: {}", characterCard.getTtsVoiceId());
            }
        }
        
        // 构建会话信息
        VoiceChatSessionResponse session = VoiceChatSessionResponse.builder()
                .sessionId(sessionId)
                .characterCardId(characterCard.getId())
                .characterName(characterCard.getName())
                .greetingMessage(characterCard.getGreetingMessage())
                .ttsVoiceId(ttsReferenceId) // 使用reference_id而不是数据库ID
                .createdAt(OffsetDateTime.now())
                .websocketEndpoint("/ws/voice-chat")
                .build();
        
        // 生成或获取historyId
        UUID historyId;
        boolean isNewHistory = false;
        if (request.getSessionId() != null && !Boolean.TRUE.equals(request.getCreateNew())) {
            // 如果是恢复现有会话，需要获取对应的historyId
            // 这里简化处理，使用sessionId作为historyId（在实际应用中可能需要更复杂的逻辑）
            historyId = request.getSessionId();
            isNewHistory = false;
        } else {
            // 新对话生成新的historyId
            historyId = UUID.randomUUID();
            isNewHistory = true;
        }

        // 将会话信息存储到Redis
        String sessionKey = SESSION_KEY_PREFIX + sessionId.toString();
        SessionInfo sessionInfo = SessionInfo.builder()
                .sessionId(sessionId)
                .historyId(historyId)
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
        
        // 注意: 新的逻辑是不再在创建会话时保存开场白，而是等第一轮对话完成后再保存
        // 这样避免了没有实际对话就产生历史记录的问题
        
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
    public void saveChatHistory(UUID historyId, UUID sessionId, UUID userId, UUID cardId, 
                               ChatHistory.Role role, String content) {
        ChatHistory chatHistory = ChatHistory.builder()
                .historyId(historyId)
                .sessionId(sessionId)
                .userId(userId)
                .cardId(cardId)
                .role(role)
                .content(content)
                .timestamp(OffsetDateTime.now())
                .build();
        
        chatHistoryRepository.save(chatHistory);
        log.debug("保存对话历史: historyId={}, sessionId={}, role={}, content length={}", 
                historyId, sessionId, role, content.length());
    }

    /**
     * 保存一轮完整对话（包括用户消息和AI回复，包含开场白）
     * 第一轮对话后会异步生成标题
     */
    @Transactional
    public void saveCompleteRound(UUID historyId, UUID sessionId, UUID userId, UUID cardId,
                                 String userMessage, String assistantMessage, String greetingMessage) {
        saveCompleteRound(historyId, sessionId, userId, cardId, userMessage, assistantMessage, greetingMessage, null);
    }
    
    public void saveCompleteRound(UUID historyId, UUID sessionId, UUID userId, UUID cardId,
                                 String userMessage, String assistantMessage, String greetingMessage, String attachments) {
        OffsetDateTime now = OffsetDateTime.now();
        
        // 检查是否需要生成标题（历史记录为空 或者 已有记录但标题为空）
        List<ChatHistory> existingHistory = chatHistoryRepository.findByHistoryIdOrderByTimestampAsc(historyId);
        boolean isFirstRound = existingHistory.isEmpty();
        boolean needsTitle = isFirstRound || existingHistory.stream().noneMatch(h -> 
            StringUtils.hasText(h.getTitle()));
        
        log.debug("标题生成检查: historyId={}, isFirstRound={}, needsTitle={}", 
                historyId, isFirstRound, needsTitle);
        
        // 如果是第一轮且有开场白，先保存开场白
        if (isFirstRound && greetingMessage != null && !greetingMessage.trim().isEmpty()) {
            ChatHistory greetingHistory = ChatHistory.builder()
                    .historyId(historyId)
                    .sessionId(sessionId)
                    .userId(userId)
                    .cardId(cardId)
                    .role(ChatHistory.Role.ASSISTANT)
                    .content(greetingMessage)
                    .timestamp(now.minusNanos(1000000)) // 稍微早一点的时间戳
                    .build();
            chatHistoryRepository.save(greetingHistory);
        }
        
        // 保存用户消息
        ChatHistory userHistory = ChatHistory.builder()
                .historyId(historyId)
                .sessionId(sessionId)
                .userId(userId)
                .cardId(cardId)
                .role(ChatHistory.Role.USER)
                .content(userMessage)
                .timestamp(now)
                .build();
        chatHistoryRepository.save(userHistory);
        
        // 保存AI回复
        ChatHistory assistantHistory = ChatHistory.builder()
                .historyId(historyId)
                .sessionId(sessionId)
                .userId(userId)
                .cardId(cardId)
                .role(ChatHistory.Role.ASSISTANT)
                .content(assistantMessage)
                .timestamp(now.plusNanos(1000000)) // 稍微晚一点的时间戳
                .attachments(attachments) // 保存附件信息
                .build();
        chatHistoryRepository.save(assistantHistory);
        
        log.info("保存完整对话轮次: historyId={}, sessionId={}, isFirstRound={}, needsTitle={}", 
                historyId, sessionId, isFirstRound, needsTitle);
        
        // 如果需要生成标题，异步生成标题
        if (needsTitle) {
            generateTitleAsync(historyId, sessionId, userId, userMessage, assistantMessage);
        }
    }

    /**
     * 异步生成对话标题
     */
    @Async
    public void generateTitleAsync(UUID historyId, UUID sessionId, UUID userId, String userMessage, String assistantMessage) {
        try {
            // 构建用于生成标题的提示
            String titlePrompt = String.format(
                    """
                    请为以下对话生成一个简洁的中文标题，要求：
                    1. 不超过10个字符
                    2. 只返回标题，不要引号、解释或其他内容
                    3. 概括对话主要内容
                    
                    用户：%s
                    助手：%s
                    
                    标题：""",
                userMessage, assistantMessage
            );
            
            log.info("开始异步生成标题: historyId={}", historyId);
            
            // 调用AI服务生成标题
            String generatedTitle = aiService.generateSimpleText(titlePrompt);
            
            if (generatedTitle != null && !generatedTitle.trim().isEmpty()) {
                String title = generatedTitle.trim();
                // 确保标题不超过10个字符
                if (title.length() > 10) {
                    title = title.substring(0, 10);
                }
                
                // 更新数据库中的标题
                int updatedRows = chatHistoryRepository.updateHistoryTitle(historyId, title);
                
                log.info("标题生成完成: historyId={}, title={}, updatedRows={}", historyId, title, updatedRows);
                
                // 通过WebSocket通知前端标题已生成（发送到语音对话页面）
                sendTitleUpdateToUser(sessionId, userId, historyId, title);
                
                // 同时通知侧边栏更新（发送到Dashboard）
                sendHistoryUpdateToUser(userId);
                
            } else {
                log.warn("AI生成的标题为空: historyId={}", historyId);
            }
            
        } catch (Exception e) {
            log.error("生成标题失败: historyId={}", historyId, e);
        }
    }

    /**
     * 通过WebSocket发送标题更新给用户
     */
    private void sendTitleUpdateToUser(UUID sessionId, UUID userId, UUID historyId, String title) {
        try {
            Map<String, Object> titleUpdate = Map.of(
                "type", "TITLE_UPDATE",
                "historyId", historyId.toString(),
                "title", title,
                "timestamp", System.currentTimeMillis()
            );
            
            // 发送到用户的语音对话会话
            String destination = "/topic/voice-chat/" + sessionId.toString();
            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                destination,
                titleUpdate
            );
            
            log.info("发送标题更新通知: userId={}, destination={}, historyId={}, title={}", 
                    userId, destination, historyId, title);
        } catch (Exception e) {
            log.error("发送标题更新通知失败: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    /**
     * 通知用户历史记录已更新
     */
    @Async
    private void sendHistoryUpdateToUser(UUID userId) {
        Map<String, Object> historyUpdate = Map.of(
            "type", "HISTORY_REFRESH",
            "timestamp", System.currentTimeMillis()
        );
        
        messagingTemplate.convertAndSendToUser(
            userId.toString(),
            "/topic/history-updates",
            historyUpdate
        );
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
                .voiceLanguage(original.getVoiceLanguage())
                .subtitleLanguage(original.getSubtitleLanguage())
                .avatarUrl(original.getAvatarUrl())
                .cardData(original.getCardData())
                .createdAt(original.getCreatedAt())
                .updatedAt(original.getUpdatedAt())
                .build();
    }

    /**
     * 根据会话ID获取历史对话
     */
    public List<ChatHistory> getChatHistoryBySession(UUID sessionId) {
        return chatHistoryRepository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    /**
     * 根据历史记录ID获取对话历史
     */
    public List<ChatHistory> getChatHistoryByHistoryId(UUID historyId) {
        return chatHistoryRepository.findByHistoryIdOrderByTimestampAsc(historyId);
    }
    
    /**
     * 更新历史记录的附件信息
     */
    @Transactional
    public void updateHistoryAttachments(UUID historyId, String attachmentsJson) {
        log.info("开始更新历史记录附件信息: historyId={}, attachments={}", historyId, attachmentsJson);
        
        int updatedRows = chatHistoryRepository.updateHistoryAttachments(historyId, attachmentsJson);
        
        log.info("历史记录附件信息更新完成: historyId={}, updatedRows={}, attachments={}", 
                historyId, updatedRows, attachmentsJson);
        
        if (updatedRows == 0) {
            log.warn("没有找到需要更新的历史记录: historyId={}", historyId);
        }
    }

    /**
     * 删除历史记录
     */
    @Transactional
    public void deleteHistory(UUID historyId, UUID userId) {
        // 验证历史记录是否存在且属于当前用户
        List<ChatHistory> historyRecords = chatHistoryRepository.findByHistoryIdOrderByTimestampAsc(historyId);
        if (historyRecords.isEmpty()) {
            throw new BusinessException("历史记录不存在");
        }
        
        // 检查权限：确保历史记录属于当前用户
        if (!historyRecords.get(0).getUserId().equals(userId)) {
            throw new BusinessException("无权删除此历史记录");
        }
        
        // 删除该historyId下的所有聊天记录
        chatHistoryRepository.deleteByHistoryId(historyId);
        
        log.info("删除历史记录成功: historyId={}, userId={}, 删除记录数={}", 
                historyId, userId, historyRecords.size());
    }

    /**
     * 获取用户的所有历史记录列表
     */
    public List<ChatSessionSummaryDTO> getUserChatHistories(UUID userId, int limit) {
        List<Object[]> results = chatHistoryRepository.findUserHistoriesSummary(userId, PageRequest.of(0, limit));
        return results.stream().map(this::convertToHistorySummary).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取用户与指定角色卡的历史记录列表
     */
    public List<ChatSessionSummaryDTO> getUserCardChatHistories(UUID userId, UUID cardId, int limit) {
        List<Object[]> results = chatHistoryRepository.findUserCardHistoriesSummary(userId, cardId, PageRequest.of(0, limit));
        return results.stream().map(this::convertToHistorySummary).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 将查询结果转换为ChatSessionSummaryDTO
     */
    private ChatSessionSummaryDTO convertToHistorySummary(Object[] result) {
        UUID historyId = (UUID) result[0];
        UUID cardId = (UUID) result[1];
        java.time.OffsetDateTime startTime = (java.time.OffsetDateTime) result[2];
        java.time.OffsetDateTime lastTime = (java.time.OffsetDateTime) result[3];
        Long messageCount = (Long) result[4];
        String firstMessage = (String) result[5];
        String dbTitle = (String) result[6]; // 数据库中的标题
        
        // 获取角色卡名称
        String cardName = characterCardRepository.findById(cardId)
                .map(card -> card.getName())
                .orElse("未知角色");
        
        // 使用数据库中的标题，如果没有则生成默认标题
        String title = (dbTitle != null && !dbTitle.trim().isEmpty()) 
                ? dbTitle 
                : "新对话"; // 简化默认标题，因为现在不再使用firstMessage
        
        return ChatSessionSummaryDTO.builder()
                .sessionId(historyId) // 使用historyId作为sessionId，保持前端兼容性
                .cardId(cardId)
                .cardName(cardName)
                .startTime(startTime)
                .lastTime(lastTime)
                .messageCount(messageCount)
                .firstMessage(firstMessage)
                .title(title)
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
        private UUID historyId;
        private UUID userId;
        private UUID characterCardId;
        private CharacterCard characterCard;
        private User user;
        private OffsetDateTime createdAt;
        private OffsetDateTime closedAt;
        private Boolean isActive;
    }
}
