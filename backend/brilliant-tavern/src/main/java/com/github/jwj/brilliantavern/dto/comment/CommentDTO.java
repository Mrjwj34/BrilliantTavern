package com.github.jwj.brilliantavern.dto.comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 评论DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentDTO {
    
    private Long id;
    private UUID cardId;
    private UUID authorId;
    private String authorName;
    private String authorAvatar;
    private String content;
    private Integer likesCount;
    private Boolean isPinned;
    private Boolean isLikedByCurrentUser;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime pinnedAt;
    
    // 回复相关
    private Long parentCommentId;
    private List<CommentDTO> replies;
    private Integer repliesCount;
    
    // 权限相关
    private Boolean canPin; // 是否可以置顶（角色卡作者）
    private Boolean canEdit; // 是否可以编辑（评论作者）
    private Boolean canDelete; // 是否可以删除（评论作者或角色卡作者）
}