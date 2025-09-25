package com.github.jwj.brilliantavern.service.tts.impl;

import com.github.jwj.brilliantavern.service.tts.TTSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FishSpeech TTS服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FishSpeechTTSService implements TTSService {

    private final WebClient webClient;
    
    @Value("${app.tts.base-url}")
    private String baseUrl;
    
    @Value("${app.tts.timeout:30s}")
    private Duration timeout;
    
    @Value("${app.tts.audio.format:wav}")
    private String audioFormat;
    
    @Value("${app.tts.audio.chunk-length:200}")
    private Integer chunkLength;
    
    @Value("${app.tts.audio.normalize:true}")
    private Boolean normalize;
    
    @Value("${app.tts.audio.streaming:false}")
    private Boolean streaming;
    
    @Value("${app.tts.audio.max-new-tokens:1024}")
    private Integer maxNewTokens;
    
    @Value("${app.tts.audio.top-p:0.8}")
    private Double topP;
    
    @Value("${app.tts.audio.repetition-penalty:1.1}")
    private Double repetitionPenalty;
    
    @Value("${app.tts.audio.temperature:0.8}")
    private Double temperature;

    private WebClient fishSpeechWebClient;

    private WebClient getFishSpeechWebClient() {
        if (fishSpeechWebClient == null) {
            fishSpeechWebClient = webClient.mutate()
                    .baseUrl(baseUrl)
                    .build();
        }
        return fishSpeechWebClient;
    }

    @Override
    public Mono<byte[]> textToSpeech(String text, String voiceId) {
        log.info("FishSpeech TTS转换: 文本='{}', 音色='{}'", 
                text.length() > 50 ? text.substring(0, 50) + "..." : text, voiceId);

        // 构建请求参数
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("text", text);
        requestBody.put("chunk_length", chunkLength);
        requestBody.put("format", audioFormat);
        requestBody.put("references", List.of());
        requestBody.put("reference_id", voiceId != null ? voiceId : "1");
        requestBody.put("seed", null);
        requestBody.put("use_memory_cache", "on");
        requestBody.put("normalize", normalize);
        requestBody.put("streaming", streaming);
        requestBody.put("max_new_tokens", maxNewTokens);
        requestBody.put("top_p", topP);
        requestBody.put("repetition_penalty", repetitionPenalty);
        requestBody.put("temperature", temperature);

        return getFishSpeechWebClient()
                .post()
                .uri("/v1/tts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class)
                        .map(body -> new RuntimeException("TTS服务错误: " + response.statusCode() + " - " + body))
                )
                .bodyToFlux(DataBuffer.class)
                .collectList()
                .map(dataBuffers -> {
                    // 合并所有数据块
                    int totalSize = dataBuffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
                    byte[] result = new byte[totalSize];
                    int offset = 0;
                    
                    for (DataBuffer buffer : dataBuffers) {
                        int size = buffer.readableByteCount();
                        buffer.read(result, offset, size);
                        offset += size;
                    }
                    
                    log.debug("TTS转换成功，音频大约 {} 字节", result.length);
                    return result;
                })
                .timeout(timeout)
                .doOnError(error -> log.error("FishSpeech TTS转换失败", error));
    }

    /**
     * 添加语音引用
     * 
     * @param referenceId 指定的引用ID
     * @param audioBytes 音频文件
     * @param text 参考文本
     * @return 完成的引用ID
     */
    public Mono<String> addVoiceReference(String referenceId, byte[] audioBytes, String text) {
        if (audioBytes == null || audioBytes.length == 0) {
            return Mono.error(new IllegalArgumentException("音频内容为空"));
        }
        log.info("添加指定ID的语音引用到FishSpeech服务: referenceId={}, text={}", referenceId, text);

        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return referenceId + ".wav";
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        fileHeaders.setContentDisposition(ContentDisposition.builder("form-data")
                .name("audio")
                .filename(audioResource.getFilename())
                .build());

        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("id", referenceId);
        formData.add("audio", new HttpEntity<>(audioResource, fileHeaders));
        formData.add("text", text);

        return getFishSpeechWebClient()
                .post()
                .uri("/v1/references/add")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(formData))
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class)
                        .map(body -> new RuntimeException("添加语音引用失败: " + response.statusCode() + " - " + body))
                )
                .bodyToMono(Void.class)
                .thenReturn(referenceId)
                .doOnSuccess(id -> log.info("成功添加指定ID的语音引用: {}", id))
                .doOnError(error -> log.error("添加指定ID的语音引用失败: {}", referenceId, error));
    }

    /**
     * 删除语音引用
     * 
     * @param referenceId 语音引用ID
     * @return 完成信号
     */
    public Mono<Void> deleteVoiceReference(String referenceId) {
        log.info("从FishSpeech服务删除语音引用: referenceId={}", referenceId);
        
        return getFishSpeechWebClient()
                .delete()
                .uri("/v1/reference/{id}", referenceId)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class)
                        .map(body -> new RuntimeException("删除语音引用失败: " + response.statusCode() + " - " + body))
                )
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("成功删除语音引用: {}", referenceId))
                .doOnError(error -> log.error("删除语音引用失败: {}", referenceId, error));
    }
}
