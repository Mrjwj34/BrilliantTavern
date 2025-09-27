package com.github.jwj.brilliantavern.service.streaming.handlers;

import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import com.github.jwj.brilliantavern.service.streaming.TagEvent;
import com.github.jwj.brilliantavern.service.streaming.StreamingVoiceOrchestrator;
import com.github.jwj.brilliantavern.service.VoiceChatService;
import com.github.jwj.brilliantavern.entity.ChatHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ASREventHandler implements EventHandler {

    private final Map<String, ASRContext> asrContexts = new java.util.concurrent.ConcurrentHashMap<>();
    private final VoiceChatService voiceChatService;

    @Override
    public boolean canHandle(TagEvent tagEvent) {
        return tagEvent.getTagType() == TagEvent.TagType.ASR;
    }

    @Override
    public Flux<VoiceStreamEvent> handleEvent(TagEvent tagEvent, StreamingVoiceOrchestrator.SessionState sessionState) {
        String contextKey = sessionState.getSessionId() + "_" + sessionState.getMessageId();

        return switch (tagEvent.getEventType()) {
            case TAG_OPENED -> handleASROpened(tagEvent, contextKey);
            case CONTENT_CHUNK -> handleASRContent(tagEvent, contextKey);
            case TAG_CLOSED -> handleASRClosed(tagEvent, contextKey, sessionState);
        };
    }

    private Flux<VoiceStreamEvent> handleASROpened(TagEvent tagEvent, String contextKey) {
        ASRContext context = new ASRContext();
        context.sessionId = tagEvent.getSessionId();
        context.messageId = tagEvent.getMessageId();
        asrContexts.put(contextKey, context);
        
        log.debug("ASR标签开始: sessionId={}, messageId={}", 
                tagEvent.getSessionId(), tagEvent.getMessageId());
        
        return Flux.empty();
    }

    private Flux<VoiceStreamEvent> handleASRContent(TagEvent tagEvent, String contextKey) {
        ASRContext context = asrContexts.get(contextKey);
        if (context != null) {
            context.contentBuffer.append(tagEvent.getContent());
            log.debug("ASR内容累积: sessionId={}, messageId={}, content={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId(), tagEvent.getContent());
        }
        
        return Flux.empty();
    }

    private Flux<VoiceStreamEvent> handleASRClosed(TagEvent tagEvent, String contextKey, StreamingVoiceOrchestrator.SessionState sessionState) {
        ASRContext context = asrContexts.remove(contextKey);
        if (context == null || context.contentBuffer.isEmpty()) {
            log.warn("ASR标签结束但没有内容: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.empty();
        }
        
        String transcription = context.contentBuffer.toString().trim();
        if (!StringUtils.hasText(transcription)) {
            log.warn("ASR转写内容为空: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.empty();
        }
        
        log.info("ASR转写完成: sessionId={}, messageId={}, transcription={}", 
                tagEvent.getSessionId(), tagEvent.getMessageId(), transcription);
        
        // 立即保存用户转写到历史记录
        saveUserTranscription(sessionState, transcription);
        
        return Flux.just(buildASRResultEvent(tagEvent, transcription));
    }

    private VoiceStreamEvent buildASRResultEvent(TagEvent tagEvent, String transcription) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", transcription);
        payload.put("confidence", 1.0);
        payload.put("language", "zh");
        
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.ASR_RESULT)
                .sessionId(tagEvent.getSessionId())
                .messageId(tagEvent.getMessageId())
                .timestamp(Instant.now().toEpochMilli())
                .payload(payload)
                .build();
    }

    private void saveUserTranscription(StreamingVoiceOrchestrator.SessionState sessionState, String transcription) {
        try {
            // 检查是否应该持久化
            if (!sessionState.isShouldPersist()) {
                log.info("由于处理错误，跳过用户转写历史保存: sessionId={}", sessionState.getSessionId());
                return;
            }
            
            if (StringUtils.hasText(transcription)) {
                voiceChatService.saveChatHistory(
                        sessionState.getSessionInfo().getHistoryId(),
                        UUID.fromString(sessionState.getSessionId()),
                        sessionState.getSessionInfo().getUser().getId(),
                        sessionState.getSessionInfo().getCharacterCardId(),
                        ChatHistory.Role.USER,
                        transcription
                );
                
                log.debug("用户转写历史保存成功: sessionId={}, transcription={}", sessionState.getSessionId(), transcription);
            }
        } catch (Exception e) {
            log.error("保存用户转写历史失败: sessionId={}", sessionState.getSessionId(), e);
            // 数据库保存失败标记为不应持久化
            sessionState.setShouldPersist(false);
            sessionState.setHasProcessingErrors(true);
        }
    }

    private static class ASRContext {
        String sessionId;
        String messageId;
        StringBuilder contentBuffer = new StringBuilder();
    }
}