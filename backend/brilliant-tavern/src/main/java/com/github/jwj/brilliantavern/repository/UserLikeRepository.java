package com.github.jwj.brilliantavern.repository;

import com.github.jwj.brilliantavern.entity.UserLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户点赞数据访问层
 */
@Repository
public interface UserLikeRepository extends JpaRepository<UserLike, UserLike.UserLikeId> {

    /**
     * 检查用户是否已点赞某个角色卡
     */
    boolean existsByUserIdAndCardId(UUID userId, UUID cardId);

    /**
     * 根据用户ID和角色卡ID查找点赞记录
     */
    Optional<UserLike> findByUserIdAndCardId(UUID userId, UUID cardId);

    /**
     * 根据用户ID查找所有点赞记录
     */
    List<UserLike> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * 根据角色卡ID查找所有点赞记录
     */
    List<UserLike> findByCardIdOrderByCreatedAtDesc(UUID cardId);

    /**
     * 统计角色卡的点赞数
     */
    long countByCardId(UUID cardId);

    /**
     * 统计用户的点赞数
     */
    long countByUserId(UUID userId);

    /**
     * 删除用户对角色卡的点赞
     */
    void deleteByUserIdAndCardId(UUID userId, UUID cardId);
}
