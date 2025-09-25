package com.github.jwj.brilliantavern.service.tts.impl;

import com.github.jwj.brilliantavern.entity.TTSVoice;
import com.github.jwj.brilliantavern.repository.TTSVoiceRepository;
import com.github.jwj.brilliantavern.service.tts.TTSVoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * TTS语音管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TTSVoiceServiceImpl implements TTSVoiceService {
    
    private final TTSVoiceRepository ttsVoiceRepository;
    private final FishSpeechTTSService fishSpeechService;
    
    @Override
    @Transactional
    public Mono<TTSVoice> createVoice(String userId, String name, String description, 
                                     byte[] audioBytes, String referenceText, Boolean isPublic) {
        log.info("创建语音引用: userId={}, name={}", userId, name);
        
        if (audioBytes == null || audioBytes.length == 0) {
            return Mono.error(new IllegalArgumentException("音频文件为空"));
        }
        
        // 生成唯一的referenceId
        String referenceId = UUID.randomUUID().toString();
        log.info("生成引用ID: {}", referenceId);
        
        // 向FishSpeech服务添加音色（使用生成的UUID）
        return fishSpeechService.addVoiceReference(referenceId, audioBytes, referenceText)
                .flatMap(returnedReferenceId -> Mono.fromCallable(() -> {
                    log.info("FishSpeech服务确认引用ID: {}", returnedReferenceId);
                    // 创建TTSVoice实体
                    TTSVoice voice = new TTSVoice();
                    voice.setName(name);
                    voice.setDescription(description);
                    voice.setReferenceId(referenceId);
                    voice.setCreatorId(UUID.fromString(userId));
                    voice.setIsPublic(isPublic != null ? isPublic : false);
                    voice.setCreatedAt(OffsetDateTime.now());
                    voice.setUpdatedAt(OffsetDateTime.now());
                    voice.setDeleted(false);
                    voice.setReferenceText(referenceText);
                    
                    // 保存到数据库
                    return ttsVoiceRepository.save(voice);
                }).subscribeOn(Schedulers.boundedElastic()))
                .doOnSuccess(voice -> log.info("成功创建语音引用: id={}, referenceId={}", 
                        voice.getId(), voice.getReferenceId()))
                .doOnError(error -> log.error("创建语音引用失败: userId={}, name={}", 
                        userId, name, error));
    }
    
    @Override
    @Transactional
    public Mono<Void> deleteVoice(String voiceId, String userId) {
        log.info("删除语音引用: voiceId={}, userId={}", voiceId, userId);
        
        return Mono.fromCallable(() -> ttsVoiceRepository.findByIdAndNotDeleted(Long.parseLong(voiceId)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(voiceOpt -> {
                    if (voiceOpt.isEmpty()) {
                        return Mono.error(new IllegalArgumentException("语音不存在"));
                    }
                    
                    TTSVoice voice = voiceOpt.get();
                    UUID userUuid = UUID.fromString(userId);
                    
                    // 权限检查：只有创建者或系统管理员可以删除
                    if (!voice.getCreatorId().equals(userUuid)) {
                        return Mono.error(new SecurityException("没有权限删除此语音"));
                    }
                    
                    // 从FishSpeech服务删除引用
                    return fishSpeechService.deleteVoiceReference(voice.getReferenceId())
                            .then(Mono.fromRunnable(() -> {
                                // 软删除
                                voice.setDeleted(true);
                                voice.setUpdatedAt(OffsetDateTime.now());
                                ttsVoiceRepository.save(voice);
                            }).subscribeOn(Schedulers.boundedElastic()).then());
                })
                .doOnSuccess(v -> log.info("成功删除语音引用: voiceId={}", voiceId))
                .doOnError(error -> log.error("删除语音引用失败: voiceId={}", voiceId, error));
    }
    
    @Override
    public Flux<TTSVoice> getUserVoices(String userId) {
        log.debug("获取用户语音列表: userId={}", userId);
        
        return Mono.fromCallable(() -> {
            UUID userUuid = UUID.fromString(userId);
            // 查找用户创建的语音和点赞的语音
            return ttsVoiceRepository.findByCreatorIdAndNotDeleted(userUuid);
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
    }
    
    @Override
    public Flux<TTSVoice> getPublicVoices() {
        log.debug("获取公开语音列表");
        
        return Mono.fromCallable(ttsVoiceRepository::findPublicVoices)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }
    
    @Override
    public Mono<TTSVoice> getVoice(String voiceId, String userId) {
        log.debug("获取语音详情: voiceId={}, userId={}", voiceId, userId);
        
        return Mono.fromCallable(() -> ttsVoiceRepository.findByIdAndNotDeleted(Long.parseLong(voiceId)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(voiceOpt -> {
                    if (voiceOpt.isEmpty()) {
                        return Mono.error(new IllegalArgumentException("语音不存在"));
                    }
                    
                    TTSVoice voice = voiceOpt.get();
                    UUID userUuid = UUID.fromString(userId);
                    
                    // 权限检查：公开语音或自己创建的语音可以访问
                    if (!voice.getIsPublic() && !voice.getCreatorId().equals(userUuid)) {
                        return Mono.error(new SecurityException("没有权限访问此语音"));
                    }
                    
                    return Mono.just(voice);
                });
    }
    
    @Override
    @Transactional
    public Mono<TTSVoice> updateVoice(String voiceId, String userId, String name, 
                                     String description, Boolean isPublic) {
        log.info("更新语音信息: voiceId={}, userId={}", voiceId, userId);
        
        return Mono.fromCallable(() -> ttsVoiceRepository.findByIdAndNotDeleted(Long.parseLong(voiceId)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(voiceOpt -> {
                    if (voiceOpt.isEmpty()) {
                        return Mono.error(new IllegalArgumentException("语音不存在"));
                    }
                    
                    TTSVoice voice = voiceOpt.get();
                    UUID userUuid = UUID.fromString(userId);
                    
                    // 权限检查：只有创建者可以更新
                    if (!voice.getCreatorId().equals(userUuid)) {
                        return Mono.error(new SecurityException("没有权限修改此语音"));
                    }
                    
                    // 更新字段
                    if (name != null) {
                        voice.setName(name);
                    }
                    if (description != null) {
                        voice.setDescription(description);
                    }
                    if (isPublic != null) {
                        voice.setIsPublic(isPublic);
                    }
                    voice.setUpdatedAt(OffsetDateTime.now());
                    
                    return Mono.fromCallable(() -> ttsVoiceRepository.save(voice))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .doOnSuccess(voice -> log.info("成功更新语音信息: voiceId={}", voiceId))
                .doOnError(error -> log.error("更新语音信息失败: voiceId={}", voiceId, error));
    }

    
    @Override
    public Flux<TTSVoice> searchVoices(String keyword, String userId, Boolean includePublic) {
        log.debug("搜索语音: keyword={}, userId={}, includePublic={}", keyword, userId, includePublic);
        
        return Mono.fromCallable(() -> {
            UUID userUuid = UUID.fromString(userId);
            if (includePublic != null && includePublic) {
                return ttsVoiceRepository.searchAccessibleVoices(keyword, userUuid);
            } else {
                return ttsVoiceRepository.searchUserVoices(keyword, userUuid);
            }
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
    }

}
