package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.dto.ApiResponse;
import com.github.jwj.brilliantavern.dto.JwtResponse;
import com.github.jwj.brilliantavern.dto.LoginRequest;
import com.github.jwj.brilliantavern.dto.RegisterRequest;
import com.github.jwj.brilliantavern.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<JwtResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("收到用户注册请求: {}", request.getUsername());
        JwtResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("注册成功", response));
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("收到用户登录请求: {}", request.getUsername());
        JwtResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("登录成功", response));
    }

    /**
     * 用户登出（可选实现，主要在前端清除token）
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout() {
        log.info("用户登出");
        return ResponseEntity.ok(ApiResponse.success("登出成功", "已成功登出"));
    }
}
