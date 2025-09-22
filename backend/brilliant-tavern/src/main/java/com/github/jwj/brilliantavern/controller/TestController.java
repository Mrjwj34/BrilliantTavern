package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.dto.ApiResponse;
import com.github.jwj.brilliantavern.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器
 */
@Tag(name = "测试接口", description = "用于测试系统功能的接口")
@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {

    /**
     * 测试认证状态
     */
    @Operation(summary = "获取用户信息", description = "测试用户认证状态，获取当前登录用户信息")
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {

            Map<String, Object> profileData = new HashMap<>();
            profileData.put("id", userPrincipal.getId());
            profileData.put("username", userPrincipal.getUsername());
            profileData.put("email", userPrincipal.getEmail());
            
            return ResponseEntity.ok(ApiResponse.success("获取用户信息成功", profileData));
        }
        
        return ResponseEntity.ok(ApiResponse.error(401, "未登录"));
    }

    /**
     * 公开接口测试
     */
    @Operation(summary = "公开接口测试", description = "测试无需认证的公开接口")
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<String>> publicEndpoint() {
        return ResponseEntity.ok(ApiResponse.success("这是一个公开接口", "Hello World!"));
    }
}
