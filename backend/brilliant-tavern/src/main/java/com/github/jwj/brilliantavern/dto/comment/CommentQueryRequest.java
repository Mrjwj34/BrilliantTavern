package com.github.jwj.brilliantavern.dto.comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 评论查询请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentQueryRequest {
    
    private UUID cardId;
    @Builder.Default
    private String sortBy = "created_at"; // created_at, likes_count
    @Builder.Default
    private String sortOrder = "desc"; // asc, desc
    @Builder.Default
    private Integer page = 0;
    @Builder.Default
    private Integer size = 20;
    private Long cursor; // 用于无缝分页的游标
}