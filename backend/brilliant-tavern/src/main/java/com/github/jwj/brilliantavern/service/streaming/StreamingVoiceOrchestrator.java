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
        ).doOnError(error -> {
            log.error("语音处理失败: sessionId={}, messageId={}", sessionIdStr, messageId, error);
            sessionStates.remove(sessionIdStr);
        }).doOnComplete(() -> {
            log.info("语音处理完成: sessionId={}, messageId={}", sessionIdStr, messageId);
            sessionStates.remove(sessionIdStr);
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
        
        // 分发标签事件到各个处理器
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
        
        // 保存对话历史
        return persistHistory(sessionState, aiEvent.getProcessedResponse())
                .then(Mono.just(VoiceStreamEvent.builder()
                        .type(VoiceStreamEvent.Type.ROUND_COMPLETED)
                        .sessionId(sessionState.sessionId)
                        .messageId(sessionState.messageId)
                        .timestamp(Instant.now().toEpochMilli())
                        .payload(Map.of("text", aiEvent.getProcessedResponse().aiResponse()))
                        .build()))
                .flux();
    }
    
    /**
     * 保存对话历史
     */
    private Mono<Void> persistHistory(SessionState sessionState, AIService.ProcessedAiResponse processed) {
        return Mono.fromRunnable(() -> {
            sessionState.metrics.mark("history_start");
            
            try {
                if (StringUtils.hasText(processed.userTranscription())) {
                    voiceChatService.saveChatHistory(
                            sessionState.sessionInfo.getHistoryId(),
                            UUID.fromString(sessionState.sessionId),
                            sessionState.sessionInfo.getUser().getId(),
                            sessionState.sessionInfo.getCharacterCardId(),
                            ChatHistory.Role.USER,
                            processed.userTranscription()
                    );
                }
                
                if (StringUtils.hasText(processed.aiResponse())) {
                    voiceChatService.saveChatHistory(
                            sessionState.sessionInfo.getHistoryId(),
                            UUID.fromString(sessionState.sessionId),
                            sessionState.sessionInfo.getUser().getId(),
                            sessionState.sessionInfo.getCharacterCardId(),
                            ChatHistory.Role.ASSISTANT,
                            processed.aiResponse()
                    );
                }
                
                sessionState.metrics.mark("history_done");
                log.debug("对话历史保存成功: sessionId={}, messageId={}", sessionState.sessionId, sessionState.messageId);
                
            } catch (Exception e) {
                log.error("保存对话历史失败: sessionId={}, messageId={}", sessionState.sessionId, sessionState.messageId, e);
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
    }
}