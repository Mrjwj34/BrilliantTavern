package com.github.jwj.brilliantavern.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 创建评论请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentRequest {
    
    @NotNull(message = "角色卡ID不能为空")
    private UUID cardId;
    
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容不能超过1000个字符")
    private String content;
    
    private Long parentCommentId; // 可选，用于回复
}