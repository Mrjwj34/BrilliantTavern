package com.github.jwj.brilliantavern.repository;

import com.github.jwj.brilliantavern.entity.CharacterCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 角色卡数据访问层
 */
@Repository
public interface CharacterCardRepository extends JpaRepository<CharacterCard, UUID> {

    /**
     * 根据创建者ID查找角色卡
     */
    Page<CharacterCard> findByCreatorIdOrderByCreatedAtDesc(UUID creatorId, Pageable pageable);

    /**
     * 查找公开的角色卡
     */
    Page<CharacterCard> findByIsPublicTrueOrderByLikesCountDescCreatedAtDesc(Pageable pageable);

    /**
     * 根据名称模糊搜索公开的角色卡
     */
    @Query("SELECT c FROM CharacterCard c WHERE c.isPublic = true AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY c.likesCount DESC, c.createdAt DESC")
    Page<CharacterCard> searchPublicCardsByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 查找热门角色卡（按点赞数排序）
     */
    @Query("SELECT c FROM CharacterCard c WHERE c.isPublic = true " +
           "ORDER BY c.likesCount DESC, c.createdAt DESC")
    Page<CharacterCard> findPopularCards(Pageable pageable);

    /**
     * 查找最新角色卡
     */
    @Query("SELECT c FROM CharacterCard c WHERE c.isPublic = true " +
           "ORDER BY c.createdAt DESC")
    Page<CharacterCard> findLatestCards(Pageable pageable);

    /**
     * 根据ID查找角色卡并加载创建者信息
     */
    @Query("SELECT c FROM CharacterCard c LEFT JOIN FETCH c.creator WHERE c.id = :id")
    Optional<CharacterCard> findByIdWithCreator(@Param("id") UUID id);

    /**
     * 统计用户创建的角色卡数量
     */
    long countByCreatorId(UUID creatorId);

    /**
     * 统计公开角色卡数量
     */
    long countByIsPublicTrue();

    /**
     * 检查用户是否有权限访问角色卡
     */
    @Query("SELECT c FROM CharacterCard c WHERE c.id = :cardId AND " +
           "(c.isPublic = true OR c.creatorId = :userId)")
    Optional<CharacterCard> findAccessibleCard(@Param("cardId") UUID cardId, @Param("userId") UUID userId);

    /**
     * 获取用户收藏（点赞）的角色卡
     */
    @Query("SELECT c FROM CharacterCard c INNER JOIN UserLike ul ON c.id = ul.cardId " +
           "WHERE ul.userId = :userId ORDER BY ul.createdAt DESC")
    Page<CharacterCard> findLikedCardsByUser(@Param("userId") UUID userId, Pageable pageable);
}
