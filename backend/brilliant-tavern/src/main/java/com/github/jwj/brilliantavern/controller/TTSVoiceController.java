package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.dto.ApiResponse;
import com.github.jwj.brilliantavern.entity.TTSVoice;
import com.github.jwj.brilliantavern.service.TTSManagerService;
import com.github.jwj.brilliantavern.service.tts.TTSResponse;
import com.github.jwj.brilliantavern.service.tts.TTSVoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * TTS语音管理控制器
 */
@Tag(name = "音色管理及语音生成", description = "TTS音色的创建、删除、查询等管理功能及语音生成接口")
@RestController
@RequestMapping("/tts/reference")
@RequiredArgsConstructor
@Slf4j
public class TTSVoiceController {

    private final TTSVoiceService ttsVoiceService;
    private final TTSManagerService ttsManagerService;

    @Operation(
        summary = "创建音色", 
        description = "上传音频文件创建新的TTS音色，支持个人和公开两种模式"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "创建成功",
                content = @Content(mediaType = "application/json", 
                        schema = @Schema(implementation = TTSVoice.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ApiResponse<TTSVoice>>> createVoice(
            @Parameter(description = "用户ID", required = true)
            @RequestParam String userId,
            @Parameter(description = "语音名称", required = true)
            @RequestParam String name,
            @Parameter(description = "语音描述")
            @RequestParam(required = false) String description,
            @Parameter(description = "音频文件(二进制)", required = true)
            @RequestPart("audio") MultipartFile audioFile,
            @Parameter(description = "参考文本", required = true)
            @RequestParam String referenceText,
            @Parameter(description = "是否公开", schema = @Schema(defaultValue = "false"))
            @RequestParam(defaultValue = "false") Boolean isPublic) {
        
        log.info("创建音色请求: userId={}, name={}, isPublic={}, fileName={}, size={}B",
                userId, name, isPublic, audioFile != null ? audioFile.getOriginalFilename() : null,
                audioFile != null ? audioFile.getSize() : -1);

        if (audioFile == null || audioFile.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error(400, "音频文件为空")));
        }
        return Mono.fromCallable(audioFile::getBytes)
                .flatMap(bytes -> ttsVoiceService.createVoice(userId, name, description, bytes, referenceText, isPublic))
                .map(voice -> ResponseEntity.ok(ApiResponse.success("创建音色成功", voice)))
                .onErrorReturn(ResponseEntity.badRequest().body(ApiResponse.error(400, "创建音色失败")));
    }

    @Operation(
        summary = "删除音色", 
        description = "删除指定的TTS音色（软删除）"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "没有权限删除此语音"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "语音不存在")
    })
    @DeleteMapping("/{voiceId}")
    public Mono<ResponseEntity<Void>> deleteVoice(
            @Parameter(description = "语音ID", required = true)
            @PathVariable String voiceId,
            @Parameter(description = "用户ID", required = true)
            @RequestParam String userId) {
        
        log.info("删除音色请求: voiceId={}, userId={}", voiceId, userId);
        
        return ttsVoiceService.deleteVoice(voiceId, userId)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .onErrorReturn(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "获取用户语音列表", 
        description = "获取指定用户可访问的语音列表"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功",
                content = @Content(mediaType = "application/json", 
                        schema = @Schema(implementation = TTSVoice.class)))
    })
    @GetMapping
    public Flux<TTSVoice> getUserVoices(
            @Parameter(description = "用户ID", required = true)
            @RequestParam String userId) {
        
        log.debug("获取用户语音列表: userId {}", userId);
        
        return ttsVoiceService.getUserVoices(userId);
    }

    @Operation(
        summary = "获取公开语音列表", 
        description = "获取所有公开可用的TTS语音列表"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功",
                content = @Content(mediaType = "application/json", 
                        schema = @Schema(implementation = TTSVoice.class)))
    })
    @GetMapping("/public")
    public Flux<TTSVoice> getPublicVoices() {
        log.debug("获取公开语音列表");
        return ttsVoiceService.getPublicVoices();
    }

    @Operation(
        summary = "获取语音详情", 
        description = "根据语音ID获取语音的详细信息"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功",
                content = @Content(mediaType = "application/json", 
                        schema = @Schema(implementation = TTSVoice.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "没有权限访问此语音"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "语音不存在")
    })
    @GetMapping("/{voiceId}")
    public Mono<ResponseEntity<TTSVoice>> getVoice(
            @Parameter(description = "语音ID", required = true)
            @PathVariable String voiceId,
            @Parameter(description = "用户ID", required = true)
            @RequestParam String userId) {
        
        log.debug("获取语音详情: voiceId={}, userId={}", voiceId, userId);
        
        return ttsVoiceService.getVoice(voiceId, userId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "更新语音信息", 
        description = "更新语音的名称、描述、公开状态等信息"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功",
                content = @Content(mediaType = "application/json", 
                        schema = @Schema(implementation = TTSVoice.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "没有权限修改此语音"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "语音不存在")
    })
    @PutMapping("/{voiceId}")
    public Mono<ResponseEntity<TTSVoice>> updateVoice(
            @Parameter(description = "语音ID", required = true)
            @PathVariable String voiceId,
            @Parameter(description = "用户ID", required = true)
            @RequestParam String userId,
            @Parameter(description = "新的语音名称")
            @RequestParam(required = false) String name,
            @Parameter(description = "新的语音描述")
            @RequestParam(required = false) String description,
            @Parameter(description = "是否公开")
            @RequestParam(required = false) Boolean isPublic) {
        
        log.info("更新语音信息: voiceId={}, userId={}", voiceId, userId);
        
        return ttsVoiceService.updateVoice(voiceId, userId, name, description, isPublic)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "搜索语音", 
        description = "根据关键词搜索语音，支持名称、描述、标签等字段模糊查询"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "搜索成功",
                content = @Content(mediaType = "application/json", 
                        schema = @Schema(implementation = TTSVoice.class)))
    })
    @GetMapping("/search")
    public Flux<TTSVoice> searchVoices(
            @Parameter(description = "搜索关键词", required = true)
            @RequestParam String keyword,
            @Parameter(description = "用户ID", required = true)
            @RequestParam String userId,
            @Parameter(description = "是否包含公开语音", schema = @Schema(defaultValue = "true"))
            @RequestParam(defaultValue = "true") Boolean includePublic) {
        
        log.debug("搜索语音: keyword={}, userId={}, includePublic={}", keyword, userId, includePublic);
        
        return ttsVoiceService.searchVoices(keyword, userId, includePublic);
    }

    @Operation(
        summary = "语音合成",
        description = "根据可选的音色ID生成语音；未提供voiceId则使用默认音色"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "语音生成成功",
            content = @Content(
                mediaType = "audio/wav",
                schema = @Schema(type = "string", format = "binary")
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "文本内容为空或参数错误"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "TTS服务异常或语音生成失败")
    })
    @PostMapping("/speak")
    public Mono<ResponseEntity<byte[]>> speak(
            @Parameter(description = "需要转换为语音的文本内容", required = true)
            @RequestParam String text,
            @Parameter(description = "音色标识符，不传则使用默认音色")
            @RequestParam(required = false) String voiceId) {

        log.debug("统一语音合成: voiceId={}, 文本长度={}", voiceId, text != null ? text.length() : 0);
        if (text == null || text.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        Mono<TTSResponse> mono = (voiceId == null || voiceId.isBlank())
                ? ttsManagerService.generateSpeechWithDefaultVoice(text)
                : ttsManagerService.generateSpeechWithVoice(text, voiceId);

        return mono
                .map(this::buildAudioResponse)
                .onErrorResume(error -> {
                    log.error("语音生成失败", error);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    private ResponseEntity<byte[]> buildAudioResponse(TTSResponse ttsResponse) {
        if (ttsResponse == null || !Boolean.TRUE.equals(ttsResponse.getSuccess()) || ttsResponse.getAudioData() == null) {
            return ResponseEntity.internalServerError().build();
        }
        HttpHeaders headers = new HttpHeaders();
        switch (ttsResponse.getAudioFormat()) {
            case MP3 -> headers.setContentType(MediaType.valueOf("audio/mpeg"));
            case WAV -> headers.setContentType(MediaType.valueOf("audio/wav"));
            case OGG -> headers.setContentType(MediaType.valueOf("audio/ogg"));
            case WEBM -> headers.setContentType(MediaType.valueOf("audio/webm"));
            default -> headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        if (ttsResponse.getVoiceId() != null) {
            headers.set("X-Voice-ID", ttsResponse.getVoiceId());
        }
        return ResponseEntity.ok().headers(headers).body(ttsResponse.getAudioData());
    }
}
