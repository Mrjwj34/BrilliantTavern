package com.github.jwj.brilliantavern.service.streaming;

import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;

/**
 * 重试服务 - 处理指数退避重试逻辑
 * 每个异常独立重试3次，不累积计数
 */
@Slf4j
@Service
public class RetryService {
    
    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_DELAY = Duration.ofMillis(500);
    private static final double BACKOFF_MULTIPLIER = 2.0;
    
    /**
     * 对 Flux 执行重试，并发送进度事件
     */
    public <T> Flux<T> retryWithProgress(Flux<T> source, 
                                        String sessionId, 
                                        String messageId,
                                        String operationName,
                                        Function<RetryContext, Flux<VoiceStreamEvent>> progressEventGenerator) {
        return source.retryWhen(
                Retry.backoff(MAX_RETRIES, INITIAL_DELAY)
                        .multiplier(BACKOFF_MULTIPLIER)
                        .doBeforeRetry(retrySignal -> {
                            long attempt = retrySignal.totalRetries() + 1;
                            Duration delay = calculateDelay(attempt);
                            
                            log.warn("重试操作 '{}': 第 {}/{} 次, 延迟 {}ms, 错误: {}", 
                                    operationName, attempt, MAX_RETRIES, delay.toMillis(), 
                                    retrySignal.failure().getMessage());
                            
                            RetryContext context = new RetryContext(
                                    sessionId, messageId, operationName,
                                    (int) attempt, MAX_RETRIES, delay,
                                    retrySignal.failure()
                            );
                            
                            // 发送重试进度事件
                            progressEventGenerator.apply(context).subscribe();
                        })
        );
    }
    
    /**
     * 对 Mono 执行重试，并发送进度事件
     */
    public <T> Mono<T> retryWithProgress(Mono<T> source, 
                                        String sessionId, 
                                        String messageId,
                                        String operationName,
                                        Function<RetryContext, Flux<VoiceStreamEvent>> progressEventGenerator) {
        return source.retryWhen(
                Retry.backoff(MAX_RETRIES, INITIAL_DELAY)
                        .multiplier(BACKOFF_MULTIPLIER)
                        .doBeforeRetry(retrySignal -> {
                            long attempt = retrySignal.totalRetries() + 1;
                            Duration delay = calculateDelay(attempt);
                            
                            log.warn("重试操作 '{}': 第 {}/{} 次, 延迟 {}ms, 错误: {}", 
                                    operationName, attempt, MAX_RETRIES, delay.toMillis(), 
                                    retrySignal.failure().getMessage());
                            
                            RetryContext context = new RetryContext(
                                    sessionId, messageId, operationName,
                                    (int) attempt, MAX_RETRIES, delay,
                                    retrySignal.failure()
                            );
                            
                            // 发送重试进度事件
                            progressEventGenerator.apply(context).subscribe();
                        })
        );
    }
    
    /**
     * 计算指数退避延迟
     */
    private Duration calculateDelay(long attempt) {
        long delayMs = (long) (INITIAL_DELAY.toMillis() * Math.pow(BACKOFF_MULTIPLIER, attempt - 1));
        return Duration.ofMillis(delayMs);
    }
    
    /**
     * 创建重试开始事件
     */
    public VoiceStreamEvent createRetryStartedEvent(String sessionId, String messageId, String operationName, Throwable error) {
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.RETRY_STARTED)
                .sessionId(sessionId)
                .messageId(messageId)
                .timestamp(Instant.now().toEpochMilli())
                .payload(Map.of(
                        "operation", operationName,
                        "maxRetries", MAX_RETRIES,
                        "error", error.getMessage()
                ))
                .build();
    }
    
    /**
     * 创建重试进度事件
     */
    public VoiceStreamEvent createRetryProgressEvent(RetryContext context) {
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.RETRY_PROGRESS)
                .sessionId(context.sessionId())
                .messageId(context.messageId())
                .timestamp(Instant.now().toEpochMilli())
                .payload(Map.of(
                        "operation", context.operationName(),
                        "attempt", context.attempt(),
                        "maxRetries", context.maxRetries(),
                        "delayMs", context.delay().toMillis(),
                        "error", context.error().getMessage()
                ))
                .build();
    }
    
    /**
     * 创建重试失败事件
     */
    public VoiceStreamEvent createRetryFailedEvent(String sessionId, String messageId, String operationName, Throwable error) {
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.RETRY_FAILED)
                .sessionId(sessionId)
                .messageId(messageId)
                .timestamp(Instant.now().toEpochMilli())
                .payload(Map.of(
                        "operation", operationName,
                        "finalError", error.getMessage(),
                        "retriesExhausted", true
                ))
                .build();
    }
    
    /**
     * 创建对话轮次丢弃事件
     */
    public VoiceStreamEvent createRoundDiscardedEvent(String sessionId, String messageId, String reason) {
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.ROUND_DISCARDED)
                .sessionId(sessionId)
                .messageId(messageId)
                .timestamp(Instant.now().toEpochMilli())
                .payload(Map.of(
                        "reason", reason,
                        "discardedAt", Instant.now().toString()
                ))
                .build();
    }
    
    /**
     * 重试上下文记录
     */
    public record RetryContext(
            String sessionId,
            String messageId,
            String operationName,
            int attempt,
            int maxRetries,
            Duration delay,
            Throwable error
    ) {}
}