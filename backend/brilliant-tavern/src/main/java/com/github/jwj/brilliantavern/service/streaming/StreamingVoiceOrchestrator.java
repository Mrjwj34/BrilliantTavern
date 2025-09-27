package com.github.jwj.brilliantavern.service.streaming;

import com.github.jwj.brilliantavern.dto.VoiceMessage;
import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import com.github.jwj.brilliantavern.entity.ChatHistory;
import com.github.jwj.brilliantavern.service.AIService;
import com.github.jwj.brilliantavern.service.VoiceChatService;
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
import java.util.Map;
import java.util.UUID;
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
    private final StreamingContentParser contentParser;
    private final AsyncEventDispatcher eventDispatcher;
    private final RetryService retryService;
    
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
                sessionState.messageId
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
        
        // 解析标签并生成事件
        Flux<TagEvent> tagEvents = contentParser.parseStream(textStream, sessionState.sessionId, sessionState.messageId);
        
        // 分发标签事件到各个处理器，错误会被外层的重试机制处理
        Flux<VoiceStreamEvent> handlerEvents = tagEvents.flatMap(tagEvent -> 
                eventDispatcher.dispatchEvent(tagEvent, sessionState));
        
        // 处理AI完成事件
        Flux<VoiceStreamEvent> completionEvents = aiEvents
                .filter(event -> event.getType() == AIService.AIStreamEvent.Type.COMPLETED)
                .next()
                .flatMapMany(aiEvent -> handleAICompletion(aiEvent, sessionState));
        
        return Flux.merge(handlerEvents, completionEvents);
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
     * 保存AI回复到历史记录
     */
    private Mono<Void> persistAIResponse(SessionState sessionState, String aiResponse) {
        return Mono.fromRunnable(() -> {
            sessionState.metrics.mark("history_start");
            
            // 检查是否应该持久化
            if (!sessionState.shouldPersist) {
                log.info("由于处理错误，跳过AI回复历史保存: sessionId={}, messageId={}", 
                        sessionState.sessionId, sessionState.messageId);
                sessionState.metrics.mark("history_skipped");
                return;
            }
            
            try {
                if (StringUtils.hasText(aiResponse)) {
                    voiceChatService.saveChatHistory(
                            sessionState.sessionInfo.getHistoryId(),
                            UUID.fromString(sessionState.sessionId),
                            sessionState.sessionInfo.getUser().getId(),
                            sessionState.sessionInfo.getCharacterCardId(),
                            ChatHistory.Role.ASSISTANT,
                            aiResponse
                    );
                }
                
                sessionState.metrics.mark("history_done");
                log.debug("AI回复历史保存成功: sessionId={}, messageId={}", sessionState.sessionId, sessionState.messageId);
                
            } catch (Exception e) {
                log.error("保存AI回复历史失败: sessionId={}, messageId={}", sessionState.sessionId, sessionState.messageId, e);
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
    }
}