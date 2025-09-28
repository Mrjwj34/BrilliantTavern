package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.dto.comment.CommentDTO;
import com.github.jwj.brilliantavern.dto.comment.CommentQueryRequest;
import com.github.jwj.brilliantavern.dto.comment.CreateCommentRequest;
import com.github.jwj.brilliantavern.dto.comment.CommentPageResponse;
import com.github.jwj.brilliantavern.entity.CardComment;
import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.github.jwj.brilliantavern.entity.CommentLike;
import com.github.jwj.brilliantavern.entity.User;
import com.github.jwj.brilliantavern.exception.BusinessException;
import com.github.jwj.brilliantavern.repository.CardCommentRepository;
import com.github.jwj.brilliantavern.repository.CharacterCardRepository;
import com.github.jwj.brilliantavern.repository.CommentLikeRepository;
import com.github.jwj.brilliantavern.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 评论服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {
    
    private final CardCommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CharacterCardRepository characterCardRepository;
    private final UserRepository userRepository;
    
    /**
     * 创建评论
     */
    @Transactional
    public CommentDTO createComment(CreateCommentRequest request, User currentUser) {
        // 验证角色卡存在
        CharacterCard card = characterCardRepository.findById(request.getCardId())
                .orElseThrow(() -> new BusinessException("角色卡不存在"));
        
        // 如果是回复，验证父评论存在
        if (request.getParentCommentId() != null) {
            CardComment parentComment = commentRepository.findById(request.getParentCommentId())
                    .orElseThrow(() -> new BusinessException("父评论不存在"));
            
            // 确保父评论属于同一个角色卡
            if (!parentComment.getCardId().equals(request.getCardId())) {
                throw new BusinessException("无法回复其他角色卡的评论");
            }
            
            // 不允许对回复进行回复（避免无限嵌套）
            if (parentComment.getParentCommentId() != null) {
                throw new BusinessException("不能回复回复，请回复主评论");
            }
        }
        
        // 创建评论
        CardComment comment = CardComment.builder()
                .cardId(request.getCardId())
                .authorId(currentUser.getId())
                .content(request.getContent())
                .parentCommentId(request.getParentCommentId())
                .likesCount(0)
                .isPinned(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        
        CardComment savedComment = commentRepository.save(comment);
        
        // 如果是主评论（非回复），则更新角色卡的评论数
        if (request.getParentCommentId() == null) {
            characterCardRepository.incrementCommentsCount(request.getCardId());
            log.info("已更新角色卡 {} 的评论数 +1", request.getCardId());
        }
        
        log.info("创建评论成功: commentId={}, cardId={}, authorId={}, parentId={}", 
                savedComment.getId(), request.getCardId(), currentUser.getId(), request.getParentCommentId());
        
        return convertToDTO(savedComment, currentUser.getId(), card.getCreatorId());
    }
    
    /**
     * 获取角色卡的评论列表（分页）
     */
    public List<CommentDTO> getComments(CommentQueryRequest request, UUID currentUserId) {
        CommentPageResponse response = getCommentsWithPagination(request, currentUserId);
        return response.getComments();
    }
    
    /**
     * 获取角色卡的评论列表（包含分页信息）
     */
    public CommentPageResponse getCommentsWithPagination(CommentQueryRequest request, UUID currentUserId) {
        CharacterCard card = characterCardRepository.findById(request.getCardId())
                .orElseThrow(() -> new BusinessException("角色卡不存在"));
        
        // 获取总评论数
        Long totalCount = commentRepository.countTopLevelCommentsByCardId(request.getCardId());
        
        PageRequest pageRequest = PageRequest.of(request.getPage(), request.getSize());
        
        List<CardComment> allComments = new ArrayList<>();
        boolean hasMore = false;
        
        if (request.getCursor() != null) {
            // 使用游标分页 - 只获取非置顶评论
            CardComment cursorComment = commentRepository.findById(request.getCursor())
                    .orElseThrow(() -> new BusinessException("游标评论不存在"));
            
            log.debug("游标分页查询: cardId={}, cursor={}, cursorCreatedAt={}, sortBy={}, sortOrder={}", 
                    request.getCardId(), request.getCursor(), cursorComment.getCreatedAt(), 
                    request.getSortBy(), request.getSortOrder());
            
            List<CardComment> commentList = commentRepository.findTopLevelCommentsByCursor(
                    request.getCardId(),
                    request.getSortBy(),
                    request.getSortOrder(),
                    request.getCursor(),
                    cursorComment.getLikesCount(),
                    cursorComment.getCreatedAt(),
                    pageRequest
            );
            
            log.debug("游标分页查询结果: 找到 {} 条评论", commentList.size());
            
            allComments.addAll(commentList);
            hasMore = commentList.size() == request.getSize();
        } else {
            // 第一页：先获取置顶评论，再获取普通评论
            Slice<CardComment> normalComments = commentRepository.findTopLevelCommentsByCardId(
                    request.getCardId(),
                    request.getSortBy(),
                    request.getSortOrder(),
                    pageRequest
            );
            
            allComments.addAll(normalComments.getContent());
            hasMore = normalComments.hasNext();
        }
        
        // 创建一个模拟的Slice
        Slice<CardComment> comments = new org.springframework.data.domain.SliceImpl<>(allComments, pageRequest, hasMore);
        
        List<CommentDTO> commentDTOs = convertToDTOList(comments.getContent(), currentUserId, card.getCreatorId());
        
        return CommentPageResponse.builder()
                .comments(commentDTOs)
                .totalCount(totalCount)
                .currentPageSize(commentDTOs.size())
                .hasMore(hasMore)
                .nextCursor(hasMore && !commentDTOs.isEmpty() ? commentDTOs.get(commentDTOs.size() - 1).getId() : null)
                .build();
    }
    
    /**
     * 点赞/取消点赞评论
     */
    @Transactional
    public boolean toggleCommentLike(Long commentId, UUID userId) {
        CardComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        
        boolean isLiked = commentLikeRepository.existsByUserIdAndCommentId(userId, commentId);
        
        if (isLiked) {
            // 取消点赞
            commentLikeRepository.deleteByUserIdAndCommentId(userId, commentId);
            log.info("取消点赞评论: commentId={}, userId={}", commentId, userId);
            return false;
        } else {
            // 点赞
            CommentLike like = CommentLike.builder()
                    .userId(userId)
                    .commentId(commentId)
                    .createdAt(OffsetDateTime.now())
                    .build();
            commentLikeRepository.save(like);
            log.info("点赞评论: commentId={}, userId={}", commentId, userId);
            return true;
        }
    }
    
    /**
     * 置顶/取消置顶评论（仅角色卡作者可操作）
     */
    @Transactional
    public void toggleCommentPin(Long commentId, UUID userId) {
        CardComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        
        CharacterCard card = characterCardRepository.findById(comment.getCardId())
                .orElseThrow(() -> new BusinessException("角色卡不存在"));
        
        // 检查权限：只有角色卡作者可以置顶评论
        if (!card.getCreatorId().equals(userId)) {
            throw new BusinessException("只有角色卡作者可以置顶评论");
        }
        
        // 不能置顶回复评论
        if (comment.getParentCommentId() != null) {
            throw new BusinessException("不能置顶回复评论");
        }
        
        boolean newPinnedStatus = !comment.getIsPinned();
        
        if (newPinnedStatus) {
            // 置顶：先取消其他评论的置顶状态
            commentRepository.unpinOtherComments(comment.getCardId(), commentId);
        }
        
        // 更新当前评论的置顶状态
        commentRepository.updatePinnedStatus(commentId, newPinnedStatus);
        
        log.info("{}评论: commentId={}, cardId={}, operatorId={}", 
                newPinnedStatus ? "置顶" : "取消置顶", commentId, comment.getCardId(), userId);
    }
    
    /**
     * 删除评论
     */
    @Transactional
    public void deleteComment(Long commentId, UUID userId) {
        CardComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        
        CharacterCard card = characterCardRepository.findById(comment.getCardId())
                .orElseThrow(() -> new BusinessException("角色卡不存在"));
        
        // 检查权限：评论作者或角色卡作者可以删除
        if (!comment.getAuthorId().equals(userId) && !card.getCreatorId().equals(userId)) {
            throw new BusinessException("无权删除此评论");
        }
        
        // 如果删除的是主评论（非回复），则更新角色卡的评论数
        boolean isMainComment = comment.getParentCommentId() == null;
        
        // 删除评论（级联删除回复和点赞）
        commentRepository.delete(comment);
        
        // 更新角色卡评论数
        if (isMainComment) {
            characterCardRepository.decrementCommentsCount(comment.getCardId());
            log.info("已更新角色卡 {} 的评论数 -1", comment.getCardId());
        }
        
        log.info("删除评论: commentId={}, cardId={}, operatorId={}", commentId, comment.getCardId(), userId);
    }
    
    /**
     * 获取评论的回复列表
     */
    public List<CommentDTO> getCommentReplies(Long commentId, UUID currentUserId) {
        CardComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("评论不存在"));
        
        CharacterCard card = characterCardRepository.findById(comment.getCardId())
                .orElseThrow(() -> new BusinessException("角色卡不存在"));
        
        List<CardComment> replies = commentRepository.findRepliesByParentId(commentId);
        
        return convertToDTOList(replies, currentUserId, card.getCreatorId());
    }
    
    /**
     * 转换为DTO列表
     */
    private List<CommentDTO> convertToDTOList(List<CardComment> comments, UUID currentUserId, UUID cardCreatorId) {
        if (comments.isEmpty()) {
            return List.of();
        }
        
        // 批量查询用户点赞状态
        List<Long> commentIds = comments.stream().map(CardComment::getId).toList();
        Set<Long> likedCommentIds = currentUserId != null ?
                Set.copyOf(commentLikeRepository.findLikedCommentIdsByUserAndComments(currentUserId, commentIds)) :
                Set.of();
        
        // 批量查询用户信息
        Set<UUID> authorIds = comments.stream().map(CardComment::getAuthorId).collect(Collectors.toSet());
        Map<UUID, User> authorsMap = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        
        // 批量查询回复数量
        Map<Long, Long> repliesCountMap = new HashMap<>();
        if (!commentIds.isEmpty()) {
            List<Object[]> repliesCounts = commentRepository.countRepliesByParentIds(commentIds);
            for (Object[] result : repliesCounts) {
                Long parentId = (Long) result[0];
                Long count = (Long) result[1];
                repliesCountMap.put(parentId, count);
            }
            // 确保所有评论ID都有对应的计数（没有回复的评论设为0）
            for (Long commentId : commentIds) {
                repliesCountMap.putIfAbsent(commentId, 0L);
            }
        }
        
        return comments.stream()
                .map(comment -> convertToDTO(comment, currentUserId, cardCreatorId, 
                        likedCommentIds, authorsMap, repliesCountMap))
                .toList();
    }
    
    /**
     * 转换为DTO
     */
    private CommentDTO convertToDTO(CardComment comment, UUID currentUserId, UUID cardCreatorId) {
        Set<Long> likedCommentIds = currentUserId != null ?
                Set.copyOf(commentLikeRepository.findLikedCommentIdsByUserAndComments(currentUserId, List.of(comment.getId()))) :
                Set.of();
        
        Map<UUID, User> authorsMap = Map.of(comment.getAuthorId(),
                userRepository.findById(comment.getAuthorId()).orElse(null));
        
        Map<Long, Long> repliesCountMap = Map.of(comment.getId(),
                commentRepository.countRepliesByParentId(comment.getId()));
        
        return convertToDTO(comment, currentUserId, cardCreatorId, likedCommentIds, authorsMap, repliesCountMap);
    }
    
    /**
     * 转换为DTO（带缓存数据）
     */
    private CommentDTO convertToDTO(CardComment comment, UUID currentUserId, UUID cardCreatorId,
                                   Set<Long> likedCommentIds, Map<UUID, User> authorsMap, Map<Long, Long> repliesCountMap) {
        User author = authorsMap.get(comment.getAuthorId());
        
        return CommentDTO.builder()
                .id(comment.getId())
                .cardId(comment.getCardId())
                .authorId(comment.getAuthorId())
                .authorName(author != null ? author.getUsername() : "未知用户")
                .authorAvatar(null) // TODO: 如果有用户头像字段，在这里设置
                .content(comment.getContent())
                .likesCount(comment.getLikesCount())
                .isPinned(comment.getIsPinned())
                .isLikedByCurrentUser(likedCommentIds.contains(comment.getId()))
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .pinnedAt(comment.getPinnedAt())
                .parentCommentId(comment.getParentCommentId())
                .repliesCount(repliesCountMap.getOrDefault(comment.getId(), 0L).intValue())
                .canPin(cardCreatorId.equals(currentUserId) && comment.getParentCommentId() == null)
                .canEdit(comment.getAuthorId().equals(currentUserId))
                .canDelete(comment.getAuthorId().equals(currentUserId) || cardCreatorId.equals(currentUserId))
                .build();
    }
}