package com.github.jwj.brilliantavern.dto;

import com.github.jwj.brilliantavern.entity.CharacterCard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建角色卡请求DTO
 */
@Schema(description = "创建角色卡请求")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCharacterCardRequest {

    @Schema(description = "角色名称", example = "苏格拉底")
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称不能超过100个字符")
    private String name;

    @Schema(description = "简短描述", example = "古希腊哲学家，智慧的化身")
    @NotBlank(message = "简短描述不能为空")
    @Size(max = 500, message = "简短描述不能超过500个字符")
    private String shortDescription;

    @Schema(description = "问候语", example = "你好，我是苏格拉底。让我们一起探讨智慧吧！")
    @Size(max = 1000, message = "问候语不能超过1000个字符")
    private String greetingMessage;

    @Schema(description = "是否公开", example = "true")
    @Builder.Default
    private Boolean isPublic = false;

    //TODO: 这里前端传错id了
    @Schema(description = "TTS音色ID", example = "zh-CN-XiaoxiaoNeural")
    @Size(max = 100, message = "TTS音色ID不能超过100个字符")
    private String ttsVoiceId;

    @Schema(description = "角色头像URL", example = "https://example.com/avatar.jpg")
    @Size(max = 500, message = "头像URL不能超过500个字符")
    private String avatarUrl;

    @Schema(description = "角色卡详细数据")
    @Valid
    @NotNull(message = "角色卡数据不能为空")
    private CharacterCardDataDto cardData;

    /**
     * 角色卡数据DTO
     */
    @Schema(description = "角色卡详细数据")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterCardDataDto {

        @Schema(description = "角色描述", example = "苏格拉底是古希腊著名的哲学家...")
        @NotBlank(message = "角色描述不能为空")
        @Size(max = 5000, message = "角色描述不能超过5000个字符")
        private String description;

        @Schema(description = "角色性格", example = "聪明、好奇、有点固执、喜欢用反问来引导对话")
        @NotBlank(message = "角色性格不能为空")
        @Size(max = 2000, message = "角色性格不能超过2000个字符")
        private String personality;

        @Schema(description = "角色场景", example = "你正在雅典的市集上与苏格拉底相遇...")
        @Size(max = 3000, message = "角色场景不能超过3000个字符")
        private String scenario;

        @Schema(description = "示例对话")
        @Valid
        private ExampleDialogDto[] exampleDialogs;

        @Schema(description = "示例对话")
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ExampleDialogDto {
            @Schema(description = "用户对话示例", example = "什么是正义？")
            @NotBlank(message = "用户对话示例不能为空")
            @Size(max = 1000, message = "用户对话示例不能超过1000个字符")
            private String user;

            @Schema(description = "助手对话示例", example = "这是一个很好的问题。那么，在你看来，一个正义的行为具体是什么样的呢？")
            @NotBlank(message = "助手对话示例不能为空")
            @Size(max = 1000, message = "助手对话示例不能超过1000个字符")
            private String assistant;
        }
    }

    /**
     * 转换为实体对象
     */
    public CharacterCard.CharacterCardData toCardData() {
        CharacterCard.CharacterCardData.CharacterCardDataBuilder builder = CharacterCard.CharacterCardData.builder()
                .description(cardData.getDescription())
                .personality(cardData.getPersonality())
                .scenario(cardData.getScenario());

        // 转换示例对话
        if (cardData.getExampleDialogs() != null) {
            CharacterCard.CharacterCardData.ExampleDialog[] dialogs = new CharacterCard.CharacterCardData.ExampleDialog[cardData.getExampleDialogs().length];
            for (int i = 0; i < cardData.getExampleDialogs().length; i++) {
                CharacterCardDataDto.ExampleDialogDto dto = cardData.getExampleDialogs()[i];
                dialogs[i] = CharacterCard.CharacterCardData.ExampleDialog.builder()
                        .user(dto.getUser())
                        .assistant(dto.getAssistant())
                        .build();
            }
            builder.exampleDialogs(dialogs);
        }

        return builder.build();
    }
}
