package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.dto.ApiResponse;
import com.github.jwj.brilliantavern.dto.VoiceOption;
import com.github.jwj.brilliantavern.service.VoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 语音相关API控制器
 */
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
@Tag(name = "语音管理", description = "语音相关API")
public class VoiceController {

    private final VoiceService voiceService;

    @GetMapping("/list")
    @Operation(summary = "获取可用语音列表", description = "获取所有可用的TTS语音选项")
    public ResponseEntity<ApiResponse<List<VoiceOption>>> getVoiceList() {
        List<VoiceOption> voices = voiceService.getAvailableVoices();
        return ResponseEntity.ok(ApiResponse.success("获取语音列表成功", voices));
    }
}
