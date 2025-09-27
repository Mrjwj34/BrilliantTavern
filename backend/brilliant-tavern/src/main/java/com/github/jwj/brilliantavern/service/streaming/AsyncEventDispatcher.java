package com.github.jwj.brilliantavern.service.streaming;

import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import com.github.jwj.brilliantavern.service.streaming.handlers.EventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 异步事件分发器
 * 统一管理四个异步事件处理器，实现事件分发和调度
 */
@Slf4j
@Component
public class AsyncEventDispatcher {

    private final List<EventHandler> eventHandlers;
    
    // 为不同类型的事件使用独立线程池
    private final Executor ttsExecutor = Executors.newFixedThreadPool(2, 
            r -> new Thread(r, "TTS-Handler-"));
    private final Executor subtitleExecutor = Executors.newFixedThreadPool(4, 
            r -> new Thread(r, "Subtitle-Handler-"));
    private final Executor asrExecutor = Executors.newFixedThreadPool(2, 
            r -> new Thread(r, "ASR-Handler-"));
    private final Executor methodExecutor = Executors.newFixedThreadPool(1, 
            r -> new Thread(r, "Method-Handler-"));

    public AsyncEventDispatcher(List<EventHandler> eventHandlers) {
        this.eventHandlers = eventHandlers;
        log.info("异步事件分发器初始化完成，注册处理器数量: {}", eventHandlers.size());
    }

    /**
     * 分发标签事件到相应的处理器
     */
    public Flux<VoiceStreamEvent> dispatchEvent(TagEvent tagEvent, StreamingVoiceOrchestrator.SessionState sessionState) {
        // 找到能处理该事件的处理器
        EventHandler handler = findHandler(tagEvent);
        if (handler == null) {
            log.warn("未找到处理器: tagType={}, eventType={}, sessionId={}, messageId={}", 
                    tagEvent.getTagType(), tagEvent.getEventType(), 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.empty();
        }
        
        // 根据标签类型选择相应的线程池
        Executor executor = getExecutor(tagEvent.getTagType());
        
        log.debug("分发事件: tagType={}, eventType={}, sessionId={}, messageId={}, handler={}", 
                tagEvent.getTagType(), tagEvent.getEventType(), 
                tagEvent.getSessionId(), tagEvent.getMessageId(), 
                handler.getClass().getSimpleName());
        
        // 在指定线程池中异步处理事件
        return handler.handleEvent(tagEvent, sessionState)
                .subscribeOn(Schedulers.fromExecutor(executor))
                .doOnError(error -> log.error("事件处理失败: tagType={}, eventType={}, sessionId={}, messageId={}", 
                        tagEvent.getTagType(), tagEvent.getEventType(), 
                        tagEvent.getSessionId(), tagEvent.getMessageId(), error))
                .onErrorResume(error -> {
                    // 处理失败时返回错误事件
                    return Flux.just(buildErrorEvent(tagEvent, error.getMessage()));
                });
    }

    /**
     * 查找能处理指定事件的处理器
     */
    private EventHandler findHandler(TagEvent tagEvent) {
        return eventHandlers.stream()
                .filter(handler -> handler.canHandle(tagEvent))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据标签类型获取相应的线程池
     */
    private Executor getExecutor(TagEvent.TagType tagType) {
        return switch (tagType) {
            case TSS -> ttsExecutor;
            case SUB -> subtitleExecutor;
            case ASR -> asrExecutor;
            case DO -> methodExecutor;
        };
    }

    /**
     * 构建错误事件
     */
    private VoiceStreamEvent buildErrorEvent(TagEvent tagEvent, String errorMessage) {
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.ERROR)
                .sessionId(tagEvent.getSessionId())
                .messageId(tagEvent.getMessageId())
                .timestamp(java.time.Instant.now().toEpochMilli())
                .payload(java.util.Map.of(
                        "error", errorMessage,
                        "tagType", tagEvent.getTagType().name(),
                        "eventType", tagEvent.getEventType().name()
                ))
                .build();
    }

    /**
     * 获取处理器统计信息
     */
    public String getHandlerStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("事件处理器统计:\n");
        for (EventHandler handler : eventHandlers) {
            stats.append("- ").append(handler.getClass().getSimpleName()).append("\n");
        }
        return stats.toString();
    }
}