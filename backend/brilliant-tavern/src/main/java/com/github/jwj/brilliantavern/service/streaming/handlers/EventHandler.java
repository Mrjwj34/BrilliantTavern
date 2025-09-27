package com.github.jwj.brilliantavern.service.streaming.handlers;

import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import com.github.jwj.brilliantavern.service.streaming.StreamingVoiceOrchestrator;
import com.github.jwj.brilliantavern.service.streaming.TagEvent;
import reactor.core.publisher.Flux;

/**
 * 事件处理器接口
 * 定义标签事件处理的通用规范
 */
public interface EventHandler {
    
    /**
     * 判断是否可以处理指定的标签事件
     */
    boolean canHandle(TagEvent tagEvent);
    
    /**
     * 处理标签事件，返回相应的流式事件
     */
    Flux<VoiceStreamEvent> handleEvent(TagEvent tagEvent, StreamingVoiceOrchestrator.SessionState sessionState);
}