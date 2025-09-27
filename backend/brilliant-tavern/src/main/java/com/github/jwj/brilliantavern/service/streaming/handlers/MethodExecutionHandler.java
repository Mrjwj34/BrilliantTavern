package com.github.jwj.brilliantavern.service.streaming.handlers;

import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import com.github.jwj.brilliantavern.service.streaming.StreamingVoiceOrchestrator;
import com.github.jwj.brilliantavern.service.streaming.TagEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class MethodExecutionHandler implements EventHandler {

    private static final Pattern METHOD_PATTERN = Pattern.compile("(\\w+)\\(([^)]*)\\)");
    private final Map<String, MethodContext> methodContexts = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public boolean canHandle(TagEvent tagEvent) {
        return tagEvent.getTagType() == TagEvent.TagType.DO;
    }

    @Override
    public Flux<VoiceStreamEvent> handleEvent(TagEvent tagEvent, StreamingVoiceOrchestrator.SessionState sessionState) {
        String contextKey = sessionState.getSessionId() + "_" + sessionState.getMessageId();
        
        switch (tagEvent.getEventType()) {
            case TAG_OPENED:
                return handleMethodOpened(tagEvent, contextKey);
            case CONTENT_CHUNK:
                return handleMethodContent(tagEvent, contextKey);
            case TAG_CLOSED:
                return handleMethodClosed(tagEvent, contextKey);
            default:
                return Flux.empty();
        }
    }

    private Flux<VoiceStreamEvent> handleMethodOpened(TagEvent tagEvent, String contextKey) {
        MethodContext context = new MethodContext();
        context.sessionId = tagEvent.getSessionId();
        context.messageId = tagEvent.getMessageId();
        methodContexts.put(contextKey, context);
        
        log.debug("方法执行标签开始: sessionId={}, messageId={}", 
                tagEvent.getSessionId(), tagEvent.getMessageId());
        
        return Flux.empty();
    }

    private Flux<VoiceStreamEvent> handleMethodContent(TagEvent tagEvent, String contextKey) {
        MethodContext context = methodContexts.get(contextKey);
        if (context != null) {
            context.contentBuffer.append(tagEvent.getContent());
            log.debug("方法内容累积: sessionId={}, messageId={}, content={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId(), tagEvent.getContent());
        }
        
        return Flux.empty();
    }

    private Flux<VoiceStreamEvent> handleMethodClosed(TagEvent tagEvent, String contextKey) {
        MethodContext context = methodContexts.remove(contextKey);
        if (context == null || context.contentBuffer.length() == 0) {
            log.warn("方法标签结束但没有内容: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.empty();
        }
        
        String methodCall = context.contentBuffer.toString().trim();
        if (!StringUtils.hasText(methodCall)) {
            log.warn("方法调用内容为空: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.empty();
        }
        
        log.info("解析方法调用: sessionId={}, messageId={}, methodCall={}", 
                tagEvent.getSessionId(), tagEvent.getMessageId(), methodCall);
        
        MethodCall parsedMethod = parseMethodCall(methodCall);
        if (parsedMethod == null) {
            log.warn("方法调用格式不正确: sessionId={}, messageId={}, methodCall={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId(), methodCall);
            return Flux.just(buildMethodErrorEvent(tagEvent, "方法调用格式不正确: " + methodCall));
        }
        
        return executeMethod(tagEvent, parsedMethod);
    }

    private MethodCall parseMethodCall(String methodCall) {
        Matcher matcher = METHOD_PATTERN.matcher(methodCall);
        if (!matcher.find()) {
            return null;
        }
        
        String methodName = matcher.group(1);
        String paramsStr = matcher.group(2);
        
        String[] params = new String[0];
        if (StringUtils.hasText(paramsStr)) {
            params = paramsStr.split(",");
            for (int i = 0; i < params.length; i++) {
                params[i] = params[i].trim().replaceAll("^[\"']", "").replaceAll("[\"']$", "");
            }
        }
        
        return new MethodCall(methodName, params);
    }

    private Flux<VoiceStreamEvent> executeMethod(TagEvent tagEvent, MethodCall methodCall) {
        log.info("执行方法（占位符）: methodName={}, params={}", 
                methodCall.methodName, String.join(", ", methodCall.params));
        
        Map<String, Object> result = new HashMap<>();
        result.put("methodName", methodCall.methodName);
        result.put("params", methodCall.params);
        result.put("status", "placeholder");
        result.put("message", "方法调用已记录，等待后续实现");
        
        return Flux.just(buildMethodResultEvent(tagEvent, result));
    }

    private VoiceStreamEvent buildMethodResultEvent(TagEvent tagEvent, Map<String, Object> result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "method_executed");
        payload.put("result", result);
        
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.METHOD_EXECUTION)
                .sessionId(tagEvent.getSessionId())
                .messageId(tagEvent.getMessageId())
                .timestamp(Instant.now().toEpochMilli())
                .payload(payload)
                .build();
    }

    private VoiceStreamEvent buildMethodErrorEvent(TagEvent tagEvent, String errorMessage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "method_error");
        payload.put("error", errorMessage);
        
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.METHOD_EXECUTION)
                .sessionId(tagEvent.getSessionId())
                .messageId(tagEvent.getMessageId())
                .timestamp(Instant.now().toEpochMilli())
                .payload(payload)
                .build();
    }

    private record MethodCall(String methodName, String[] params) {}

    private static class MethodContext {
        String sessionId;
        String messageId;
        StringBuilder contentBuffer = new StringBuilder();
    }
}