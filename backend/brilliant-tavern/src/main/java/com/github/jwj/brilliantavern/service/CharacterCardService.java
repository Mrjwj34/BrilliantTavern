package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.dto.CharacterCardResponse;
import com.github.jwj.brilliantavern.dto.CreateCharacterCardRequest;
import com.github.jwj.brilliantavern.dto.LikeResponse;
import com.github.jwj.brilliantavern.dto.UpdateCharacterCardRequest;
import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.github.jwj.brilliantavern.entity.UserLike;
import com.github.jwj.brilliantavern.exception.BusinessException;
import com.github.jwj.brilliantavern.repository.CharacterCardRepository;
import com.github.jwj.brilliantavern.repository.UserLikeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 角色卡服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterCardService {

    private final CharacterCardRepository characterCardRepository;
    private final UserLikeRepository userLikeRepository;

    /**
     * 创建角色卡
     */
    @Transactional
    public CharacterCardResponse createCharacterCard(UUID creatorId, CreateCharacterCardRequest request) {
        log.info("创建角色卡: 用户={}, 角色名称={}", creatorId, request.getName());

        CharacterCard card = CharacterCard.builder()
                .creatorId(creatorId)
                .name(request.getName())
                .shortDescription(request.getShortDescription())
                .greetingMessage(request.getGreetingMessage())
                .isPublic(request.getIsPublic())
                .ttsVoiceId(request.getTtsVoiceId())
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
            card.setTtsVoiceId(request.getTtsVoiceId());
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
}
