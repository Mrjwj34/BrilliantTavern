package com.github.jwj.brilliantavern.entity.converter;

import com.github.jwj.brilliantavern.entity.ChatHistory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 在数据库中使用小写枚举值存储/读取聊天角色，适配已有 check 约束。
 */
@Converter(autoApply = true)
public class ChatHistoryRoleConverter implements AttributeConverter<ChatHistory.Role, String> {

    @Override
    public String convertToDatabaseColumn(ChatHistory.Role attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ChatHistory.Role convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return null;
        }
        return ChatHistory.Role.valueOf(dbData.trim().toUpperCase(Locale.ROOT));
    }
}
