package com.github.jwj.brilliantavern.repository;

import com.github.jwj.brilliantavern.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 评论点赞Repository
 */
@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLike.CommentLikeId> {
    
    /**
     * 查找用户是否点赞了指定评论
     */
    Optional<CommentLike> findByUserIdAndCommentId(UUID userId, Long commentId);
    
    /**
     * 查找用户点赞的所有评论ID（批量查询）
     */
    @Query("SELECT cl.commentId FROM CommentLike cl WHERE cl.userId = :userId AND cl.commentId IN :commentIds")
    List<Long> findLikedCommentIdsByUserAndComments(@Param("userId") UUID userId, @Param("commentIds") List<Long> commentIds);
    
    /**
     * 统计评论的点赞数
     */
    Long countByCommentId(Long commentId);
    
    /**
     * 删除用户对评论的点赞
     */
    void deleteByUserIdAndCommentId(UUID userId, Long commentId);
    
    /**
     * 检查用户是否点赞了评论
     */
    boolean existsByUserIdAndCommentId(UUID userId, Long commentId);
}