package com.github.jwj.brilliantavern.service.streaming.handlers;

import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import com.github.jwj.brilliantavern.service.CharacterMemoryService;
import com.github.jwj.brilliantavern.service.ImageGenerationService;
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
    
    // 添加去重机制：跟踪已执行的方法调用
    private final Map<String, java.util.Set<String>> executedMethods = new java.util.concurrent.ConcurrentHashMap<>();
    
    private final CharacterMemoryService characterMemoryService;
    private final ImageGenerationService imageGenerationService;

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
        
        // 生成去重key
        String sessionMessageKey = tagEvent.getSessionId() + "_" + tagEvent.getMessageId();
        
        // 按分号分割多个方法调用
        String[] methodCallArray = methodCalls.split(";");
        
        return Flux.fromArray(methodCallArray)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .flatMap(methodCall -> {
                // 生成方法调用的唯一标识
                String methodKey = methodCall.trim();
                
                // 检查是否已经执行过这个方法
                java.util.Set<String> sessionExecutedMethods = executedMethods.computeIfAbsent(sessionMessageKey, k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
                
                if (sessionExecutedMethods.contains(methodKey)) {
                    log.warn("方法调用重复，跳过执行: sessionId={}, messageId={}, methodCall={}", 
                            tagEvent.getSessionId(), tagEvent.getMessageId(), methodCall);
                    return Flux.empty(); // 跳过重复的方法调用
                }
                
                // 标记为已执行
                sessionExecutedMethods.add(methodKey);
                
                MethodCall parsedMethod = parseMethodCall(methodCall);
                if (parsedMethod == null) {
                    log.warn("方法调用格式不正确: sessionId={}, messageId={}, methodCall={}", 
                            tagEvent.getSessionId(), tagEvent.getMessageId(), methodCall);
                    return Flux.just(buildMethodErrorEvent(tagEvent, "方法调用格式不正确: " + methodCall));
                }
                
                return executeMethod(tagEvent, parsedMethod, context.sessionState)
                    .doFinally(signalType -> {
                        // 方法执行完成后，可以选择清理去重记录（用于允许后续重新执行）
                        // 这里暂时保留，避免同一消息内的重复执行
                        log.debug("方法执行完成: methodCall={}, signalType={}", methodCall, signalType);
                    });
            })
            .doFinally(signalType -> {
                // 当整个方法调用处理完成后，延迟清理去重记录以释放内存
                // 延迟清理是为了确保同一个消息内的重复调用能被正确过滤
                Mono.delay(java.time.Duration.ofSeconds(30))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(delay -> {
                        executedMethods.remove(sessionMessageKey);
                        log.debug("延迟清理方法执行去重记录: sessionMessageKey={}", sessionMessageKey);
                    })
                    .subscribe();
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
            case "imagen", "生成图片", "生图" -> executeImagenMethod(tagEvent, methodCall.params, sessionState);
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
    
    /**
     * 执行图像生成方法
     */
    private Flux<VoiceStreamEvent> executeImagenMethod(TagEvent tagEvent, String[] params, 
                                                      StreamingVoiceOrchestrator.SessionState sessionState) {
        if (params.length < 1) {
            log.warn("imagen方法参数不足: sessionId={}, messageId={}, params={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId(), String.join(", ", params));
            return Flux.just(buildMethodErrorEvent(tagEvent, "imagen方法至少需要1个参数: description(string)，可选参数: isSelf(boolean)"));
        }

        try {
            // 解析参数 - 支持key=value格式
            boolean isSelf = false; // 默认值
            String description = null;
            
            for (String param : params) {
                String trimmedParam = param.trim();
                if (trimmedParam.startsWith("isSelf=")) {
                    String value = trimmedParam.substring("isSelf=".length()).trim();
                    isSelf = Boolean.parseBoolean(value);
                    log.debug("解析isSelf参数: {} -> {}", value, isSelf);
                } else if (trimmedParam.startsWith("description=")) {
                    description = trimmedParam.substring("description=".length()).trim();
                    // 移除可能的引号
                    if (description.startsWith("\"") && description.endsWith("\"")) {
                        description = description.substring(1, description.length() - 1);
                    }
                    log.debug("解析description参数: {}", description);
                } else {
                    // 如果没有key=value格式，假设是description
                    if (description == null) {
                        description = trimmedParam;
                        if (description.startsWith("\"") && description.endsWith("\"")) {
                            description = description.substring(1, description.length() - 1);
                        }
                        log.debug("默认解析为description参数: {}", description);
                    }
                }
            }
            
            if (description == null || description.trim().isEmpty()) {
                return Flux.just(buildMethodErrorEvent(tagEvent, "imagen方法缺少description参数"));
            }
            
            log.info("imagen方法参数解析完成: isSelf={}, description={}", isSelf, description);
            
            // 发送图像生成开始事件
            VoiceStreamEvent startEvent = buildImageGenerationStartEvent(tagEvent, isSelf, description);
            
            // 异步执行图像生成
            return Flux.concat(
                Flux.just(startEvent),
                imageGenerationService.generateImageAsync(
                    sessionState.getSessionInfo().getUserId(),
                    sessionState.getSessionInfo().getCharacterCard(),
                    isSelf,
                    description,
                    tagEvent.getSessionId(),
                    tagEvent.getMessageId()
                ).map(result -> buildImageGenerationResultEvent(tagEvent, result))
                .onErrorReturn(buildImageGenerationErrorEvent(tagEvent, "图像生成失败"))
            );
            
        } catch (Exception e) {
            log.error("imagen方法参数解析失败: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId(), e);
            return Flux.just(buildMethodErrorEvent(tagEvent, "imagen方法参数格式错误: " + e.getMessage()));
        }
    }
    
    /**
     * 构建图像生成开始事件
     */
    private VoiceStreamEvent buildImageGenerationStartEvent(TagEvent tagEvent, boolean isSelf, String description) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "image_generation_started");
        payload.put("isSelf", isSelf);
        payload.put("description", description);
        
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.METHOD_EXECUTION)
                .sessionId(tagEvent.getSessionId())
                .messageId(tagEvent.getMessageId())
                .timestamp(Instant.now().toEpochMilli())
                .payload(payload)
                .build();
    }
    
    /**
     * 构建图像生成结果事件
     */
    private VoiceStreamEvent buildImageGenerationResultEvent(TagEvent tagEvent, ImageGenerationService.ImageGenerationResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "image_generation_completed");
        payload.put("result", Map.of(
                "imageUri", result.imageUri(),
                "description", result.description(),
                "isSelf", result.isSelf(),
                "status", "success"
        ));
        
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.METHOD_EXECUTION)
                .sessionId(tagEvent.getSessionId())
                .messageId(tagEvent.getMessageId())
                .timestamp(Instant.now().toEpochMilli())
                .payload(payload)
                .build();
    }
    
    /**
     * 构建图像生成错误事件
     */
    private VoiceStreamEvent buildImageGenerationErrorEvent(TagEvent tagEvent, String errorMessage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "image_generation_failed");
        payload.put("error", errorMessage);
        
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.METHOD_EXECUTION)
                .sessionId(tagEvent.getSessionId())
                .messageId(tagEvent.getMessageId())
                .timestamp(Instant.now().toEpochMilli())
                .payload(payload)
                .build();
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