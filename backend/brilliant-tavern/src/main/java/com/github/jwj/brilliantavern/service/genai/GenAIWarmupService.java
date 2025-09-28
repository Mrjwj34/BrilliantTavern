package com.github.jwj.brilliantavern.service.genai;

import com.github.jwj.brilliantavern.config.GenAIConfig;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * GenAI服务预热服务
 * 在应用启动后主动预热Vertex AI服务，减少首次调用的冷启动延迟
 * 使用极小的请求量进行预热，成本可控
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenAIWarmupService {

    private final Client genAIClient;
    private final GenAIConfig genAIConfig;
    
    @Value("${app.genai.warmup.enabled:true}")
    private boolean warmupEnabled;
    
    @Value("${app.genai.warmup.text:Hi}")
    private String warmupText;
    
    @Value("${app.genai.warmup.timeout:30s}")
    private Duration warmupTimeout;
    
    @Value("${app.genai.warmup.delay:10s}")
    private Duration warmupDelay;
    
    @Value("${app.genai.warmup.maintain-interval:3600000}") // 1小时，单位毫秒
    private long maintainIntervalMs;
    
    private final AtomicBoolean warmedUp = new AtomicBoolean(false);
    private final AtomicReference<LocalDateTime> lastWarmupTime = new AtomicReference<>();

    /**
     * 应用启动完成后执行预热
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!warmupEnabled) {
            log.info("GenAI服务预热已禁用");
            return;
        }
        
        log.info("应用启动完成，{}秒后开始GenAI服务预热", warmupDelay.getSeconds());
        
        Mono.delay(warmupDelay)
            .then(Mono.fromRunnable(this::performWarmup))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                unused -> log.info("GenAI预热任务已启动"),
                error -> log.error("启动GenAI预热任务失败", error)
            );
    }

    /**
     * 定时维持预热（每小时执行一次）
     */
    @Scheduled(fixedRateString = "${app.genai.warmup.maintain-interval:3600000}")
    public void maintainWarmup() {
        if (!warmupEnabled || !warmedUp.get()) {
            return;
        }
        
        log.debug("执行GenAI服务维持预热");
        performWarmup();
    }

    /**
     * 执行GenAI服务预热
     */
    @Async
    public void performWarmup() {
        try {
            log.debug("开始GenAI服务预热 - 文本: '{}'", warmupText);
            long startTime = System.currentTimeMillis();

            // 创建极小的预热请求
            Content warmupContent = Content.fromParts(Part.fromText(warmupText));
            List<Content> contents = List.of(warmupContent);

            // 优化的配置：最小化token消耗
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .temperature(0.1f)          // 降低随机性
                    .maxOutputTokens(10)        // 最小输出
                    .build();

            // 执行预热请求
            GenerateContentResponse response = genAIClient.models.generateContent(
                genAIConfig.getVertexAi().getModel(),
                contents,
                config
            );

            // 验证响应
            if (response != null && response.text() != null) {
                warmedUp.set(true);
                lastWarmupTime.set(LocalDateTime.now());
                long duration = System.currentTimeMillis() - startTime;
                log.info("GenAI服务预热成功，响应: '{}', 耗时: {}ms", 
                    response.text().trim(), duration);
            } else {
                log.warn("GenAI预热响应为空，可能预热失败");
            }

        } catch (Exception e) {
            log.error("GenAI服务预热失败", e);
            warmedUp.set(false);
        }
    }

    /**
     * 手动触发预热（用于测试或重新预热）
     */
    public void manualWarmup() {
        log.info("手动触发GenAI服务预热");
        warmedUp.set(false);
        performWarmup();
    }

    /**
     * 检查预热状态
     */
    public boolean isWarmedUp() {
        return warmedUp.get();
    }

    /**
     * 获取最后预热时间
     */
    public LocalDateTime getLastWarmupTime() {
        return lastWarmupTime.get();
    }

    /**
     * 获取预热状态信息
     */
    public WarmupStatus getWarmupStatus() {
        return new WarmupStatus(
            warmedUp.get(),
            lastWarmupTime.get(),
            warmupEnabled,
            warmupText,
            genAIConfig.getVertexAi().getModel()
        );
    }

    /**
     * 预热状态记录
     */
    public record WarmupStatus(
        boolean isWarmedUp,
        LocalDateTime lastWarmupTime,
        boolean warmupEnabled,
        String warmupText,
        String model
    ) {}
}