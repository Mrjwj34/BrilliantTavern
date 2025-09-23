package com.github.jwj.brilliantavern.dto;

import com.github.jwj.brilliantavern.entity.CharacterCard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 角色卡响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterCardResponse {

    private UUID id;
    private UUID creatorId;
    private String creatorUsername;
    private String name;
    private String shortDescription;
    private String greetingMessage;
    private Boolean isPublic;
    private Integer likesCount;
    private String ttsVoiceId;
    private CharacterCardDataDto cardData;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean isLikedByCurrentUser; // 当前用户是否已点赞

    /**
     * 角色卡数据DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterCardDataDto {
        private String description;
        private String personality;
        private String scenario;
        private ExampleDialogDto[] exampleDialogs;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ExampleDialogDto {
            private String user;
            private String assistant;
        }
    }

    /**
     * 从实体对象转换
     */
    public static CharacterCardResponse fromEntity(CharacterCard card) {
        return fromEntity(card, false);
    }

    /**
     * 从实体对象转换
     */
    public static CharacterCardResponse fromEntity(CharacterCard card, Boolean isLikedByCurrentUser) {
        CharacterCardResponseBuilder builder = CharacterCardResponse.builder()
                .id(card.getId())
                .creatorId(card.getCreatorId())
                .name(card.getName())
                .shortDescription(card.getShortDescription())
                .greetingMessage(card.getGreetingMessage())
                .isPublic(card.getIsPublic())
                .likesCount(card.getLikesCount())
                .ttsVoiceId(card.getTtsVoiceId())
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .isLikedByCurrentUser(isLikedByCurrentUser);

        // 设置创建者用户名
        if (card.getCreator() != null) {
            builder.creatorUsername(card.getCreator().getUsername());
        }

        // 转换卡片数据
        if (card.getCardData() != null) {
            builder.cardData(convertCardData(card.getCardData()));
        }

        return builder.build();
    }

    /**
     * 转换卡片数据
     */
    private static CharacterCardDataDto convertCardData(CharacterCard.CharacterCardData cardData) {
        CharacterCardDataDto.CharacterCardDataDtoBuilder builder = CharacterCardDataDto.builder()
                .description(cardData.getDescription())
                .personality(cardData.getPersonality())
                .scenario(cardData.getScenario());

        // 转换示例对话
        if (cardData.getExampleDialogs() != null) {
            CharacterCardDataDto.ExampleDialogDto[] dialogs = new CharacterCardDataDto.ExampleDialogDto[cardData.getExampleDialogs().length];
            for (int i = 0; i < cardData.getExampleDialogs().length; i++) {
                CharacterCard.CharacterCardData.ExampleDialog dialog = cardData.getExampleDialogs()[i];
                dialogs[i] = CharacterCardDataDto.ExampleDialogDto.builder()
                        .user(dialog.getUser())
                        .assistant(dialog.getAssistant())
                        .build();
            }
            builder.exampleDialogs(dialogs);
        }

        return builder.build();
    }
}
