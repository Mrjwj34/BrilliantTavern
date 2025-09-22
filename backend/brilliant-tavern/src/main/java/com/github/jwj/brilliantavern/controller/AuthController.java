package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.dto.ApiResponse;
import com.github.jwj.brilliantavern.dto.JwtResponse;
import com.github.jwj.brilliantavern.dto.LoginRequest;
import com.github.jwj.brilliantavern.dto.RegisterRequest;
import com.github.jwj.brilliantavern.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Tag(name = "认证管理", description = "用户注册、登录、登出相关接口")
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册
     */
    @Operation(summary = "用户注册", description = "创建新用户账户")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<JwtResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        log.info("收到用户注册请求: {}", request.getUsername());
        JwtResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("注册成功", response));
    }

    /**
     * 用户登录
     */
    @Operation(summary = "用户登录", description = "用户身份验证，返回JWT令牌")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        log.info("收到用户登录请求: {}", request.getUsername());
        JwtResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("登录成功", response));
    }

    /**
     * 用户登出
     */
    @Operation(summary = "用户登出", description = "用户登出（主要在前端清除token）")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout() {
        log.info("用户登出");
        return ResponseEntity.ok(ApiResponse.success("登出成功", "已成功登出"));
    }
}
