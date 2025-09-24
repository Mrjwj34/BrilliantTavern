package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.github.jwj.brilliantavern.repository.CharacterCardRepository;
import com.github.jwj.brilliantavern.service.TTSManagerService;
import com.github.jwj.brilliantavern.service.tts.TTSResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * TTS语音合成控制器
 */
@Tag(name = "TTS语音合成", description = "文本转语音相关接口，支持角色语音生成、音色选择等功能")
@Slf4j
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
public class TTSController {

    private final TTSManagerService ttsManagerService;
    private final CharacterCardRepository characterCardRepository;

    /**
     * 为指定角色生成语音
     * 
     * @param characterId 角色卡ID
     * @param text 要转换的文本
     * @return 音频数据
     */
    @Operation(
        summary = "角色语音生成",
        description = "根据指定角色的音色特征，将输入文本转换为语音音频文件"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "语音生成成功",
            content = @Content(
                mediaType = "audio/wav",
                schema = @Schema(type = "string", format = "binary")
            )
        ),
        @ApiResponse(
            responseCode = "404", 
            description = "指定角色不存在"
        ),
        @ApiResponse(
            responseCode = "500", 
            description = "TTS服务异常或语音生成失败"
        )
    })
    @PostMapping("/character/{characterId}/speak")
    public Mono<ResponseEntity<byte[]>> generateCharacterSpeech(
            @Parameter(description = "角色卡UUID", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID characterId,
            @Parameter(description = "需要转换为语音的文本内容", required = true)
            @RequestBody String text) {
        
        log.debug("为角色 {} 生成语音，文本长度: {}", characterId, text.length());
        
        return Mono.fromCallable(() -> characterCardRepository.findById(characterId))
                .flatMap(optionalCard -> {
                    if (optionalCard.isEmpty()) {
                        return Mono.<ResponseEntity<byte[]>>just(ResponseEntity.notFound().build());
                    }
                    
                    CharacterCard characterCard = optionalCard.get();
                    return ttsManagerService.generateSpeech(text, characterCard)
                            .map(this::buildAudioResponse);
                })
                .onErrorResume(error -> {
                    log.error("角色语音生成失败", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * 使用指定音色生成语音
     * 
     * @param voiceId 音色ID
     * @param text 要转换的文本
     * @return 音频数据
     */
    @Operation(
        summary = "指定音色语音生成",
        description = "使用指定的音色ID生成语音，适用于自定义音色需求"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "语音生成成功",
            content = @Content(
                mediaType = "audio/wav",
                schema = @Schema(type = "string", format = "binary")
            )
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "音色ID不支持或参数错误"
        ),
        @ApiResponse(
            responseCode = "500", 
            description = "TTS服务异常或语音生成失败"
        )
    })
    @PostMapping("/voice/{voiceId}/speak")
    public Mono<ResponseEntity<byte[]>> generateSpeechWithVoice(
            @Parameter(description = "音色标识符", required = true, example = "female1")
            @PathVariable String voiceId,
            @Parameter(description = "需要转换为语音的文本内容", required = true)
            @RequestBody String text) {
        
        log.debug("使用音色 {} 生成语音，文本长度: {}", voiceId, text.length());
        
        return ttsManagerService.generateSpeechWithVoice(text, voiceId)
                .map(this::buildAudioResponse)
                .onErrorResume(error -> {
                    log.error("语音生成失败", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * 使用默认音色生成语音
     * 
     * @param text 要转换的文本
     * @return 音频数据
     */
    @Operation(
        summary = "默认音色语音生成",
        description = "使用系统默认音色生成语音，最简单的TTS接口"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "语音生成成功",
            content = @Content(
                mediaType = "audio/wav",
                schema = @Schema(type = "string", format = "binary")
            )
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "文本内容为空或参数错误"
        ),
        @ApiResponse(
            responseCode = "500", 
            description = "TTS服务异常或语音生成失败"
        )
    })
    @PostMapping("/speak")
    public Mono<ResponseEntity<byte[]>> generateDefaultSpeech(
            @Parameter(description = "需要转换为语音的文本内容", required = true)
            @RequestBody String text) {
        log.debug("使用默认音色生成语音，文本长度: {}", text.length());
        
        return ttsManagerService.generateSpeechWithDefaultVoice(text)
                .map(this::buildAudioResponse)
                .onErrorResume(error -> {
                    log.error("语音生成失败", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * 获取支持的音色列表
     * 
     * @return 音色列表
     */
    @Operation(
        summary = "获取支持的音色列表",
        description = "返回当前TTS服务支持的所有音色ID及其详细信息"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "成功获取音色列表",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(type = "array", example = "[\"default\", \"female1\", \"male1\"]")
            )
        ),
        @ApiResponse(
            responseCode = "500", 
            description = "TTS服务异常或无法获取音色信息"
        )
    })
    @GetMapping("/voices")
    public Mono<ResponseEntity<String[]>> getSupportedVoices() {
        log.debug("获取支持的音色列表");
        
        return ttsManagerService.getSupportedVoices()
                .map(voices -> ResponseEntity.ok(voices))
                .onErrorResume(error -> {
                    log.error("获取音色列表失败", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * 检查TTS服务状态
     * 
     * @return 服务状态
     */
    @Operation(
        summary = "检查TTS服务状态",
        description = "检查TTS服务是否正常运行，返回服务可用性状态"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "状态检查完成",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(type = "boolean", example = "true", description = "true表示服务正常，false表示服务异常")
            )
        )
    })
    @GetMapping("/status")
    public Mono<ResponseEntity<Boolean>> checkServiceStatus() {
        log.debug("检查TTS服务状态");
        
        return ttsManagerService.checkServiceStatus()
                .map(status -> ResponseEntity.ok(status))
                .onErrorResume(error -> {
                    log.error("TTS服务状态检查失败", error);
                    return Mono.just(ResponseEntity.ok(false));
                });
    }
    
    /**
     * 测试TTS服务
     */
    @Operation(
        summary = "TTS服务快速测试",
        description = "使用预设或自定义文本测试TTS服务功能，便于开发调试"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "测试成功，返回音频文件",
            content = @Content(
                mediaType = "audio/wav",
                schema = @Schema(type = "string", format = "binary")
            )
        ),
        @ApiResponse(
            responseCode = "500", 
            description = "TTS服务异常或测试失败"
        )
    })
    @PostMapping("/test")
    public Mono<ResponseEntity<byte[]>> testTTS(
            @Parameter(description = "测试用文本", example = "你好，这是TTS测试") 
            @RequestParam(defaultValue = "你好，这是TTS测试") String text) {
        log.info("TTS服务测试，文本: {}", text);
        
        return ttsManagerService.generateSpeechWithDefaultVoice(text)
                .map(this::buildAudioResponse)
                .onErrorResume(error -> {
                    log.error("TTS测试失败", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * 构建音频响应
     * 
     * @param ttsResponse TTS响应结果
     * @return HTTP响应
     */
    private ResponseEntity<byte[]> buildAudioResponse(TTSResponse ttsResponse) {
        if (!ttsResponse.getSuccess()) {
            log.warn("TTS生成失败: {}", ttsResponse.getErrorMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        
        HttpHeaders headers = new HttpHeaders();
        
        // 根据音频格式设置Content-Type
        switch (ttsResponse.getAudioFormat()) {
            case MP3:
                headers.setContentType(MediaType.valueOf("audio/mpeg"));
                break;
            case WAV:
                headers.setContentType(MediaType.valueOf("audio/wav"));
                break;
            case OGG:
                headers.setContentType(MediaType.valueOf("audio/ogg"));
                break;
            case WEBM:
                headers.setContentType(MediaType.valueOf("audio/webm"));
                break;
            default:
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        
        // 设置音频时长和其他元数据
        if (ttsResponse.getDuration() != null) {
            headers.set("X-Audio-Duration", ttsResponse.getDuration().toString());
        }
        if (ttsResponse.getVoiceId() != null) {
            headers.set("X-Voice-ID", ttsResponse.getVoiceId());
        }
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(ttsResponse.getAudioData());
    }
}
