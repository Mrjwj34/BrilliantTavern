package com.github.jwj.brilliantavern.service.tts.impl;

import com.github.jwj.brilliantavern.entity.TTSVoice;
import com.github.jwj.brilliantavern.entity.TTSVoiceLike;
import com.github.jwj.brilliantavern.entity.TTSVoiceLikeId;
import com.github.jwj.brilliantavern.entity.User;
import com.github.jwj.brilliantavern.repository.TTSVoiceRepository;
import com.github.jwj.brilliantavern.repository.TTSVoiceLikeRepository;
import com.github.jwj.brilliantavern.repository.UserRepository;
import com.github.jwj.brilliantavern.service.TTSCacheService;
import com.github.jwj.brilliantavern.service.tts.TTSVoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * TTS语音管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TTSVoiceServiceImpl implements TTSVoiceService {
    
    private final TTSVoiceRepository ttsVoiceRepository;
    private final TTSVoiceLikeRepository ttsVoiceLikeRepository;
    private final UserRepository userRepository;
    private final FishSpeechTTSService fishSpeechService;
    private final TTSCacheService ttsCacheService;
    
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
    UUID creatorUuid = UUID.fromString(userId);

    return fishSpeechService.addVoiceReference(referenceId, audioBytes, referenceText)
        .flatMap(returnedReferenceId -> Mono.fromCallable(() -> {
                    log.info("FishSpeech服务确认引用ID: {}", returnedReferenceId);
                    // 创建TTSVoice实体
                    TTSVoice voice = new TTSVoice();
                    // ID 由数据库自动生成，不需要设置
                    voice.setName(name);
                    voice.setDescription(description);
                    voice.setReferenceId(referenceId);
            voice.setCreatorId(creatorUuid);
                    voice.setIsPublic(isPublic != null ? isPublic : false);
                    voice.setCreatedAt(OffsetDateTime.now());
                    voice.setUpdatedAt(OffsetDateTime.now());
                    voice.setDeleted(false);
                    voice.setReferenceText(referenceText);
            voice.setLikesCount(0);
                    
                    // 保存到数据库
                    return ttsVoiceRepository.save(voice);
        }).subscribeOn(Schedulers.boundedElastic()))
        .map(savedVoice -> enrichVoice(savedVoice, creatorUuid))
                .doOnSuccess(voice -> log.info("成功创建语音引用: id={}, referenceId={}", 
                        voice.getId(), voice.getReferenceId()))
                .doOnError(error -> log.error("创建语音引用失败: userId={}, name= {}", userId, name, error));
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
                    
                    // 软删除并清理缓存
                    return Mono.fromRunnable(() -> {
                                voice.setDeleted(true);
                                voice.setUpdatedAt(OffsetDateTime.now());
                                ttsVoiceRepository.save(voice);
                                ttsCacheService.clearVoiceTestCache(voice.getReferenceId());
                            })
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .doOnSuccess(v -> log.info("成功删除语音引用: voiceId={}", voiceId))
                .doOnError(error -> log.error("删除语音引用失败: voiceId={}", voiceId, error))
                .then();
    }
    
    @Override
    public Flux<TTSVoice> getUserVoices(String userId) {
        log.debug("获取用户语音列表: userId={}", userId);
        
        return Mono.fromCallable(() -> {
            UUID userUuid = UUID.fromString(userId);
            List<TTSVoice> ownVoices = ttsVoiceRepository.findByCreatorIdAndNotDeleted(userUuid);
            List<Long> likedIds = ttsVoiceLikeRepository.findLikedVoiceIdsByUserId(userUuid);
            List<TTSVoice> likedVoices = likedIds.isEmpty()
                    ? Collections.emptyList()
                    : ttsVoiceRepository.findAllByIdInAndNotDeleted(likedIds);

            LinkedHashMap<Long, TTSVoice> voiceMap = new LinkedHashMap<>();
            ownVoices.forEach(voice -> voiceMap.put(voice.getId(), voice));
            likedVoices.forEach(voice -> {
                if (voice.getId() != null) {
                    voiceMap.putIfAbsent(voice.getId(), voice);
                }
            });

            List<TTSVoice> mergedVoices = new ArrayList<>(voiceMap.values());
            sortVoices(mergedVoices, "newest");
            enrichVoices(mergedVoices, userUuid);
            return mergedVoices;
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
    }
    
    @Override
    public Flux<TTSVoice> getPublicVoices(String userId, String sort) {
        log.debug("获取公开语音列表, userId={}, sort={}", userId, sort);

        UUID currentUserId = parseUserId(userId);

        return Mono.fromCallable(() -> {
            List<TTSVoice> voices = new ArrayList<>(ttsVoiceRepository.findPublicVoices());

            if (currentUserId != null) {
                List<TTSVoice> ownVoices = ttsVoiceRepository.findByCreatorIdAndNotDeleted(currentUserId);
                List<Long> likedIds = ttsVoiceLikeRepository.findLikedVoiceIdsByUserId(currentUserId);
                List<TTSVoice> likedVoices = likedIds.isEmpty()
                        ? Collections.emptyList()
                        : ttsVoiceRepository.findAllByIdInAndNotDeleted(likedIds);

                LinkedHashMap<Long, TTSVoice> merged = new LinkedHashMap<>();
                ownVoices.forEach(voice -> merged.put(voice.getId(), voice));
                voices.forEach(voice -> {
                    if (voice.getId() != null) {
                        merged.putIfAbsent(voice.getId(), voice);
                    }
                });
                likedVoices.forEach(voice -> {
                    if (voice.getId() != null) {
                        merged.putIfAbsent(voice.getId(), voice);
                    }
                });
                voices = new ArrayList<>(merged.values());
            }

            sortVoices(voices, sort);
            enrichVoices(voices, currentUserId);
            return voices;
        }).subscribeOn(Schedulers.boundedElastic())
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
                    
                    enrichVoices(Collections.singletonList(voice), userUuid);
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
                .subscribeOn(Schedulers.boundedElastic())
                .map(savedVoice -> enrichVoice(savedVoice, userUuid));
                })
                .doOnSuccess(voice -> log.info("成功更新语音信息: voiceId={}", voiceId))
                .doOnError(error -> log.error("更新语音信息失败: voiceId={}", voiceId, error));
    }

    
    @Override
    public Flux<TTSVoice> searchVoices(String keyword, String userId, Boolean includePublic, String sort) {
        log.debug("搜索语音: keyword={}, userId={}, includePublic={}, sort={}", keyword, userId, includePublic, sort);

        return Mono.fromCallable(() -> {
            UUID userUuid = UUID.fromString(userId);
            List<TTSVoice> voices;
            if (includePublic != null && includePublic) {
                voices = ttsVoiceRepository.searchAccessibleVoices(keyword, userUuid);
            } else {
                voices = ttsVoiceRepository.searchUserVoices(keyword, userUuid);
            }
            sortVoices(voices, sort);
            enrichVoices(voices, userUuid);
            return voices;
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
    }

    @Override
    @Transactional
    public Mono<TTSVoice> likeVoice(String voiceId, String userId) {
        log.info("点赞语音: voiceId={}, userId={}", voiceId, userId);

        return Mono.fromCallable(() -> {
            Long voiceLongId = Long.parseLong(voiceId);
            UUID userUuid = UUID.fromString(userId);

            TTSVoice voice = ttsVoiceRepository.findByIdAndNotDeleted(voiceLongId)
                    .orElseThrow(() -> new IllegalArgumentException("语音不存在"));

            if (!Boolean.TRUE.equals(voice.getIsPublic()) && !voice.getCreatorId().equals(userUuid)) {
                throw new SecurityException("没有权限点赞此语音");
            }

            if (!ttsVoiceLikeRepository.existsByIdUserIdAndIdVoiceId(userUuid, voiceLongId)) {
                TTSVoiceLike like = TTSVoiceLike.builder()
                        .id(new TTSVoiceLikeId(userUuid, voiceLongId))
                        .build();
                ttsVoiceLikeRepository.save(like);
            }

            TTSVoice refreshed = ttsVoiceRepository.findByIdAndNotDeleted(voiceLongId)
                    .orElseThrow(() -> new IllegalArgumentException("语音不存在"));
            return enrichVoice(refreshed, userUuid);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    @Transactional
    public Mono<TTSVoice> unlikeVoice(String voiceId, String userId) {
        log.info("取消点赞语音: voiceId={}, userId={}", voiceId, userId);

        return Mono.fromCallable(() -> {
            Long voiceLongId = Long.parseLong(voiceId);
            UUID userUuid = UUID.fromString(userId);

            TTSVoice voice = ttsVoiceRepository.findByIdAndNotDeleted(voiceLongId)
                    .orElseThrow(() -> new IllegalArgumentException("语音不存在"));

            if (!Boolean.TRUE.equals(voice.getIsPublic()) && !voice.getCreatorId().equals(userUuid)) {
                throw new SecurityException("没有权限操作此语音");
            }

            if (ttsVoiceLikeRepository.existsByIdUserIdAndIdVoiceId(userUuid, voiceLongId)) {
                ttsVoiceLikeRepository.deleteById(new TTSVoiceLikeId(userUuid, voiceLongId));
            }

            TTSVoice refreshed = ttsVoiceRepository.findByIdAndNotDeleted(voiceLongId)
                    .orElseThrow(() -> new IllegalArgumentException("语音不存在"));
            return enrichVoice(refreshed, userUuid);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private UUID parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return UUID.fromString(userId);
    }

    private void enrichVoices(List<TTSVoice> voices, UUID currentUserId) {
        if (voices == null || voices.isEmpty()) {
            return;
        }

        Set<UUID> creatorIds = voices.stream()
                .map(TTSVoice::getCreatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> creatorNameMap = creatorIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(creatorIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getUsername));

        Set<Long> likedVoiceIds = Collections.emptySet();
        List<Long> voiceIds = voices.stream()
                .map(TTSVoice::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (currentUserId != null && !voiceIds.isEmpty()) {
            likedVoiceIds = new HashSet<>(ttsVoiceLikeRepository.findLikedVoiceIds(currentUserId, voiceIds));
        }

        for (TTSVoice voice : voices) {
            if (voice.getLikesCount() == null) {
                voice.setLikesCount(0);
            }
            voice.setCreatorName(creatorNameMap.getOrDefault(voice.getCreatorId(), "匿名用户"));
            voice.setLiked(currentUserId != null && likedVoiceIds.contains(voice.getId()));
            voice.setOwned(currentUserId != null && currentUserId.equals(voice.getCreatorId()));
        }
    }

    private TTSVoice enrichVoice(TTSVoice voice, UUID currentUserId) {
        enrichVoices(Collections.singletonList(voice), currentUserId);
        return voice;
    }

    private void sortVoices(List<TTSVoice> voices, String sort) {
        if (voices == null || voices.isEmpty()) {
            return;
        }

        if ("likes".equalsIgnoreCase(sort)) {
            voices.sort(
                Comparator
                    .comparingInt((TTSVoice v) -> v.getLikesCount() == null ? 0 : v.getLikesCount())
                    .reversed()
                    .thenComparing(TTSVoice::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            );
        } else {
            voices.sort(Comparator.comparing(TTSVoice::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        }
    }

}
