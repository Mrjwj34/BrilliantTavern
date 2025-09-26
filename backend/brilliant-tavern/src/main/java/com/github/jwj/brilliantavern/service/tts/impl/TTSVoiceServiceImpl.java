package com.github.jwj.brilliantavern.service.tts.impl;

import com.github.jwj.brilliantavern.dto.CursorPageResponse;
import com.github.jwj.brilliantavern.dto.VoiceMarketFilter;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
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
import java.util.Base64;

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

    @PersistenceContext
    private EntityManager entityManager;
    
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
    public Mono<CursorPageResponse<TTSVoice>> getVoiceMarket(VoiceMarketFilter filter, String keyword, String cursor, int size, String userId) {
        return Mono.fromCallable(() -> {
            int normalizedSize = Math.min(Math.max(size, 1), 50);
            UUID currentUserId = parseUserId(userId);

            if (filter.requiresLogin() && currentUserId == null) {
                throw new SecurityException("请先登录以查看该列表");
            }

            VoiceCursorData cursorData = parseCursor(filter, cursor);
            String normalizedKeyword = normalizeKeyword(keyword);
            int fetchLimit = normalizedSize + 1;

            return switch (filter) {
                case LIKED -> buildLikedVoiceResponse(
                        fetchLikedVoices(currentUserId, normalizedKeyword, cursorData, fetchLimit),
                        normalizedSize,
                        currentUserId
                );
                case MY -> buildStandardVoiceResponse(
                        fetchMyVoices(currentUserId, normalizedKeyword, cursorData, fetchLimit),
                        VoiceMarketFilter.MY,
                        normalizedSize,
                        currentUserId
                );
                case POPULAR -> buildStandardVoiceResponse(
                        fetchPopularVoices(currentUserId, normalizedKeyword, cursorData, fetchLimit),
                        VoiceMarketFilter.POPULAR,
                        normalizedSize,
                        currentUserId
                );
                case LATEST -> buildStandardVoiceResponse(
                        fetchLatestVoices(currentUserId, normalizedKeyword, cursorData, fetchLimit),
                        VoiceMarketFilter.LATEST,
                        normalizedSize,
                        currentUserId
                );
                case PUBLIC -> buildStandardVoiceResponse(
                        fetchLatestVoices(currentUserId, normalizedKeyword, cursorData, fetchLimit),
                        VoiceMarketFilter.PUBLIC,
                        normalizedSize,
                        currentUserId
                );
            };
        }).subscribeOn(Schedulers.boundedElastic());
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

    private CursorPageResponse<TTSVoice> buildStandardVoiceResponse(
            List<TTSVoice> fetchedVoices,
            VoiceMarketFilter filter,
            int pageSize,
            UUID currentUserId) {

        boolean hasNext = fetchedVoices.size() > pageSize;
        List<TTSVoice> limited = hasNext
                ? new ArrayList<>(fetchedVoices.subList(0, pageSize))
                : new ArrayList<>(fetchedVoices);

        enrichVoices(limited, currentUserId);

        String nextCursor = null;
        if (hasNext && !limited.isEmpty()) {
            TTSVoice lastVoice = limited.get(limited.size() - 1);
            VoiceCursorData nextCursorData = switch (filter) {
                case POPULAR -> VoiceCursorData.byPopularity(
                        lastVoice.getLikesCount() == null ? 0 : lastVoice.getLikesCount(),
                        lastVoice.getCreatedAt(),
                        lastVoice.getId()
                );
                case PUBLIC, LATEST, MY -> VoiceCursorData.byCreatedAt(
                        lastVoice.getCreatedAt(),
                        lastVoice.getId()
                );
                case LIKED -> null;
            };
            nextCursor = encodeCursor(filter, nextCursorData);
        }

        return CursorPageResponse.<TTSVoice>builder()
                .items(limited)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    private CursorPageResponse<TTSVoice> buildLikedVoiceResponse(
            List<TTSVoiceLike> likes,
            int pageSize,
            UUID currentUserId) {

        boolean hasNext = likes.size() > pageSize;
        List<TTSVoiceLike> limited = hasNext
                ? new ArrayList<>(likes.subList(0, pageSize))
                : new ArrayList<>(likes);

        List<TTSVoice> voices = limited.stream()
                .map(TTSVoiceLike::getVoice)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        enrichVoices(voices, currentUserId);

        String nextCursor = null;
        if (hasNext && !limited.isEmpty()) {
            TTSVoiceLike lastLike = limited.get(limited.size() - 1);
            nextCursor = encodeCursor(
                    VoiceMarketFilter.LIKED,
                    VoiceCursorData.byLikedAt(lastLike.getCreatedAt(),
                            lastLike.getVoice() != null ? lastLike.getVoice().getId() : null)
            );
        }

        return CursorPageResponse.<TTSVoice>builder()
                .items(voices)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    private List<TTSVoice> fetchLatestVoices(UUID currentUserId, String keyword, VoiceCursorData cursorData, int limit) {
    StringBuilder jpql = new StringBuilder("SELECT v FROM TTSVoice v LEFT JOIN FETCH v.creator WHERE v.deleted = false");
        if (currentUserId != null) {
            jpql.append(" AND (v.isPublic = true OR v.creatorId = :currentUserId)");
        } else {
            jpql.append(" AND v.isPublic = true");
        }
        if (keyword != null) {
            appendVoiceKeywordCondition(jpql, "v");
        }
        if (cursorData != null && cursorData.getCreatedAt() != null && cursorData.getVoiceId() != null) {
            jpql.append(" AND (v.createdAt < :cursorCreated OR (v.createdAt = :cursorCreated AND v.id < :cursorVoiceId))");
        }
        jpql.append(" ORDER BY v.createdAt DESC, v.id DESC");

        TypedQuery<TTSVoice> query = entityManager.createQuery(jpql.toString(), TTSVoice.class);
        if (currentUserId != null) {
            query.setParameter("currentUserId", currentUserId);
        }
        if (keyword != null) {
            query.setParameter("keyword", buildKeywordPattern(keyword));
        }
        if (cursorData != null && cursorData.getCreatedAt() != null && cursorData.getVoiceId() != null) {
            query.setParameter("cursorCreated", cursorData.getCreatedAt());
            query.setParameter("cursorVoiceId", cursorData.getVoiceId());
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private List<TTSVoice> fetchPopularVoices(UUID currentUserId, String keyword, VoiceCursorData cursorData, int limit) {
    StringBuilder jpql = new StringBuilder("SELECT v FROM TTSVoice v LEFT JOIN FETCH v.creator WHERE v.deleted = false");
        if (currentUserId != null) {
            jpql.append(" AND (v.isPublic = true OR v.creatorId = :currentUserId)");
        } else {
            jpql.append(" AND v.isPublic = true");
        }
        if (keyword != null) {
            appendVoiceKeywordCondition(jpql, "v");
        }
        if (cursorData != null && cursorData.getLikesCount() != null && cursorData.getCreatedAt() != null && cursorData.getVoiceId() != null) {
            jpql.append(" AND (COALESCE(v.likesCount, 0) < :cursorLikes OR (COALESCE(v.likesCount, 0) = :cursorLikes AND (v.createdAt < :cursorCreated OR (v.createdAt = :cursorCreated AND v.id < :cursorVoiceId))))");
        }
        jpql.append(" ORDER BY COALESCE(v.likesCount, 0) DESC, v.createdAt DESC, v.id DESC");

        TypedQuery<TTSVoice> query = entityManager.createQuery(jpql.toString(), TTSVoice.class);
        if (currentUserId != null) {
            query.setParameter("currentUserId", currentUserId);
        }
        if (keyword != null) {
            query.setParameter("keyword", buildKeywordPattern(keyword));
        }
        if (cursorData != null && cursorData.getLikesCount() != null && cursorData.getCreatedAt() != null && cursorData.getVoiceId() != null) {
            query.setParameter("cursorLikes", cursorData.getLikesCount());
            query.setParameter("cursorCreated", cursorData.getCreatedAt());
            query.setParameter("cursorVoiceId", cursorData.getVoiceId());
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private List<TTSVoice> fetchMyVoices(UUID currentUserId, String keyword, VoiceCursorData cursorData, int limit) {
        if (currentUserId == null) {
            return Collections.emptyList();
        }
    StringBuilder jpql = new StringBuilder("SELECT v FROM TTSVoice v LEFT JOIN FETCH v.creator WHERE v.deleted = false AND v.creatorId = :currentUserId");
        if (keyword != null) {
            appendVoiceKeywordCondition(jpql, "v");
        }
        if (cursorData != null && cursorData.getCreatedAt() != null && cursorData.getVoiceId() != null) {
            jpql.append(" AND (v.createdAt < :cursorCreated OR (v.createdAt = :cursorCreated AND v.id < :cursorVoiceId))");
        }
        jpql.append(" ORDER BY v.createdAt DESC, v.id DESC");

        TypedQuery<TTSVoice> query = entityManager.createQuery(jpql.toString(), TTSVoice.class);
        query.setParameter("currentUserId", currentUserId);
        if (keyword != null) {
            query.setParameter("keyword", buildKeywordPattern(keyword));
        }
        if (cursorData != null && cursorData.getCreatedAt() != null && cursorData.getVoiceId() != null) {
            query.setParameter("cursorCreated", cursorData.getCreatedAt());
            query.setParameter("cursorVoiceId", cursorData.getVoiceId());
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private List<TTSVoiceLike> fetchLikedVoices(UUID currentUserId, String keyword, VoiceCursorData cursorData, int limit) {
        if (currentUserId == null) {
            return Collections.emptyList();
        }
        StringBuilder jpql = new StringBuilder(
                "SELECT l FROM TTSVoiceLike l " +
                "JOIN FETCH l.voice v " +
                "LEFT JOIN FETCH v.creator " +
                "WHERE l.id.userId = :currentUserId " +
                "AND v.deleted = false " +
                "AND (v.isPublic = true OR v.creatorId = :currentUserId)"
        );
        if (keyword != null) {
            appendVoiceKeywordCondition(jpql, "v");
        }
        if (cursorData != null && cursorData.getLikeCreatedAt() != null && cursorData.getVoiceId() != null) {
            jpql.append(" AND (l.createdAt < :cursorLikedAt OR (l.createdAt = :cursorLikedAt AND v.id < :cursorVoiceId))");
        }
        jpql.append(" ORDER BY l.createdAt DESC, v.id DESC");

        TypedQuery<TTSVoiceLike> query = entityManager.createQuery(jpql.toString(), TTSVoiceLike.class);
        query.setParameter("currentUserId", currentUserId);
        if (keyword != null) {
            query.setParameter("keyword", buildKeywordPattern(keyword));
        }
        if (cursorData != null && cursorData.getLikeCreatedAt() != null && cursorData.getVoiceId() != null) {
            query.setParameter("cursorLikedAt", cursorData.getLikeCreatedAt());
            query.setParameter("cursorVoiceId", cursorData.getVoiceId());
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private void appendVoiceKeywordCondition(StringBuilder jpql, String alias) {
        jpql.append(" AND (LOWER(").append(alias).append(".name) LIKE :keyword " +
                "OR LOWER(").append(alias).append(".description) LIKE :keyword)");
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildKeywordPattern(String keyword) {
        return "%" + keyword + "%";
    }

    private VoiceCursorData parseCursor(VoiceMarketFilter filter, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String decodedStr = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = decodedStr.split("\\|", -1);
            if (parts.length == 0 || !filter.name().equals(parts[0])) {
                return null;
            }
            return switch (filter) {
                case POPULAR -> {
                    if (parts.length < 4) {
                        yield null;
                    }
                    Integer likes = Integer.parseInt(parts[1]);
                    OffsetDateTime created = OffsetDateTime.parse(parts[2]);
                    Long voiceId = Long.parseLong(parts[3]);
                    yield VoiceCursorData.byPopularity(likes, created, voiceId);
                }
                case PUBLIC, LATEST, MY -> {
                    if (parts.length < 3) {
                        yield null;
                    }
                    OffsetDateTime created = OffsetDateTime.parse(parts[1]);
                    Long voiceId = Long.parseLong(parts[2]);
                    yield VoiceCursorData.byCreatedAt(created, voiceId);
                }
                case LIKED -> {
                    if (parts.length < 3) {
                        yield null;
                    }
                    OffsetDateTime likedAt = OffsetDateTime.parse(parts[1]);
                    Long voiceId = Long.parseLong(parts[2]);
                    yield VoiceCursorData.byLikedAt(likedAt, voiceId);
                }
            };
        } catch (Exception ex) {
            log.warn("解析音色游标失败: filter={}, cursor={}", filter, cursor, ex);
            return null;
        }
    }

    private String encodeCursor(VoiceMarketFilter filter, VoiceCursorData cursorData) {
        if (cursorData == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add(filter.name());
        switch (filter) {
            case POPULAR -> {
                if (cursorData.getLikesCount() == null || cursorData.getCreatedAt() == null || cursorData.getVoiceId() == null) {
                    return null;
                }
                parts.add(String.valueOf(cursorData.getLikesCount()));
                parts.add(cursorData.getCreatedAt().toString());
                parts.add(cursorData.getVoiceId().toString());
            }
            case PUBLIC, LATEST, MY -> {
                if (cursorData.getCreatedAt() == null || cursorData.getVoiceId() == null) {
                    return null;
                }
                parts.add(cursorData.getCreatedAt().toString());
                parts.add(cursorData.getVoiceId().toString());
            }
            case LIKED -> {
                if (cursorData.getLikeCreatedAt() == null || cursorData.getVoiceId() == null) {
                    return null;
                }
                parts.add(cursorData.getLikeCreatedAt().toString());
                parts.add(cursorData.getVoiceId().toString());
            }
        }
        String rawCursor = String.join("|", parts);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
    }

    private static class VoiceCursorData {
        private final Integer likesCount;
        private final OffsetDateTime createdAt;
        private final Long voiceId;
        private final OffsetDateTime likeCreatedAt;

        private VoiceCursorData(Integer likesCount, OffsetDateTime createdAt, Long voiceId, OffsetDateTime likeCreatedAt) {
            this.likesCount = likesCount;
            this.createdAt = createdAt;
            this.voiceId = voiceId;
            this.likeCreatedAt = likeCreatedAt;
        }

        static VoiceCursorData byPopularity(Integer likesCount, OffsetDateTime createdAt, Long voiceId) {
            return new VoiceCursorData(likesCount, createdAt, voiceId, null);
        }

        static VoiceCursorData byCreatedAt(OffsetDateTime createdAt, Long voiceId) {
            return new VoiceCursorData(null, createdAt, voiceId, null);
        }

        static VoiceCursorData byLikedAt(OffsetDateTime likedAt, Long voiceId) {
            return new VoiceCursorData(null, null, voiceId, likedAt);
        }

        Integer getLikesCount() {
            return likesCount;
        }

        OffsetDateTime getCreatedAt() {
            return createdAt;
        }

        Long getVoiceId() {
            return voiceId;
        }

        OffsetDateTime getLikeCreatedAt() {
            return likeCreatedAt;
        }
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
