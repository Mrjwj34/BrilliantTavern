package com.github.jwj.brilliantavern.dto;

import com.github.jwj.brilliantavern.entity.CharacterCard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新角色卡请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCharacterCardRequest {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "简短描述不能超过500个字符")
    private String shortDescription;

    @Size(max = 1000, message = "问候语不能超过1000个字符")
    private String greetingMessage;

    private Boolean isPublic;

    @Size(max = 100, message = "TTS音色ID不能超过100个字符")
    private String ttsVoiceId;

    @Valid
    private CharacterCardDataDto cardData;

    /**
     * 角色卡数据DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterCardDataDto {

        @Size(max = 5000, message = "角色描述不能超过5000个字符")
        private String description;

        @Size(max = 2000, message = "角色性格不能超过2000个字符")
        private String personality;

        @Size(max = 3000, message = "角色场景不能超过3000个字符")
        private String scenario;

        @Valid
        private ExampleDialogDto[] exampleDialogs;

        @Valid
        private CustomPromptsDto customPrompts;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ExampleDialogDto {
            @Size(max = 1000, message = "用户对话示例不能超过1000个字符")
            private String user;

            @Size(max = 1000, message = "助手对话示例不能超过1000个字符")
            private String assistant;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CustomPromptsDto {
            @Size(max = 2000, message = "系统提示前缀不能超过2000个字符")
            private String systemPromptPrefix;

            @Size(max = 2000, message = "系统提示后缀不能超过2000个字符")
            private String systemPromptSuffix;
        }
    }

    /**
     * 转换为实体对象的卡片数据
     */
    public CharacterCard.CharacterCardData toCardData() {
        if (cardData == null) {
            return null;
        }

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

        // 转换自定义提示
        if (cardData.getCustomPrompts() != null) {
            builder.customPrompts(CharacterCard.CharacterCardData.CustomPrompts.builder()
                    .systemPromptPrefix(cardData.getCustomPrompts().getSystemPromptPrefix())
                    .systemPromptSuffix(cardData.getCustomPrompts().getSystemPromptSuffix())
                    .build());
        }

        return builder.build();
    }
}
