package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.service.genai.GenAIWarmupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * GenAI健康检查控制器
 * 提供GenAI服务的健康状态检查和预热管理功能
 */
@Slf4j
@RestController
@RequestMapping("/api/genai")
@RequiredArgsConstructor
@Tag(name = "GenAI健康检查", description = "GenAI服务健康状态和预热管理")
public class GenAIHealthController {

    private final GenAIWarmupService genAIWarmupService;

    /**
     * 获取GenAI服务健康状态
     */
    @GetMapping("/health")
    @Operation(summary = "检查GenAI服务健康状态", description = "返回GenAI服务的预热状态和相关信息")
    public ResponseEntity<Map<String, Object>> getHealth() {
        try {
            GenAIWarmupService.WarmupStatus status = genAIWarmupService.getWarmupStatus();
            
            Map<String, Object> healthInfo = Map.of(
                "status", status.isWarmedUp() ? "healthy" : "cold",
                "warmedUp", status.isWarmedUp(),
                "lastWarmupTime", status.lastWarmupTime(),
                "warmupEnabled", status.warmupEnabled(),
                "model", status.model(),
                "warmupText", status.warmupText(),
                "timestamp", LocalDateTime.now()
            );

            log.debug("GenAI健康检查: {}", healthInfo);
            return ResponseEntity.ok(healthInfo);

        } catch (Exception e) {
            log.error("GenAI健康检查失败", e);
            
            Map<String, Object> errorInfo = Map.of(
                "status", "error",
                "warmedUp", false,
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.status(500).body(errorInfo);
        }
    }

    /**
     * 手动触发GenAI服务预热
     */
    @PostMapping("/warmup")
    @Operation(summary = "手动预热GenAI服务", description = "立即执行GenAI服务预热操作")
    public ResponseEntity<Map<String, Object>> manualWarmup() {
        try {
            log.info("收到手动预热GenAI服务请求");
            
            // 异步执行预热
            genAIWarmupService.manualWarmup();
            
            Map<String, Object> response = Map.of(
                "message", "GenAI预热任务已启动",
                "timestamp", LocalDateTime.now(),
                "status", "started"
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("手动预热GenAI服务失败", e);
            
            Map<String, Object> errorResponse = Map.of(
                "message", "预热启动失败",
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now(),
                "status", "error"
            );
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}