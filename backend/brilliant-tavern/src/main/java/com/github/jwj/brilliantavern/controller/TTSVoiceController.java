package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.dto.CursorPageResponse;
import com.github.jwj.brilliantavern.dto.VoiceMarketFilter;
import com.github.jwj.brilliantavern.entity.TTSVoice;
import com.github.jwj.brilliantavern.exception.BusinessException;
import com.github.jwj.brilliantavern.service.TTSManagerService;
import com.github.jwj.brilliantavern.dto.TTSResponse;
import com.github.jwj.brilliantavern.service.tts.TTSVoiceService;
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
        @ApiResponse(responseCode = "200", description = "创建成功",
                content = @Content(mediaType = "application/json", 
                        schema = @Schema(implementation = TTSVoice.class))),
        @ApiResponse(responseCode = "400", description = "请求参数无效"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<com.github.jwj.brilliantavern.dto.ApiResponse<TTSVoice>>> createVoice(
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
            return Mono.just(ResponseEntity.badRequest()
                    .body(com.github.jwj.brilliantavern.dto.ApiResponse.error(400, "音频文件为空")));
        }
        return Mono.fromCallable(audioFile::getBytes)
                .flatMap(bytes -> ttsVoiceService.createVoice(userId, name, description, bytes, referenceText, isPublic))
                .map(voice -> ResponseEntity.ok(com.github.jwj.brilliantavern.dto.ApiResponse.success("创建音色成功", voice)))
                .onErrorResume(error -> {
                    log.error("创建音色失败", error);
                    String message = error.getMessage() != null ? error.getMessage() : "创建音色失败";
                    return Mono.just(ResponseEntity.badRequest().body(com.github.jwj.brilliantavern.dto.ApiResponse.error(400, message)));
                });
    }

    @Operation(
        summary = "删除音色", 
        description = "删除指定的TTS音色（软删除）"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "403", description = "没有权限删除此语音"),
        @ApiResponse(responseCode = "404", description = "语音不存在")
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
    @ApiResponse(responseCode = "200", description = "查询成功",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = CursorPageResponse.class)))
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
        @ApiResponse(responseCode = "200", description = "查询成功",
                content = @Content(mediaType = "application/json", 
                        schema = @Schema(implementation = TTSVoice.class)))
    })
    @GetMapping("/public")
    public Flux<TTSVoice> getPublicVoices(
            @Parameter(description = "用户ID，用于返回点赞状态")
            @RequestParam(value = "userId", required = false) String userId,
            @Parameter(description = "排序方式：newest 或 likes", schema = @Schema(defaultValue = "newest"))
            @RequestParam(value = "sort", defaultValue = "newest") String sort) {
        log.debug("获取公开语音列表: userId={}, sort={}", userId, sort);
        return ttsVoiceService.getPublicVoices(userId, sort);
    }

    @Operation(
        summary = "获取音色市场列表",
        description = "支持筛选、搜索和游标分页的音色市场数据"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = TTSVoice.class)))
    })
    @GetMapping("/market")
    public Mono<ResponseEntity<CursorPageResponse<TTSVoice>>> getVoiceMarket(
        @Parameter(description = "筛选类型", schema = @Schema(defaultValue = "public"))
        @RequestParam(defaultValue = "public") String filter,
            @Parameter(description = "搜索关键词")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "游标")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "返回数量", schema = @Schema(defaultValue = "20"))
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "用户ID，用于我的/点赞筛选")
            @RequestParam(required = false) String userId) {

        VoiceMarketFilter marketFilter = VoiceMarketFilter.fromString(filter);
        log.debug("获取音色市场: filter={}, keyword={}, size={}, cursor={}, userId={}", marketFilter, keyword, size, cursor, userId);

        return ttsVoiceService.getVoiceMarket(marketFilter, keyword, cursor, size, userId)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    if (error instanceof SecurityException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }
                    if (error instanceof BusinessException businessException) {
                        HttpStatus status = HttpStatus.resolve(businessException.getCode());
                        if (status == null) {
                            status = HttpStatus.BAD_REQUEST;
                        }
                        return Mono.just(ResponseEntity.status(status).build());
                    }
                    log.error("获取音色市场失败", error);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    @Operation(
        summary = "获取语音详情", 
        description = "根据语音ID获取语音的详细信息"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功",
                content = @Content(mediaType = "application/json", 
                        schema = @Schema(implementation = TTSVoice.class))),
        @ApiResponse(responseCode = "403", description = "没有权限访问此语音"),
        @ApiResponse(responseCode = "404", description = "语音不存在")
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
        @ApiResponse(responseCode = "200", description = "更新成功",
                content = @Content(mediaType = "application/json", 
                        schema = @Schema(implementation = TTSVoice.class))),
        @ApiResponse(responseCode = "403", description = "没有权限修改此语音"),
        @ApiResponse(responseCode = "404", description = "语音不存在")
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
        @ApiResponse(responseCode = "200", description = "搜索成功",
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
            @RequestParam(defaultValue = "true") Boolean includePublic,
            @Parameter(description = "排序方式：newest 或 likes", schema = @Schema(defaultValue = "newest"))
            @RequestParam(defaultValue = "newest") String sort) {
        
        log.debug("搜索语音: keyword={}, userId={}, includePublic={}, sort={}", keyword, userId, includePublic, sort);
        
        return ttsVoiceService.searchVoices(keyword, userId, includePublic, sort);
    }

    @Operation(
        summary = "点赞语音",
        description = "为指定语音点赞"
    )
    @PostMapping("/{voiceId}/like")
    public Mono<ResponseEntity<TTSVoice>> likeVoice(
            @Parameter(description = "语音ID", required = true)
            @PathVariable String voiceId,
            @Parameter(description = "用户ID", required = true)
            @RequestParam String userId) {

        log.info("点赞语音: voiceId={}, userId={}", voiceId, userId);

        return ttsVoiceService.likeVoice(voiceId, userId)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("点赞语音失败: voiceId={}, userId={}", voiceId, userId, error);
                    HttpStatus status = error instanceof SecurityException ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
                    return Mono.just(ResponseEntity.status(status).build());
                });
    }

    @Operation(
        summary = "取消点赞语音",
        description = "取消对指定语音的点赞"
    )
    @DeleteMapping("/{voiceId}/like")
    public Mono<ResponseEntity<TTSVoice>> unlikeVoice(
            @Parameter(description = "语音ID", required = true)
            @PathVariable String voiceId,
            @Parameter(description = "用户ID", required = true)
            @RequestParam String userId) {

        log.info("取消点赞语音: voiceId={}, userId={}", voiceId, userId);

        return ttsVoiceService.unlikeVoice(voiceId, userId)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("取消点赞语音失败: voiceId={}, userId={}", voiceId, userId, error);
                    HttpStatus status = error instanceof SecurityException ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
                    return Mono.just(ResponseEntity.status(status).build());
                });
    }

    @Operation(
        summary = "语音合成",
        description = "根据可选的音色ID生成语音；未提供voiceId则使用默认音色"
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
        @ApiResponse(responseCode = "400", description = "文本内容为空或参数错误"),
        @ApiResponse(responseCode = "500", description = "TTS服务异常或语音生成失败")
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
