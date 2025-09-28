package com.github.jwj.brilliantavern.service.tts;

import com.github.jwj.brilliantavern.service.TTSManagerService;
import com.github.jwj.brilliantavern.service.tts.impl.FishSpeechTTSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TTS服务预热服务
 * 在应用启动后主动预热TTS服务，减少首次调用的冷启动延迟
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TTSWarmupService {

    private final FishSpeechTTSService fishSpeechTTSService;
    private final TTSManagerService ttsManagerService;
    
    @Value("${app.tts.warmup.enabled:true}")
    private boolean warmupEnabled;
    
    @Value("${app.tts.warmup.text:你好}")
    private String warmupText;
    
    @Value("${app.tts.warmup.voice-ids:1,2,3}")
    private List<String> warmupVoiceIds;
    
    @Value("${app.tts.warmup.timeout:10s}")
    private Duration warmupTimeout;
    
    @Value("${app.tts.warmup.delay:5s}")
    private Duration warmupDelay;
    
    private final AtomicBoolean warmedUp = new AtomicBoolean(false);

    /**
     * 应用启动完成后执行预热
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!warmupEnabled) {
            log.info("TTS服务预热已禁用");
            return;
        }
        
        log.info("应用启动完成，{}秒后开始TTS服务预热", warmupDelay.getSeconds());
        
        Mono.delay(warmupDelay)
            .then(Mono.fromRunnable(this::performWarmup))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                unused -> log.info("TTS预热任务已启动"),
                error -> log.error("启动TTS预热任务失败", error)
            );
    }

    /**
     * 执行TTS服务预热
     */
    @Async
    public void performWarmup() {
        if (warmedUp.get()) {
            log.debug("TTS服务已经预热过，跳过");
            return;
        }

        log.info("开始TTS服务预热 - 文本: '{}', 音色数量: {}", warmupText, warmupVoiceIds.size());
        long startTime = System.currentTimeMillis();

        // 完全异步化的预热流程
        performHealthCheckAsync()
            .flatMap(isHealthy -> {
                if (!isHealthy) {
                    log.warn("TTS服务健康检查失败，跳过预热");
                    return Mono.empty();
                }
                
                // 预热各个音色
                return warmupAllVoicesAsync();
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                unused -> {
                    warmedUp.set(true);
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("TTS服务预热完成，耗时: {}ms", duration);
                },
                error -> {
                    log.error("TTS服务预热失败", error);
                }
            );
    }


    /**
     * 异步执行TTS服务健康检查
     */
    private Mono<Boolean> performHealthCheckAsync() {
        log.debug("执行TTS服务健康检查");
        
        String healthCheckText = "测试";
        String defaultVoiceId = warmupVoiceIds.isEmpty() ? "1" : warmupVoiceIds.get(0);
        
        return ttsManagerService.generateSpeechWithVoice(healthCheckText, defaultVoiceId)
            .timeout(warmupTimeout)
            .map(response -> {
                if (Boolean.TRUE.equals(response.getSuccess())) {
                    log.debug("TTS服务健康检查成功");
                    return true;
                } else {
                    log.warn("TTS服务健康检查失败: {}", response.getErrorMessage());
                    return false;
                }
            })
            .onErrorReturn(false)
            .doOnError(error -> log.warn("TTS服务健康检查异常: {}", error.getMessage()));
    }

    /**
     * 异步预热所有音色
     */
    private Mono<Void> warmupAllVoicesAsync() {
        if (warmupVoiceIds.isEmpty()) {
            return Mono.empty();
        }
        
        // 并行预热所有音色
        return Flux.fromIterable(warmupVoiceIds)
            .flatMap(this::warmupVoiceAsync, 3)  // 最多并发3个音色预热
            .then();
    }

    /**
     * 异步预热指定音色
     */
    private Mono<Void> warmupVoiceAsync(String voiceId) {
        log.debug("预热音色: {}", voiceId);
        
        return ttsManagerService.streamSpeechWithVoice(warmupText, voiceId, "warmup", "warmup")
            .timeout(warmupTimeout)
            .doOnNext(chunk -> log.debug("预热音色 {} 收到数据: {} bytes", voiceId, 
                chunk.getAudioData() != null ? chunk.getAudioData().length : 0))
            .doOnComplete(() -> log.debug("预热音色 {} 完成", voiceId))
            .doOnError(error -> log.warn("预热音色 {} 失败: {}", voiceId, error.getMessage()))
            .onErrorResume(error -> Mono.empty())  // 忽略单个音色预热失败
            .then();
    }

    /**
     * 手动触发预热（用于测试或需要重新预热的场景）
     */
    public void manualWarmup() {
        log.info("手动触发TTS服务预热");
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
     * 预热指定的音色列表（动态预热）
     */
    public void warmupVoices(List<String> voiceIds) {
        if (voiceIds == null || voiceIds.isEmpty()) {
            return;
        }
        
        log.info("动态预热音色: {}", voiceIds);
        
        Flux.fromIterable(voiceIds)
            .flatMap(this::warmupVoiceAsync, 3)  // 最多并发3个音色预热
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                unused -> {}, // 每个音色完成时不需要特别处理
                error -> log.error("动态预热任务失败", error),
                () -> log.debug("动态预热任务完成")
            );
    }
}