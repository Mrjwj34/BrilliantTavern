package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.dto.CharacterCardMarketFilter;
import com.github.jwj.brilliantavern.dto.CharacterCardResponse;
import com.github.jwj.brilliantavern.dto.CursorPageResponse;
import com.github.jwj.brilliantavern.dto.CreateCharacterCardRequest;
import com.github.jwj.brilliantavern.dto.LikeResponse;
import com.github.jwj.brilliantavern.dto.UpdateCharacterCardRequest;
import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.github.jwj.brilliantavern.entity.TTSVoice;
import com.github.jwj.brilliantavern.entity.UserLike;
import com.github.jwj.brilliantavern.exception.BusinessException;
import com.github.jwj.brilliantavern.repository.CharacterCardRepository;
import com.github.jwj.brilliantavern.repository.TTSVoiceRepository;
import com.github.jwj.brilliantavern.repository.UserLikeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 角色卡服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterCardService {

    private final CharacterCardRepository characterCardRepository;
    private final UserLikeRepository userLikeRepository;
    private final TTSVoiceRepository ttsVoiceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 创建角色卡
     */
    @Transactional
    public CharacterCardResponse createCharacterCard(UUID creatorId, CreateCharacterCardRequest request) {
        log.info("创建角色卡: 用户={}, 角色名称={}", creatorId, request.getName());

        // 如果提供了ttsVoiceId,将其转换为reference_id
        String ttsReferenceId = null;
        if (request.getTtsVoiceId() != null) {
            ttsReferenceId = convertVoiceIdToReferenceId(request.getTtsVoiceId());
        }

        CharacterCard card = CharacterCard.builder()
                .creatorId(creatorId)
                .name(request.getName())
                .shortDescription(request.getShortDescription())
                .greetingMessage(request.getGreetingMessage())
                .isPublic(request.getIsPublic())
                .ttsVoiceId(ttsReferenceId)
                .avatarUrl(request.getAvatarUrl())
                .cardData(request.toCardData())
                .build();

        CharacterCard savedCard = characterCardRepository.save(card);
        log.info("角色卡创建成功: ID={}", savedCard.getId());

        return CharacterCardResponse.fromEntity(savedCard);
    }

    /**
     * 更新角色卡
     */
    @Transactional
    public CharacterCardResponse updateCharacterCard(UUID cardId, UUID userId, UpdateCharacterCardRequest request) {
        log.info("更新角色卡: ID={}, 用户={}", cardId, userId);

        CharacterCard card = characterCardRepository.findById(cardId)
                .orElseThrow(() -> new BusinessException(404, "角色卡不存在"));

        // 检查权限
        if (!card.getCreatorId().equals(userId)) {
            throw new BusinessException(403, "无权限修改该角色卡");
        }

        // 更新字段
        if (request.getName() != null) {
            card.setName(request.getName());
        }
        if (request.getShortDescription() != null) {
            card.setShortDescription(request.getShortDescription());
        }
        if (request.getGreetingMessage() != null) {
            card.setGreetingMessage(request.getGreetingMessage());
        }
        if (request.getIsPublic() != null) {
            card.setIsPublic(request.getIsPublic());
        }
        if (request.getTtsVoiceId() != null) {
            String ttsReferenceId = convertVoiceIdToReferenceId(request.getTtsVoiceId());
            card.setTtsVoiceId(ttsReferenceId);
        }
        if (request.getAvatarUrl() != null) {
            card.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getCardData() != null) {
            card.setCardData(request.toCardData());
        }

        CharacterCard savedCard = characterCardRepository.save(card);
        log.info("角色卡更新成功: ID={}", savedCard.getId());

        return CharacterCardResponse.fromEntity(savedCard);
    }

    /**
     * 删除角色卡
     */
    @Transactional
    public void deleteCharacterCard(UUID cardId, UUID userId) {
        log.info("删除角色卡: ID={}, 用户={}", cardId, userId);

        CharacterCard card = characterCardRepository.findById(cardId)
                .orElseThrow(() -> new BusinessException(404, "角色卡不存在"));

        // 检查权限
        if (!card.getCreatorId().equals(userId)) {
            throw new BusinessException(403, "无权限删除该角色卡");
        }

        characterCardRepository.delete(card);
        log.info("角色卡删除成功: ID={}", cardId);
    }

    /**
     * 根据ID获取角色卡（内部使用，不检查权限）
     */
    @Transactional(readOnly = true)
    public CharacterCard findById(UUID cardId) {
        return characterCardRepository.findById(cardId).orElse(null);
    }

    /**
     * 根据ID获取角色卡
     */
    @Transactional(readOnly = true)
    public CharacterCardResponse getCharacterCard(UUID cardId, UUID currentUserId) {
        CharacterCard card = characterCardRepository.findByIdWithCreator(cardId)
                .orElseThrow(() -> new BusinessException(404, "角色卡不存在"));

        // 检查访问权限
        if (!card.getIsPublic() && !card.getCreatorId().equals(currentUserId)) {
            throw new BusinessException(403, "无权限访问该角色卡");
        }

        // 检查当前用户是否已点赞
        boolean isLiked = currentUserId != null && 
                userLikeRepository.existsByUserIdAndCardId(currentUserId, cardId);

        return CharacterCardResponse.fromEntity(card, isLiked);
    }

    /**
     * 角色市场游标分页查询
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<CharacterCardResponse> getMarketCards(
            CharacterCardMarketFilter filter,
            String keyword,
            String cursor,
            int size,
            UUID currentUserId) {

        int normalizedSize = Math.min(Math.max(size, 1), 50);
        MarketCursorData cursorData = parseCursor(filter, cursor);
        String normalizedKeyword = normalizeKeyword(keyword);
        int fetchLimit = normalizedSize + 1;

        if ((filter == CharacterCardMarketFilter.MY || filter == CharacterCardMarketFilter.LIKED) && currentUserId == null) {
            throw new BusinessException(401, "请先登录以查看该列表");
        }

        return switch (filter) {
            case LIKED -> buildLikedResponse(normalizedKeyword, cursorData, fetchLimit, normalizedSize, currentUserId);
            case MY -> buildStandardResponse(
                    fetchMyCards(currentUserId, normalizedKeyword, cursorData, fetchLimit),
                    CharacterCardMarketFilter.MY,
                    normalizedSize,
                    currentUserId
            );
            case LATEST -> buildStandardResponse(
                    fetchLatestPublicCards(normalizedKeyword, cursorData, fetchLimit),
                    CharacterCardMarketFilter.LATEST,
                    normalizedSize,
                    currentUserId
            );
            case POPULAR -> buildStandardResponse(
                    fetchPopularPublicCards(normalizedKeyword, cursorData, fetchLimit),
                    CharacterCardMarketFilter.POPULAR,
                    normalizedSize,
                    currentUserId
            );
            case PUBLIC -> buildStandardResponse(
                    fetchPopularPublicCards(normalizedKeyword, cursorData, fetchLimit),
                    CharacterCardMarketFilter.PUBLIC,
                    normalizedSize,
                    currentUserId
            );
        };
    }

    private CursorPageResponse<CharacterCardResponse> buildStandardResponse(
            List<CharacterCard> fetchedCards,
            CharacterCardMarketFilter filter,
            int pageSize,
            UUID currentUserId) {

        boolean hasNext = fetchedCards.size() > pageSize;
        List<CharacterCard> limited = hasNext
                ? new ArrayList<>(fetchedCards.subList(0, pageSize))
                : new ArrayList<>(fetchedCards);

        Set<UUID> likedCardIds;
        if (currentUserId != null && !limited.isEmpty()) {
            List<UUID> cardIds = limited.stream()
                    .map(CharacterCard::getId)
                    .collect(Collectors.toList());
            likedCardIds = new HashSet<>(userLikeRepository.findCardIdsByUserIdAndCardIdIn(currentUserId, cardIds));
        } else {
            likedCardIds = Collections.emptySet();
        }

        List<CharacterCardResponse> items = limited.stream()
                .map(card -> CharacterCardResponse.fromEntity(card, likedCardIds.contains(card.getId())))
                .collect(Collectors.toList());

        String nextCursor = null;
        if (hasNext && !limited.isEmpty()) {
            CharacterCard lastCard = limited.get(limited.size() - 1);
            MarketCursorData nextCursorData = switch (filter) {
                case POPULAR, PUBLIC -> MarketCursorData.byPopularity(lastCard.getLikesCount(), lastCard.getCreatedAt(), lastCard.getId());
                case LATEST, MY -> MarketCursorData.byCreatedAt(lastCard.getCreatedAt(), lastCard.getId());
                case LIKED -> null;
            };
            nextCursor = encodeCursor(filter, nextCursorData);
        }

        return CursorPageResponse.<CharacterCardResponse>builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    private CursorPageResponse<CharacterCardResponse> buildLikedResponse(
            String keyword,
            MarketCursorData cursorData,
            int fetchLimit,
            int pageSize,
            UUID currentUserId) {

        if (currentUserId == null) {
            return CursorPageResponse.<CharacterCardResponse>builder().build();
        }

        List<UserLike> likes = fetchLikedCards(currentUserId, keyword, cursorData, fetchLimit);
        boolean hasNext = likes.size() > pageSize;
        List<UserLike> limited = hasNext
                ? new ArrayList<>(likes.subList(0, pageSize))
                : new ArrayList<>(likes);

        List<CharacterCardResponse> items = limited.stream()
                .map(UserLike::getCharacterCard)
                .map(card -> CharacterCardResponse.fromEntity(card, true))
                .collect(Collectors.toList());

        String nextCursor = null;
        if (hasNext && !limited.isEmpty()) {
            UserLike lastLike = limited.get(limited.size() - 1);
            nextCursor = encodeCursor(
                    CharacterCardMarketFilter.LIKED,
                    MarketCursorData.byLikedAt(lastLike.getCreatedAt(), lastLike.getCardId())
            );
        }

        return CursorPageResponse.<CharacterCardResponse>builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    private List<CharacterCard> fetchPopularPublicCards(String keyword, MarketCursorData cursorData, int limit) {
        StringBuilder jpql = new StringBuilder("SELECT c FROM CharacterCard c LEFT JOIN FETCH c.creator WHERE c.isPublic = true");
        if (keyword != null) {
            appendKeywordCondition(jpql, "c");
        }
        if (cursorData != null && cursorData.getLikesCount() != null
                && cursorData.getCreatedAt() != null && cursorData.getCardId() != null) {
            jpql.append(" AND (c.likesCount < :cursorLikes OR (c.likesCount = :cursorLikes AND (c.createdAt < :cursorCreated OR (c.createdAt = :cursorCreated AND c.id < :cursorCardId))))");
        }
        jpql.append(" ORDER BY c.likesCount DESC, c.createdAt DESC, c.id DESC");

        TypedQuery<CharacterCard> query = entityManager.createQuery(jpql.toString(), CharacterCard.class);
        if (keyword != null) {
            query.setParameter("keyword", buildKeywordPattern(keyword));
        }
        if (cursorData != null && cursorData.getLikesCount() != null
                && cursorData.getCreatedAt() != null && cursorData.getCardId() != null) {
            query.setParameter("cursorLikes", cursorData.getLikesCount());
            query.setParameter("cursorCreated", cursorData.getCreatedAt());
            query.setParameter("cursorCardId", cursorData.getCardId());
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private List<CharacterCard> fetchLatestPublicCards(String keyword, MarketCursorData cursorData, int limit) {
        StringBuilder jpql = new StringBuilder("SELECT c FROM CharacterCard c LEFT JOIN FETCH c.creator WHERE c.isPublic = true");
        if (keyword != null) {
            appendKeywordCondition(jpql, "c");
        }
        if (cursorData != null && cursorData.getCreatedAt() != null && cursorData.getCardId() != null) {
            jpql.append(" AND (c.createdAt < :cursorCreated OR (c.createdAt = :cursorCreated AND c.id < :cursorCardId))");
        }
        jpql.append(" ORDER BY c.createdAt DESC, c.id DESC");

        TypedQuery<CharacterCard> query = entityManager.createQuery(jpql.toString(), CharacterCard.class);
        if (keyword != null) {
            query.setParameter("keyword", buildKeywordPattern(keyword));
        }
        if (cursorData != null && cursorData.getCreatedAt() != null && cursorData.getCardId() != null) {
            query.setParameter("cursorCreated", cursorData.getCreatedAt());
            query.setParameter("cursorCardId", cursorData.getCardId());
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private List<CharacterCard> fetchMyCards(UUID creatorId, String keyword, MarketCursorData cursorData, int limit) {
        StringBuilder jpql = new StringBuilder("SELECT c FROM CharacterCard c LEFT JOIN FETCH c.creator WHERE c.creatorId = :creatorId");
        if (keyword != null) {
            appendKeywordCondition(jpql, "c");
        }
        if (cursorData != null && cursorData.getCreatedAt() != null && cursorData.getCardId() != null) {
            jpql.append(" AND (c.createdAt < :cursorCreated OR (c.createdAt = :cursorCreated AND c.id < :cursorCardId))");
        }
        jpql.append(" ORDER BY c.createdAt DESC, c.id DESC");

        TypedQuery<CharacterCard> query = entityManager.createQuery(jpql.toString(), CharacterCard.class);
        query.setParameter("creatorId", creatorId);
        if (keyword != null) {
            query.setParameter("keyword", buildKeywordPattern(keyword));
        }
        if (cursorData != null && cursorData.getCreatedAt() != null && cursorData.getCardId() != null) {
            query.setParameter("cursorCreated", cursorData.getCreatedAt());
            query.setParameter("cursorCardId", cursorData.getCardId());
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private List<UserLike> fetchLikedCards(UUID userId, String keyword, MarketCursorData cursorData, int limit) {
        StringBuilder jpql = new StringBuilder("SELECT ul FROM UserLike ul JOIN FETCH ul.characterCard c LEFT JOIN FETCH c.creator " +
                "WHERE ul.userId = :userId AND (c.isPublic = true OR c.creatorId = :userId)");
        if (keyword != null) {
            appendKeywordCondition(jpql, "c");
        }
        if (cursorData != null && cursorData.getLikeCreatedAt() != null && cursorData.getCardId() != null) {
            jpql.append(" AND (ul.createdAt < :cursorLikedAt OR (ul.createdAt = :cursorLikedAt AND c.id < :cursorCardId))");
        }
        jpql.append(" ORDER BY ul.createdAt DESC, c.id DESC");

        TypedQuery<UserLike> query = entityManager.createQuery(jpql.toString(), UserLike.class);
        query.setParameter("userId", userId);
        if (keyword != null) {
            query.setParameter("keyword", buildKeywordPattern(keyword));
        }
        if (cursorData != null && cursorData.getLikeCreatedAt() != null && cursorData.getCardId() != null) {
            query.setParameter("cursorLikedAt", cursorData.getLikeCreatedAt());
            query.setParameter("cursorCardId", cursorData.getCardId());
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private void appendKeywordCondition(StringBuilder jpql, String alias) {
        jpql.append(" AND (LOWER(").append(alias).append(".name) LIKE :keyword " +
                "OR LOWER(").append(alias).append(".shortDescription) LIKE :keyword " +
                "OR LOWER(").append(alias).append(".greetingMessage) LIKE :keyword)");
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildKeywordPattern(String keyword) {
        return "%" + keyword + "%";
    }

    private MarketCursorData parseCursor(CharacterCardMarketFilter filter, String cursor) {
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
                case POPULAR, PUBLIC -> {
                    if (parts.length < 4) {
                        yield null;
                    }
                    Integer likes = Integer.parseInt(parts[1]);
                    OffsetDateTime created = OffsetDateTime.parse(parts[2]);
                    UUID cardId = UUID.fromString(parts[3]);
                    yield MarketCursorData.byPopularity(likes, created, cardId);
                }
                case LATEST, MY -> {
                    if (parts.length < 3) {
                        yield null;
                    }
                    OffsetDateTime created = OffsetDateTime.parse(parts[1]);
                    UUID cardId = UUID.fromString(parts[2]);
                    yield MarketCursorData.byCreatedAt(created, cardId);
                }
                case LIKED -> {
                    if (parts.length < 3) {
                        yield null;
                    }
                    OffsetDateTime likedAt = OffsetDateTime.parse(parts[1]);
                    UUID cardId = UUID.fromString(parts[2]);
                    yield MarketCursorData.byLikedAt(likedAt, cardId);
                }
            };
        } catch (Exception ex) {
            log.warn("解析游标失败: filter={}, cursor={}", filter, cursor, ex);
            return null;
        }
    }

    private String encodeCursor(CharacterCardMarketFilter filter, MarketCursorData cursorData) {
        if (cursorData == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add(filter.name());
        switch (filter) {
            case POPULAR, PUBLIC -> {
                if (cursorData.getLikesCount() == null || cursorData.getCreatedAt() == null || cursorData.getCardId() == null) {
                    return null;
                }
                parts.add(String.valueOf(cursorData.getLikesCount()));
                parts.add(cursorData.getCreatedAt().toString());
                parts.add(cursorData.getCardId().toString());
            }
            case LATEST, MY -> {
                if (cursorData.getCreatedAt() == null || cursorData.getCardId() == null) {
                    return null;
                }
                parts.add(cursorData.getCreatedAt().toString());
                parts.add(cursorData.getCardId().toString());
            }
            case LIKED -> {
                if (cursorData.getLikeCreatedAt() == null || cursorData.getCardId() == null) {
                    return null;
                }
                parts.add(cursorData.getLikeCreatedAt().toString());
                parts.add(cursorData.getCardId().toString());
            }
        }
        String rawCursor = String.join("|", parts);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
    }

    private static class MarketCursorData {
        private final Integer likesCount;
        private final OffsetDateTime createdAt;
        private final UUID cardId;
        private final OffsetDateTime likeCreatedAt;

        private MarketCursorData(Integer likesCount, OffsetDateTime createdAt, UUID cardId, OffsetDateTime likeCreatedAt) {
            this.likesCount = likesCount;
            this.createdAt = createdAt;
            this.cardId = cardId;
            this.likeCreatedAt = likeCreatedAt;
        }

        static MarketCursorData byPopularity(Integer likesCount, OffsetDateTime createdAt, UUID cardId) {
            return new MarketCursorData(likesCount, createdAt, cardId, null);
        }

        static MarketCursorData byCreatedAt(OffsetDateTime createdAt, UUID cardId) {
            return new MarketCursorData(null, createdAt, cardId, null);
        }

        static MarketCursorData byLikedAt(OffsetDateTime likedAt, UUID cardId) {
            return new MarketCursorData(null, null, cardId, likedAt);
        }

        Integer getLikesCount() {
            return likesCount;
        }

        OffsetDateTime getCreatedAt() {
            return createdAt;
        }

        UUID getCardId() {
            return cardId;
        }

        OffsetDateTime getLikeCreatedAt() {
            return likeCreatedAt;
        }
    }

    /**
     * 获取公开角色卡列表
     */
    @Transactional(readOnly = true)
    public Page<CharacterCardResponse> getPublicCharacterCards(Pageable pageable, UUID currentUserId) {
        Page<CharacterCard> cards = characterCardRepository.findByIsPublicTrueOrderByLikesCountDescCreatedAtDesc(pageable);
        return cards.map(card -> {
            boolean isLiked = currentUserId != null && 
                    userLikeRepository.existsByUserIdAndCardId(currentUserId, card.getId());
            return CharacterCardResponse.fromEntity(card, isLiked);
        });
    }

    /**
     * 获取用户创建的角色卡列表
     */
    @Transactional(readOnly = true)
    public Page<CharacterCardResponse> getUserCharacterCards(UUID creatorId, Pageable pageable, UUID currentUserId) {
        Page<CharacterCard> cards = characterCardRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId, pageable);
        return cards.map(card -> {
            boolean isLiked = currentUserId != null && 
                    userLikeRepository.existsByUserIdAndCardId(currentUserId, card.getId());
            return CharacterCardResponse.fromEntity(card, isLiked);
        });
    }

    /**
     * 搜索公开角色卡
     */
    @Transactional(readOnly = true)
    public Page<CharacterCardResponse> searchPublicCharacterCards(String keyword, Pageable pageable, UUID currentUserId) {
        Page<CharacterCard> cards = characterCardRepository.searchPublicCardsByKeyword(keyword, pageable);
        return cards.map(card -> {
            boolean isLiked = currentUserId != null && 
                    userLikeRepository.existsByUserIdAndCardId(currentUserId, card.getId());
            return CharacterCardResponse.fromEntity(card, isLiked);
        });
    }

    /**
     * 获取热门角色卡
     */
    @Transactional(readOnly = true)
    public Page<CharacterCardResponse> getPopularCharacterCards(Pageable pageable, UUID currentUserId) {
        Page<CharacterCard> cards = characterCardRepository.findPopularCards(pageable);
        return cards.map(card -> {
            boolean isLiked = currentUserId != null && 
                    userLikeRepository.existsByUserIdAndCardId(currentUserId, card.getId());
            return CharacterCardResponse.fromEntity(card, isLiked);
        });
    }

    /**
     * 获取最新角色卡
     */
    @Transactional(readOnly = true)
    public Page<CharacterCardResponse> getLatestCharacterCards(Pageable pageable, UUID currentUserId) {
        Page<CharacterCard> cards = characterCardRepository.findLatestCards(pageable);
        return cards.map(card -> {
            boolean isLiked = currentUserId != null && 
                    userLikeRepository.existsByUserIdAndCardId(currentUserId, card.getId());
            return CharacterCardResponse.fromEntity(card, isLiked);
        });
    }

    /**
     * 点赞/取消点赞角色卡
     */
    @Transactional
    public LikeResponse toggleLike(UUID cardId, UUID userId) {
        log.info("切换点赞状态: 角色卡={}, 用户={}", cardId, userId);

        // 检查角色卡是否存在且可访问
        CharacterCard card = characterCardRepository.findAccessibleCard(cardId, userId)
                .orElseThrow(() -> new BusinessException(404, "角色卡不存在或无权限访问"));

        log.info("当前角色卡点赞数: {}", card.getLikesCount());
        
        boolean isLiked = userLikeRepository.existsByUserIdAndCardId(userId, cardId);
        log.info("用户当前点赞状态: {}", isLiked);

        if (isLiked) {
            // 取消点赞
            userLikeRepository.deleteByUserIdAndCardId(userId, cardId);
            log.info("删除点赞记录完成");
        } else {
            // 添加点赞
            UserLike like = UserLike.builder()
                    .userId(userId)
                    .cardId(cardId)
                    .build();
            userLikeRepository.save(like);
            log.info("添加点赞记录完成");
        }

        // 重新计算实际的点赞数，确保数据一致性
        int actualLikesCount = (int) userLikeRepository.countByCardId(cardId);
        log.info("从数据库查询到的实际点赞数: {}", actualLikesCount);
        
        int oldCount = card.getLikesCount();
        card.setLikesCount(actualLikesCount);
        characterCardRepository.save(card);
        
        log.info("更新角色卡点赞数: 从{}更新为{}", oldCount, actualLikesCount);
        
        boolean newLikedStatus = !isLiked;
        LikeResponse response = LikeResponse.builder()
                .isLiked(newLikedStatus)
                .likesCount(actualLikesCount)
                .build();
        
        log.info("返回响应: isLiked={}, likesCount={}", response.isLiked(), response.getLikesCount());
        return response;
    }

    /**
     * 获取用户点赞的角色卡列表
     */
    @Transactional(readOnly = true)
    public Page<CharacterCardResponse> getUserLikedCards(UUID userId, Pageable pageable) {
        Page<CharacterCard> cards = characterCardRepository.findLikedCardsByUser(userId, pageable);
        return cards.map(card -> CharacterCardResponse.fromEntity(card, true)); // 都是点赞的
    }

    /**
     * 将TTS voice数据库ID转换为reference_id
     */
    private String convertVoiceIdToReferenceId(String voiceIdStr) {
        if (voiceIdStr == null || voiceIdStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            Long voiceId = Long.parseLong(voiceIdStr.trim());
            return ttsVoiceRepository.findById(voiceId)
                    .map(TTSVoice::getReferenceId)
                    .orElseThrow(() -> new BusinessException("TTS音色不存在: " + voiceId));
        } catch (NumberFormatException e) {
            throw new BusinessException("TTS音色ID格式错误: " + voiceIdStr);
        }
    }
}
