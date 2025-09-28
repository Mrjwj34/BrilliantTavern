package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.dto.comment.CommentDTO;
import com.github.jwj.brilliantavern.dto.comment.CommentQueryRequest;
import com.github.jwj.brilliantavern.dto.comment.CreateCommentRequest;
import com.github.jwj.brilliantavern.dto.comment.CommentPageResponse;
import com.github.jwj.brilliantavern.entity.User;
import com.github.jwj.brilliantavern.security.UserPrincipal;
import com.github.jwj.brilliantavern.service.CommentService;
import com.github.jwj.brilliantavern.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 评论控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Tag(name = "评论管理", description = "角色卡评论相关API")
public class CommentController {
    
    private final CommentService commentService;
    
    @PostMapping
    @Operation(summary = "创建评论", description = "创建新评论或回复已有评论")
    public ApiResponse<CommentDTO> createComment(
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal UserPrincipal currentUserPrincipal) {
        
        if (currentUserPrincipal == null) {
            throw new IllegalArgumentException("用户未登录，无法创建评论");
        }
        
        // 创建User对象传递给Service层
        User currentUser = User.builder()
                .id(currentUserPrincipal.getId())
                .username(currentUserPrincipal.getUsername())
                .email(currentUserPrincipal.getEmail())
                .build();
        
        log.info("用户 {} 创建评论，角色卡ID: {}, 父评论ID: {}", 
                currentUser.getUsername(), request.getCardId(), request.getParentCommentId());
        
        CommentDTO comment = commentService.createComment(request, currentUser);
        
        return ApiResponse.success(comment);
    }
    
    @GetMapping
    @Operation(summary = "获取评论列表", description = "获取指定角色卡的评论列表，支持分页和排序")
    public ApiResponse<CommentPageResponse> getComments(
            @Parameter(description = "角色卡ID") @RequestParam UUID cardId,
            @Parameter(description = "排序字段") @RequestParam(defaultValue = "created_at") String sortBy,
            @Parameter(description = "排序方向") @RequestParam(defaultValue = "desc") String sortOrder,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "页大小") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "游标ID") @RequestParam(required = false) Long cursor,
            @AuthenticationPrincipal UserPrincipal currentUserPrincipal) {
        
        CommentQueryRequest request = CommentQueryRequest.builder()
                .cardId(cardId)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .page(page)
                .size(size)
                .cursor(cursor)
                .build();
        
        UUID currentUserId = currentUserPrincipal != null ? currentUserPrincipal.getId() : null;
        CommentPageResponse response = commentService.getCommentsWithPagination(request, currentUserId);
        
        return ApiResponse.success(response);
    }
    
    @GetMapping("/{commentId}/replies")
    @Operation(summary = "获取评论回复", description = "获取指定评论的回复列表")
    public ApiResponse<List<CommentDTO>> getCommentReplies(
            @Parameter(description = "评论ID") @PathVariable Long commentId,
            @AuthenticationPrincipal UserPrincipal currentUserPrincipal) {
        
        UUID currentUserId = currentUserPrincipal != null ? currentUserPrincipal.getId() : null;
        List<CommentDTO> replies = commentService.getCommentReplies(commentId, currentUserId);
        
        return ApiResponse.success(replies);
    }
    
    @PostMapping("/{commentId}/like")
    @Operation(summary = "点赞/取消点赞评论", description = "切换评论的点赞状态")
    public ApiResponse<Boolean> toggleCommentLike(
            @Parameter(description = "评论ID") @PathVariable Long commentId,
            @AuthenticationPrincipal UserPrincipal currentUserPrincipal) {
        
        if (currentUserPrincipal == null) {
            throw new IllegalArgumentException("用户未登录，无法点赞评论");
        }
        
        log.info("用户 {} 切换评论 {} 的点赞状态", currentUserPrincipal.getUsername(), commentId);
        
        boolean isLiked = commentService.toggleCommentLike(commentId, currentUserPrincipal.getId());
        
        return ApiResponse.success(isLiked);
    }
    
    @PostMapping("/{commentId}/pin")
    @Operation(summary = "置顶/取消置顶评论", description = "切换评论的置顶状态（仅角色卡作者可操作）")
    public ApiResponse<Void> toggleCommentPin(
            @Parameter(description = "评论ID") @PathVariable Long commentId,
            @AuthenticationPrincipal UserPrincipal currentUserPrincipal) {
        
        if (currentUserPrincipal == null) {
            throw new IllegalArgumentException("用户未登录，无法置顶评论");
        }
        
        log.info("用户 {} 切换评论 {} 的置顶状态", currentUserPrincipal.getUsername(), commentId);
        
        commentService.toggleCommentPin(commentId, currentUserPrincipal.getId());
        
        return ApiResponse.success();
    }
    
    @DeleteMapping("/{commentId}")
    @Operation(summary = "删除评论", description = "删除指定评论（评论作者或角色卡作者可操作）")
    public ApiResponse<Void> deleteComment(
            @Parameter(description = "评论ID") @PathVariable Long commentId,
            @AuthenticationPrincipal UserPrincipal currentUserPrincipal) {
        
        if (currentUserPrincipal == null) {
            throw new IllegalArgumentException("用户未登录，无法删除评论");
        }
        
        log.info("用户 {} 删除评论 {}", currentUserPrincipal.getUsername(), commentId);
        
        commentService.deleteComment(commentId, currentUserPrincipal.getId());
        
        return ApiResponse.success();
    }
}