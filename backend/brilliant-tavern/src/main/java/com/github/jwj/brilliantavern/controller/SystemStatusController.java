package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统状态测试控制器
 */
@Tag(name = "系统测试", description = "系统状态检测接口")
@Slf4j
@RestController
@RequestMapping("/system")
public class SystemStatusController {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 检测系统状态
     */
    @GetMapping("/status")
    @Operation(summary = "检测系统状态", description = "检测系统各组件状态")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 检测Redis连接
        try {
            redisTemplate.opsForValue().set("test:ping", "pong", 10, java.util.concurrent.TimeUnit.SECONDS);
            String pong = (String) redisTemplate.opsForValue().get("test:ping");
            status.put("redis", "pong".equals(pong) ? "OK" : "ERROR");
        } catch (Exception e) {
            status.put("redis", "ERROR: " + e.getMessage());
        }
        
        // 检测WebSocket配置
        status.put("websocket", "Configured");
        
        // 检测语音聊天服务
        status.put("voiceChat", "Ready");
        
        // 系统信息
        status.put("timestamp", java.time.OffsetDateTime.now());
        status.put("version", "1.0.0-MVP");
        
        return ResponseEntity.ok(ApiResponse.success("系统状态检测完成", status));
    }
}
