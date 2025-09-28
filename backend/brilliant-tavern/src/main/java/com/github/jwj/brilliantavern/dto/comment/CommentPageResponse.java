package com.github.jwj.brilliantavern.dto.comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评论分页响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentPageResponse {
    
    /**
     * 评论列表
     */
    private List<CommentDTO> comments;
    
    /**
     * 总评论数（不包括回复）
     */
    private Long totalCount;
    
    /**
     * 当前页评论数
     */
    private Integer currentPageSize;
    
    /**
     * 是否还有更多
     */
    private Boolean hasMore;
    
    /**
     * 下一页的游标ID
     */
    private Long nextCursor;
}