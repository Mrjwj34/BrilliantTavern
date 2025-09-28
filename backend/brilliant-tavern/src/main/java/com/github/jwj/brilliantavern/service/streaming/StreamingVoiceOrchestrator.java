package com.github.jwj.brilliantavern.service.streaming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jwj.brilliantavern.dto.VoiceMessage;
import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import com.github.jwj.brilliantavern.entity.ChatHistory;
import com.github.jwj.brilliantavern.service.AIService;
import com.github.jwj.brilliantavern.service.VoiceChatService;
import com.github.jwj.brilliantavern.service.ChatMemoryService;
import com.github.jwj.brilliantavern.service.metrics.ConversationMetrics;
import com.github.jwj.brilliantavern.service.streaming.handlers.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流式语音对话协调器
 * 替换原有复杂实现，提供清晰的模块化架构
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingVoiceOrchestrator {

    private final AIService aiService;
    private final VoiceChatService voiceChatService;
    private final ChatMemoryService chatMemoryService;
    private final StreamingContentParser contentParser;
    private final AsyncEventDispatcher eventDispatcher;
    private final RetryService retryService;
    private final ObjectMapper objectMapper;
    
    // 会话状态管理
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    /**
     * 处理语音输入，返回流式事件
     */
    public Flux<VoiceStreamEvent> processVoiceInput(UUID sessionId, VoiceMessageWithMetadata voiceMessage) {
        String sessionIdStr = sessionId.toString();
        String messageId = voiceMessage.messageId();
        
        // 创建会话状态
        SessionState sessionState = createSessionState(sessionId, messageId);
        sessionStates.put(sessionIdStr, sessionState);
        
        log.info("开始处理语音输入: sessionId={}, messageId={}", sessionIdStr, messageId);
        
        return Flux.concat(
            // 1. 发送处理开始事件
            sendProcessingStarted(sessionState),
            
            // 2. 处理AI流式响应
            processAIResponse(sessionState, voiceMessage),
            
            // 3. 发送处理完成事件
            sendProcessingCompleted(sessionState)
        ).onErrorResume(error -> {
            log.error("语音处理失败: sessionId={}, messageId={}", sessionIdStr, messageId, error);
            sessionState.hasProcessingErrors = true;
            sessionState.shouldPersist = false;
            
            // 发送对话轮次丢弃事件
            return Flux.concat(
                    Flux.just(retryService.createRetryFailedEvent(sessionIdStr, messageId, "对话处理", error)),
                    Flux.just(retryService.createRoundDiscardedEvent(sessionIdStr, messageId, "所有重试均失败，丢弃本次对话"))
            );
        }).doFinally(signal -> {
            sessionStates.remove(sessionIdStr);
            log.info("语音处理{}完成: sessionId={}, messageId={}", 
                    sessionState.hasProcessingErrors ? "失败并" : "", sessionIdStr, messageId);
        });
    }
    
    /**
     * 创建会话状态
     */
    private SessionState createSessionState(UUID sessionId, String messageId) {
        VoiceChatService.SessionInfo sessionInfo = voiceChatService.getSession(sessionId);
        voiceChatService.extendSession(sessionId);
        
        ConversationMetrics metrics = ConversationMetrics.start(sessionId.toString(), messageId);
        
        return SessionState.builder()
                .sessionId(sessionId.toString())
                .messageId(messageId)
                .sessionInfo(sessionInfo)
                .metrics(metrics)
                .build();
    }
    
    /**
     * 发送处理开始事件
     */
    private Flux<VoiceStreamEvent> sendProcessingStarted(SessionState sessionState) {
        return Flux.just(VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.PROCESSING_STARTED)
                .sessionId(sessionState.sessionId)
                .messageId(sessionState.messageId)
                .timestamp(Instant.now().toEpochMilli())
                .payload(Map.of(
                        "characterCardId", sessionState.sessionInfo.getCharacterCardId().toString(),
                        "voiceId", getVoiceId(sessionState.sessionInfo)
                ))
                .build());
    }
    
    /**
     * 处理AI流式响应
     */
    private Flux<VoiceStreamEvent> processAIResponse(SessionState sessionState, VoiceMessageWithMetadata voiceMessage) {
        sessionState.metrics.mark("llm_start");
        
        // 获取AI流式响应
        Flux<AIService.AIStreamEvent> aiEvents = aiService.streamVoiceConversation(
                voiceMessage.voiceMessage(),
                sessionState.sessionInfo.getCharacterCard(),
                sessionState.sessionId,
                sessionState.messageId,
                sessionState.sessionInfo.getUserId()
        ).doOnNext(event -> {
            if (event.getType() == AIService.AIStreamEvent.Type.CHUNK) {
                sessionState.metrics.markIfAbsent("llm_first_token");
            }
        }).share();
        
        // 提取流式文本内容
        Flux<String> textStream = aiEvents
                .filter(event -> event.getType() == AIService.AIStreamEvent.Type.CHUNK)
                .map(AIService.AIStreamEvent::getContent)
                .filter(StringUtils::hasText);
        
        // 解析标签并生成事件，包含标签解析错误处理
        Flux<TagEvent> tagEvents = contentParser.parseStream(textStream, sessionState.sessionId, sessionState.messageId)
                .onErrorResume(error -> {
                    // 捕获标签解析错误，将其作为可重试的错误处理
                    if (error instanceof StreamingContentParser.TagParsingException) {
                        log.warn("标签解析失败，将触发重试: sessionId={}, messageId={}, error={}", 
                                sessionState.sessionId, sessionState.messageId, error.getMessage());
                        return Flux.error(error); // 传播错误以触发外层重试机制
                    } else {
                        log.error("标签解析过程中发生未知错误: sessionId={}, messageId={}", 
                                sessionState.sessionId, sessionState.messageId, error);
                        return Flux.error(error);
                    }
                });
        
        // 分发标签事件到各个处理器
        Flux<VoiceStreamEvent> handlerEvents = tagEvents.flatMap(tagEvent -> 
                eventDispatcher.dispatchEvent(tagEvent, sessionState));
        
        // 处理AI完成事件
        Flux<VoiceStreamEvent> completionEvents = aiEvents
                .filter(event -> event.getType() == AIService.AIStreamEvent.Type.COMPLETED)
                .next()
                .flatMapMany(aiEvent -> handleAICompletion(aiEvent, sessionState));
        
        // 处理记忆检索事件
        Flux<VoiceStreamEvent> memoryEvents = aiEvents
                .filter(event -> event.getType() == AIService.AIStreamEvent.Type.MEMORY_RETRIEVAL_STARTED ||
                               event.getType() == AIService.AIStreamEvent.Type.MEMORY_RETRIEVAL_COMPLETED)
                .map(aiEvent -> handleMemoryRetrievalEvent(aiEvent, sessionState));
        
        // 监听所有事件，对图像生成完成事件进行特殊处理
        return Flux.merge(handlerEvents, completionEvents, memoryEvents)
                .doOnNext(event -> {
                    // 监听图像生成完成事件，收集图像信息
                    if (event.getType() == VoiceStreamEvent.Type.METHOD_EXECUTION) {
                        Map<String, Object> payload = event.getPayload();
                        String action = payload != null ? (String) payload.get("action") : null;
                        
                        if (payload != null && "image_generation_completed".equals(action)) {
                            log.info("检测到图像生成完成事件: sessionId={}, messageId={}", 
                                    sessionState.sessionId, sessionState.messageId);
                            collectImageAttachment(event, sessionState);
                        }
                    }
                });
    }
    
    /**
     * 处理记忆检索事件
     */
    private VoiceStreamEvent handleMemoryRetrievalEvent(AIService.AIStreamEvent aiEvent, SessionState sessionState) {
        VoiceStreamEvent.Type eventType = switch (aiEvent.getType()) {
            case MEMORY_RETRIEVAL_STARTED -> VoiceStreamEvent.Type.MEMORY_RETRIEVAL_STARTED;
            case MEMORY_RETRIEVAL_COMPLETED -> VoiceStreamEvent.Type.MEMORY_RETRIEVAL_COMPLETED;
            default -> throw new IllegalArgumentException("不支持的记忆检索事件类型: " + aiEvent.getType());
        };
        
        log.debug("处理记忆检索事件: type={}, content={}, sessionId={}, messageId={}", 
                eventType, aiEvent.getContent(), sessionState.sessionId, sessionState.messageId);
        
        return VoiceStreamEvent.builder()
                .type(eventType)
                .sessionId(sessionState.sessionId)
                .messageId(sessionState.messageId)
                .timestamp(Instant.now().toEpochMilli())
                .payload(Map.of(
                        "message", aiEvent.getContent(),
                        "action", eventType == VoiceStreamEvent.Type.MEMORY_RETRIEVAL_STARTED ? "memory_started" : "memory_completed"
                ))
                .build();
    }
    
    /**
     * 收集图像附件信息
     */
    private void collectImageAttachment(VoiceStreamEvent event, SessionState sessionState) {
        try {
            log.info("开始收集图像附件信息: sessionId={}, messageId={}", 
                    sessionState.sessionId, sessionState.messageId);
            
            Map<String, Object> payload = event.getPayload();
            Map<String, Object> result = (Map<String, Object>) payload.get("result");
            
            log.debug("图像附件payload: {}", payload);
            log.debug("图像附件result: {}", result);
            
            if (result != null) {
                String imageUri = (String) result.get("imageUri");
                String description = (String) result.get("description");
                Boolean isSelf = (Boolean) result.get("isSelf");
                
                log.info("解析图像附件信息: imageUri={}, description={}, isSelf={}", 
                        imageUri, description, isSelf);
                
                if (imageUri != null) {
                    ImageAttachment attachment = new ImageAttachment(
                            imageUri,
                            description != null ? description : "",
                            isSelf != null ? isSelf : false
                    );
                    
                    sessionState.getGeneratedImages().add(attachment);
                    log.info("图像附件已添加到SessionState: sessionId={}, messageId={}, imageUri={}, 总数={}", 
                            sessionState.sessionId, sessionState.messageId, imageUri, 
                            sessionState.getGeneratedImages().size());
                    
                    // 如果对话已经保存，则更新附件信息
                    updateHistoryAttachments(sessionState);
                } else {
                    log.warn("图像URI为空，跳过附件收集: sessionId={}, messageId={}", 
                            sessionState.sessionId, sessionState.messageId);
                }
            } else {
                log.warn("图像附件result为空: sessionId={}, messageId={}", 
                        sessionState.sessionId, sessionState.messageId);
            }
        } catch (Exception e) {
            log.error("收集图像附件信息失败: sessionId={}, messageId={}", 
                    sessionState.sessionId, sessionState.messageId, e);
        }
    }
    
    /**
     * 更新已保存历史记录的附件信息
     */
    private void updateHistoryAttachments(SessionState sessionState) {
        // 使用异步方式更新，避免阻塞主流
        Mono.fromRunnable(() -> {
            try {
                log.info("尝试更新历史记录附件信息: sessionId={}, messageId={}", 
                        sessionState.sessionId, sessionState.messageId);
                
                boolean hasImages = !sessionState.getGeneratedImages().isEmpty();
                
                log.info("更新附件条件检查: hasImages={}, imageCount={}", 
                        hasImages, sessionState.getGeneratedImages().size());
                
                // 只要有图片就尝试更新
                if (hasImages) {
                    // 序列化图像附件信息
                    Map<String, Object> attachmentsData = Map.of("images", sessionState.getGeneratedImages());
                    String attachmentsJson = objectMapper.writeValueAsString(attachmentsData);
                    
                    log.info("准备更新数据库附件信息: historyId={}, attachmentsJson={}", 
                            sessionState.sessionInfo.getHistoryId(), attachmentsJson);
                    
                    // 更新数据库中的附件信息 - 只更新当前会话的最新ASSISTANT消息
                    voiceChatService.updateLatestAssistantMessageAttachments(
                            UUID.fromString(sessionState.sessionId),
                            attachmentsJson
                    );
                    
                    log.info("更新历史记录附件信息成功: sessionId={}, messageId={}, imageCount={}", 
                            sessionState.sessionId, sessionState.messageId, sessionState.getGeneratedImages().size());
                } else {
                    log.info("不满足更新附件条件，跳过更新: sessionId={}, messageId={}", 
                            sessionState.sessionId, sessionState.messageId);
                }
            } catch (Exception e) {
                log.error("更新历史记录附件信息失败: sessionId={}, messageId={}", 
                        sessionState.sessionId, sessionState.messageId, e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe();
    }
    
    /**
     * 处理AI完成事件
     */
    private Flux<VoiceStreamEvent> handleAICompletion(AIService.AIStreamEvent aiEvent, SessionState sessionState) {
        sessionState.metrics.mark("llm_completed");
        
        // 获取SUB标签内容，如果为空则使用完整响应作为备用
        String subtitleContent = sessionState.getSubtitleContent().toString().trim();
        String responseToSave = StringUtils.hasText(subtitleContent) ? subtitleContent : aiEvent.getProcessedResponse().aiResponse();
        
        // 只保存AI回复到历史记录，用户转写已在ASR事件中处理
        return persistAIResponse(sessionState, responseToSave)
                .then(Mono.just(VoiceStreamEvent.builder()
                        .type(VoiceStreamEvent.Type.ROUND_COMPLETED)
                        .sessionId(sessionState.sessionId)
                        .messageId(sessionState.messageId)
                        .timestamp(Instant.now().toEpochMilli())
                        .payload(Map.of("text", responseToSave))
                        .build()))
                .flux();
    }
    
    /**
     * 保存完整对话轮次到历史记录
     */
    private Mono<Void> persistAIResponse(SessionState sessionState, String aiResponse) {
        return Mono.fromRunnable(() -> {
            sessionState.metrics.mark("history_start");
            
            // 检查是否应该持久化
            if (!sessionState.shouldPersist) {
                log.info("由于处理错误，跳过对话历史保存: sessionId={}, messageId={}", 
                        sessionState.sessionId, sessionState.messageId);
                sessionState.metrics.mark("history_skipped");
                return;
            }
            
            try {
                // 将AI回复存储到会话状态
                sessionState.setAssistantMessage(aiResponse);
                
                // 如果有用户消息和AI回复，保存完整轮次
                if (StringUtils.hasText(sessionState.getUserMessage()) && StringUtils.hasText(aiResponse)) {
                    // 获取角色卡的开场白
                    String greetingMessage = sessionState.sessionInfo.getCharacterCard().getGreetingMessage();
                    
                    // 序列化图像附件信息
                    String attachmentsJson = null;
                    log.info("历史保存时检查图片附件: sessionId={}, messageId={}, imageCount={}", 
                            sessionState.sessionId, sessionState.messageId, sessionState.getGeneratedImages().size());
                    
                    if (!sessionState.getGeneratedImages().isEmpty()) {
                        try {
                            Map<String, Object> attachmentsData = Map.of("images", sessionState.getGeneratedImages());
                            attachmentsJson = objectMapper.writeValueAsString(attachmentsData);
                            log.info("图像附件序列化成功: sessionId={}, messageId={}, attachmentsJson={}", 
                                    sessionState.sessionId, sessionState.messageId, attachmentsJson);
                        } catch (JsonProcessingException e) {
                            log.error("序列化图像附件信息失败: sessionId={}, messageId={}", 
                                    sessionState.sessionId, sessionState.messageId, e);
                        }
                    } else {
                        log.info("历史保存时没有图片附件: sessionId={}, messageId={}", 
                                sessionState.sessionId, sessionState.messageId);
                    }
                    
                    // 保存到数据库 (PostgreSQL)
                    voiceChatService.saveCompleteRound(
                            sessionState.sessionInfo.getHistoryId(),
                            UUID.fromString(sessionState.sessionId),
                            sessionState.sessionInfo.getUser().getId(),
                            sessionState.sessionInfo.getCharacterCardId(),
                            sessionState.getUserMessage(),
                            aiResponse,
                            greetingMessage,
                            attachmentsJson
                    );
                    
                    // 同时保存到Redis缓存供AI上下文使用
                    chatMemoryService.addUserMessage(sessionState.sessionId, sessionState.getUserMessage());
                    chatMemoryService.addAssistantMessage(sessionState.sessionId, aiResponse);
                    
                    sessionState.metrics.mark("history_done");
                    log.info("完整对话轮次保存成功(DB+Redis): sessionId={}, messageId={}, userMsg={}, assistantMsg={}", 
                            sessionState.sessionId, sessionState.messageId, 
                            sessionState.getUserMessage().length(), aiResponse.length());
                } else {
                    log.warn("对话轮次不完整，跳过保存: sessionId={}, messageId={}, hasUserMsg={}, hasAssistantMsg={}", 
                            sessionState.sessionId, sessionState.messageId,
                            StringUtils.hasText(sessionState.getUserMessage()),
                            StringUtils.hasText(aiResponse));
                }
                
            } catch (Exception e) {
                log.error("保存对话历史失败: sessionId={}, messageId={}", sessionState.sessionId, sessionState.messageId, e);
                // 数据库保存失败也标记为不应持久化，避免部分数据不一致
                sessionState.shouldPersist = false;
                sessionState.hasProcessingErrors = true;
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    
    /**
     * 发送处理完成事件
     */
    private Flux<VoiceStreamEvent> sendProcessingCompleted(SessionState sessionState) {
        return Flux.defer(() -> {
            sessionState.metrics.mark("flow_completed");
            String report = sessionState.metrics.buildReport();
            if (StringUtils.hasText(report)) {
                log.info(report);
            }
            
            return Flux.just(VoiceStreamEvent.builder()
                    .type(VoiceStreamEvent.Type.PROCESSING_COMPLETED)
                    .sessionId(sessionState.sessionId)
                    .messageId(sessionState.messageId)
                    .timestamp(Instant.now().toEpochMilli())
                    .build());
        });
    }
    
    /**
     * 获取音色ID
     */
    private String getVoiceId(VoiceChatService.SessionInfo sessionInfo) {
        String voiceIdRaw = sessionInfo.getCharacterCard().getTtsVoiceId();
        return StringUtils.hasText(voiceIdRaw) ? voiceIdRaw : "default";
    }

    /**
     * 语音消息元数据记录
     */
    public record VoiceMessageWithMetadata(VoiceMessage voiceMessage, String messageId) {}
    
    /**
     * 会话状态
     */
    @lombok.Data
    @lombok.Builder
    public static class SessionState {
        private String sessionId;
        private String messageId;
        private VoiceChatService.SessionInfo sessionInfo;
        private ConversationMetrics metrics;
        @lombok.Builder.Default
        private StringBuilder subtitleContent = new StringBuilder(); // 用于收集SUB标签内容
        @lombok.Builder.Default
        private boolean hasProcessingErrors = false; // 跟踪是否有处理错误
        @lombok.Builder.Default
        private boolean shouldPersist = true; // 标记是否应该持久化数据
        // 新增：收集本轮对话数据
        private String userMessage; // 用户消息（转写结果）
        private String assistantMessage; // AI回复
        @lombok.Builder.Default
        private List<ImageAttachment> generatedImages = new ArrayList<>(); // 生成的图片
    }
    
    /**
     * 图像附件记录
     */
    public record ImageAttachment(
            String uri,
            String description,
            boolean isSelf
    ) {}
}