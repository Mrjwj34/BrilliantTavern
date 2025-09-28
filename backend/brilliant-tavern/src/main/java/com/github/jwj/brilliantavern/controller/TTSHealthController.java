package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.service.tts.TTSWarmupService;
import com.github.jwj.brilliantavern.service.TTSManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * TTS服务健康检查和监控端点
 */
@Slf4j
@RestController
@RequestMapping("/tts")
@RequiredArgsConstructor
public class TTSHealthController {

    private final TTSWarmupService ttsWarmupService;
    private final TTSManagerService ttsManagerService;
    
    @Value("${app.tts.base-url}")
    private String ttsBaseUrl;
    
    @Value("${app.tts.warmup.voice-ids:1,2,3}")
    private List<String> defaultVoiceIds;

    /**
     * TTS服务健康检查
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> healthCheck() {
        long startTime = System.currentTimeMillis();
        
        return performHealthCheck()
            .map(isHealthy -> {
                long duration = System.currentTimeMillis() - startTime;
                
                Map<String, Object> health = Map.of(
                    "status", isHealthy ? "UP" : "DOWN",
                    "timestamp", Instant.now().toString(),
                    "service", "TTS",
                    "baseUrl", ttsBaseUrl,
                    "responseTimeMs", duration,
                    "warmedUp", ttsWarmupService.isWarmedUp(),
                    "details", Map.of(
                        "connectionPool", "tts-http-pool",
                        "warmupVoices", defaultVoiceIds
                    )
                );
                
                return isHealthy ? 
                    ResponseEntity.ok(health) : 
                    ResponseEntity.status(503).body(health);
            })
            .onErrorResume(error -> {
                long duration = System.currentTimeMillis() - startTime;
                log.error("TTS健康检查失败", error);
                
                Map<String, Object> health = Map.of(
                    "status", "DOWN",
                    "timestamp", Instant.now().toString(),
                    "service", "TTS",
                    "baseUrl", ttsBaseUrl,
                    "responseTimeMs", duration,
                    "warmedUp", ttsWarmupService.isWarmedUp(),
                    "error", error.getMessage()
                );
                
                return Mono.just(ResponseEntity.status(503).body(health));
            });
    }

    /**
     * 获取TTS服务状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = Map.of(
            "baseUrl", ttsBaseUrl,
            "warmedUp", ttsWarmupService.isWarmedUp(),
            "warmupVoices", defaultVoiceIds,
            "timestamp", Instant.now().toString()
        );
        
        return ResponseEntity.ok(status);
    }

    /**
     * 手动触发预热
     */
    @PostMapping("/warmup")
    public ResponseEntity<Map<String, Object>> triggerWarmup(@RequestParam(required = false) List<String> voiceIds) {
        log.info("手动触发TTS预热，音色: {}", voiceIds != null ? voiceIds : "默认");
        
        if (voiceIds != null && !voiceIds.isEmpty()) {
            ttsWarmupService.warmupVoices(voiceIds);
        } else {
            ttsWarmupService.manualWarmup();
        }
        
        Map<String, Object> response = Map.of(
            "message", "预热任务已启动",
            "voiceIds", voiceIds != null ? voiceIds : defaultVoiceIds,
            "timestamp", Instant.now().toString()
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * 测试指定音色
     */
    @PostMapping("/test")
    public Mono<ResponseEntity<Map<String, Object>>> testVoice(
            @RequestParam String voiceId,
            @RequestParam(defaultValue = "测试音色") String text) {
        
        log.info("测试TTS音色: {} - 文本: {}", voiceId, text);
        long startTime = System.currentTimeMillis();
        
        return ttsManagerService.generateSpeechWithVoice(text, voiceId)
            .timeout(Duration.ofSeconds(30))
            .map(response -> {
                long duration = System.currentTimeMillis() - startTime;
                
                Map<String, Object> result = Map.of(
                    "success", Boolean.TRUE.equals(response.getSuccess()),
                    "voiceId", voiceId,
                    "text", text,
                    "responseTimeMs", duration,
                    "audioDataSize", response.getAudioData() != null ? response.getAudioData().length : 0,
                    "fromCache", Boolean.TRUE.equals(response.getFromCache()),
                    "timestamp", Instant.now().toString(),
                    "error", response.getErrorMessage() != null ? response.getErrorMessage() : ""
                );
                
                return Boolean.TRUE.equals(response.getSuccess()) ? 
                    ResponseEntity.ok(result) : 
                    ResponseEntity.status(500).body(result);
            })
            .onErrorResume(error -> {
                long duration = System.currentTimeMillis() - startTime;
                log.error("测试TTS音色失败: {}", voiceId, error);
                
                Map<String, Object> result = Map.of(
                    "success", false,
                    "voiceId", voiceId,
                    "text", text,
                    "responseTimeMs", duration,
                    "error", error.getMessage(),
                    "timestamp", Instant.now().toString()
                );
                
                return Mono.just(ResponseEntity.status(500).body(result));
            });
    }

    /**
     * 执行简单的健康检查
     */
    private Mono<Boolean> performHealthCheck() {
        String healthText = "健康检查";
        String defaultVoiceId = defaultVoiceIds.isEmpty() ? "1" : defaultVoiceIds.get(0);
        
        return ttsManagerService.generateSpeechWithVoice(healthText, defaultVoiceId)
            .timeout(Duration.ofSeconds(10))
            .map(response -> Boolean.TRUE.equals(response.getSuccess()))
            .onErrorReturn(false);
    }
}