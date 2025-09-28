package com.github.jwj.brilliantavern.service.streaming.handlers;

import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import com.github.jwj.brilliantavern.service.CharacterMemoryService;
import com.github.jwj.brilliantavern.service.streaming.StreamingVoiceOrchestrator;
import com.github.jwj.brilliantavern.service.streaming.TagEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class MethodExecutionHandler implements EventHandler {

    private static final Pattern METHOD_PATTERN = Pattern.compile("(\\w+)\\(([^)]*)\\)");
    private final Map<String, MethodContext> methodContexts = new java.util.concurrent.ConcurrentHashMap<>();
    
    private final CharacterMemoryService characterMemoryService;

    @Override
    public boolean canHandle(TagEvent tagEvent) {
        return tagEvent.getTagType() == TagEvent.TagType.DO;
    }

    @Override
    public Flux<VoiceStreamEvent> handleEvent(TagEvent tagEvent, StreamingVoiceOrchestrator.SessionState sessionState) {
        String contextKey = sessionState.getSessionId() + "_" + sessionState.getMessageId();
        
        switch (tagEvent.getEventType()) {
            case TAG_OPENED:
                return handleMethodOpened(tagEvent, contextKey, sessionState);
            case CONTENT_CHUNK:
                return handleMethodContent(tagEvent, contextKey);
            case TAG_CLOSED:
                return handleMethodClosed(tagEvent, contextKey);
            default:
                return Flux.empty();
        }
    }

    private Flux<VoiceStreamEvent> handleMethodOpened(TagEvent tagEvent, String contextKey, 
                                                      StreamingVoiceOrchestrator.SessionState sessionState) {
        MethodContext context = new MethodContext();
        context.sessionId = tagEvent.getSessionId();
        context.messageId = tagEvent.getMessageId();
        context.sessionState = sessionState;
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
        if (context == null) {
            log.warn("方法标签结束但没有上下文: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.empty();
        }
        
        String methodCalls = context.contentBuffer.toString().trim();
        
        if (!StringUtils.hasText(methodCalls)) {
            log.debug("方法标签为空，无需执行方法: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.empty();
        }
        
        log.info("解析方法调用: sessionId={}, messageId={}, methodCalls={}", 
                tagEvent.getSessionId(), tagEvent.getMessageId(), methodCalls);
        
        // 按分号分割多个方法调用
        String[] methodCallArray = methodCalls.split(";");
        
        return Flux.fromArray(methodCallArray)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .flatMap(methodCall -> {
                MethodCall parsedMethod = parseMethodCall(methodCall);
                if (parsedMethod == null) {
                    log.warn("方法调用格式不正确: sessionId={}, messageId={}, methodCall={}", 
                            tagEvent.getSessionId(), tagEvent.getMessageId(), methodCall);
                    return Flux.just(buildMethodErrorEvent(tagEvent, "方法调用格式不正确: " + methodCall));
                }
                return executeMethod(tagEvent, parsedMethod, context.sessionState);
            });
    }

    private MethodCall parseMethodCall(String methodCall) {
        Matcher matcher = METHOD_PATTERN.matcher(methodCall);
        if (!matcher.find()) {
            return null;
        }
        
        String methodName = matcher.group(1);
        String paramsStr = matcher.group(2);
        
        String[] params = parseParameters(paramsStr);
        return new MethodCall(methodName, params);
    }

    /**
     * 解析方法参数，支持引号内的参数
     */
    private String[] parseParameters(String paramsStr) {
        if (!StringUtils.hasText(paramsStr)) {
            return new String[0];
        }

        java.util.List<String> params = new java.util.ArrayList<>();
        StringBuilder currentParam = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = '"';
        
        for (int i = 0; i < paramsStr.length(); i++) {
            char c = paramsStr.charAt(i);
            
            if (!inQuotes && (c == '"' || c == '\'')) {
                inQuotes = true;
                quoteChar = c;
                // 不包含引号本身
            } else if (inQuotes && c == quoteChar) {
                inQuotes = false;
                // 不包含引号本身
            } else if (!inQuotes && c == ',') {
                // 参数分隔符
                params.add(currentParam.toString().trim());
                currentParam = new StringBuilder();
            } else {
                currentParam.append(c);
            }
        }
        
        // 添加最后一个参数
        if (currentParam.length() > 0) {
            params.add(currentParam.toString().trim());
        }
        
        return params.toArray(new String[0]);
    }

    private Flux<VoiceStreamEvent> executeMethod(TagEvent tagEvent, MethodCall methodCall, 
                                                  StreamingVoiceOrchestrator.SessionState sessionState) {
        log.info("执行方法: methodName={}, params={}", 
                methodCall.methodName, String.join(", ", methodCall.params));
        
        String methodName = methodCall.methodName.toLowerCase();
        
        return switch (methodName) {
            case "remember", "记住" -> executeRememberMethod(tagEvent, methodCall.params, sessionState);
            // 可以在这里添加更多方法
            default -> {
                log.warn("未知的方法: {}", methodCall.methodName);
                Map<String, Object> result = new HashMap<>();
                result.put("methodName", methodCall.methodName);
                result.put("params", methodCall.params);
                result.put("status", "unknown_method");
                result.put("message", "未知的方法: " + methodCall.methodName);
                yield Flux.just(buildMethodResultEvent(tagEvent, result));
            }
        };
    }

    /**
     * 执行记忆方法
     */
    private Flux<VoiceStreamEvent> executeRememberMethod(TagEvent tagEvent, String[] params, 
                                                         StreamingVoiceOrchestrator.SessionState sessionState) {
        if (params.length == 0 || !StringUtils.hasText(params[0])) {
            log.warn("记忆方法缺少参数: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.just(buildMethodErrorEvent(tagEvent, "记忆方法缺少参数"));
        }

        String memoryContent = params[0];
        
        return Mono.fromCallable(() -> {
            try {
                // 从SessionState获取用户ID和角色卡信息
                UUID userId = sessionState.getSessionInfo().getUserId();
                UUID cardId = sessionState.getSessionInfo().getCharacterCard().getId();
                String characterName = sessionState.getSessionInfo().getCharacterCard().getName();
                
                // 调用记忆服务存储记忆
                characterMemoryService.storeMemory(userId, cardId, memoryContent);

                log.info("角色记忆存储成功: userId={}, cardId={}, content={}", 
                    userId, cardId, memoryContent);

                Map<String, Object> result = new HashMap<>();
                result.put("methodName", "remember");
                result.put("params", params);
                result.put("status", "success");
                result.put("message", String.format("%s记住了: %s", 
                    characterName,
                    memoryContent.length() > 20 ? memoryContent.substring(0, 20) + "..." : memoryContent));
                result.put("memoryContent", memoryContent);

                return buildMethodResultEvent(tagEvent, result);

            } catch (Exception e) {
                log.error("存储记忆失败: sessionState={}", sessionState.getSessionId(), e);
                return buildMethodErrorEvent(tagEvent, "存储记忆失败: " + e.getMessage());
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flux();
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
        StreamingVoiceOrchestrator.SessionState sessionState;
        StringBuilder contentBuffer = new StringBuilder();
    }
}