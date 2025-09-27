package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.dto.ApiResponse;
import com.github.jwj.brilliantavern.dto.voice.VoiceChatSessionRequest;
import com.github.jwj.brilliantavern.dto.voice.VoiceChatSessionResponse;
import com.github.jwj.brilliantavern.entity.ChatHistory;
import com.github.jwj.brilliantavern.entity.User;
import com.github.jwj.brilliantavern.security.UserPrincipal;
import com.github.jwj.brilliantavern.service.VoiceChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * 语音聊天REST API控制器
 */
@Tag(name = "语音聊天", description = "语音对话功能相关接口")
@Slf4j
@RestController
@RequestMapping("/voice-chat")
@RequiredArgsConstructor
public class VoiceChatController {
    
    private final VoiceChatService voiceChatService;

    /**
     * 创建语音聊天会话
     */
    @PostMapping("/sessions")
    @Operation(summary = "创建语音聊天会话", description = "为指定角色卡创建新的语音聊天会话")
    public ResponseEntity<ApiResponse<VoiceChatSessionResponse>> createSession(
            @Valid @RequestBody VoiceChatSessionRequest request,
            Authentication authentication) {
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = User.builder()
                .id(userPrincipal.getId())
                .username(userPrincipal.getUsername())
                .email(userPrincipal.getEmail())
                .build();
        
    VoiceChatSessionResponse session = voiceChatService.createSession(request, user);
    String websocketEndpoint = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path("/ws/voice-chat")
        .toUriString();
    session.setWebsocketEndpoint(websocketEndpoint);
        
        return ResponseEntity.ok(ApiResponse.success("创建语音聊天会话成功", session));
    }

    /**
     * 获取聊天历史
     */
    @GetMapping("/history")
    @Operation(summary = "获取聊天历史", description = "获取用户与指定角色卡的聊天历史")
    public ResponseEntity<ApiResponse<List<ChatHistory>>> getChatHistory(
            @RequestParam UUID characterCardId,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        List<ChatHistory> history = voiceChatService.getChatHistory(
                userPrincipal.getId(), 
                characterCardId, 
                limit
        );
        
        return ResponseEntity.ok(ApiResponse.success("获取聊天历史成功", history));
    }

    /**
     * 关闭语音聊天会话
     */
    @PostMapping("/sessions/{sessionId}/close")
    @Operation(summary = "关闭语音聊天会话", description = "关闭指定的语音聊天会话")
    public ResponseEntity<ApiResponse<Void>> closeSession(
            @PathVariable UUID sessionId,
            Authentication authentication) {
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        voiceChatService.closeSession(sessionId, userPrincipal.getId());
        
        return ResponseEntity.ok(ApiResponse.success("关闭会话成功", null));
    }

    /**
     * 检查会话状态
     */
    @GetMapping("/sessions/{sessionId}/status")
    @Operation(summary = "检查会话状态", description = "检查语音聊天会话是否活跃")
    public ResponseEntity<ApiResponse<Boolean>> checkSessionStatus(@PathVariable UUID sessionId) {
        
        boolean isActive = voiceChatService.isSessionActive(sessionId);
        
        return ResponseEntity.ok(ApiResponse.success("获取会话状态成功", isActive));
    }
}
