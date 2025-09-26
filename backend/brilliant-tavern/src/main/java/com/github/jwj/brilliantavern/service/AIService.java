package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.github.jwj.brilliantavern.dto.VoiceMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI服务类，负责与Spring AI集成处理语音对话
 * 使用ChatMemory管理对话历史，支持音频转写功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    
    @Value("classpath:prompts/character-chat-template.st")
    private Resource promptTemplate;
    
    // ASR转写结果的正则表达式
    private static final Pattern ASR_PATTERN = Pattern.compile("\\[ASR_TRANSCRIPTION](.*?)\\[/ASR_TRANSCRIPTION]");

    /**
     * 处理语音消息并返回包含分段的AI流式事件。
     */
    public Flux<AIStreamEvent> streamVoiceConversation(VoiceMessage voiceMessage,
                                                       CharacterCard characterCard,
                                                       String conversationId,
                                                       String messageId) {
        return Flux.defer(() -> {
            try {
                String systemPrompt = buildSystemPrompt(characterCard);
                List<org.springframework.ai.chat.messages.Message> historyMessages = chatMemory.get(conversationId);

                ByteArrayResource audioResource = new ByteArrayResource(voiceMessage.getAudioData());
                MimeType audioMimeType = getAudioMimeType(voiceMessage.getAudioFormat());

                StringBuilder fullResponse = new StringBuilder();
                ChatClient chatClient = ChatClient.create(chatModel);

                return chatClient.prompt()
                        .system(systemPrompt)
                        .messages(historyMessages)
                        .user(userSpec -> userSpec.media(audioMimeType, audioResource))
                        .stream()
                        .content()
                        .doOnNext(content -> {
                            fullResponse.append(content);
                            log.debug("AI回复片段: {}", content);
                        })
                        .map(content -> AIStreamEvent.chunk(messageId, content))
                        .concatWith(Mono.fromCallable(() -> {
                            ProcessedAiResponse processed = processCompleteResponse(fullResponse.toString(), conversationId, "音频消息");
                            return AIStreamEvent.completed(messageId, processed);
                        }))
                        .doOnError(error -> log.error("AI处理语音消息失败", error));
            } catch (Exception e) {
                log.error("处理语音消息失败", e);
                return Flux.<AIStreamEvent>error(e);
            }
        });
    }
    
    /**
     * 根据音频格式获取对应的MimeType
     */
    private MimeType getAudioMimeType(String audioFormat) {
        if (audioFormat == null) {
            return MimeTypeUtils.APPLICATION_OCTET_STREAM;
        }
        
        return switch (audioFormat.toLowerCase()) {
            case "wav" -> MimeType.valueOf("audio/wav");
            case "mp3" -> MimeType.valueOf("audio/mpeg");
            case "webm" -> MimeType.valueOf("audio/webm");
            case "ogg" -> MimeType.valueOf("audio/ogg");
            case "m4a" -> MimeType.valueOf("audio/m4a");
            default -> MimeTypeUtils.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * 构建系统提示词，使用模板
     */
    public String buildSystemPrompt(CharacterCard characterCard) {
        try {
            String template = promptTemplate.getContentAsString(StandardCharsets.UTF_8);
            
            PromptTemplate promptTemplate = new PromptTemplate(template);
            Map<String, Object> variables = Map.of(
                "character_name", characterCard.getName(),
                "character_description", getCharacterDescription(characterCard),
                "character_personality", getCharacterPersonality(characterCard),
                "character_style", getCharacterStyle(characterCard)
            );
            
            return promptTemplate.render(variables);
            
        } catch (IOException e) {
            log.error("读取提示词模板失败", e);
            throw new RuntimeException("无法构建系统提示词", e);
        }
    }
    
    /**
     * 获取角色描述
     */
    private String getCharacterDescription(CharacterCard characterCard) {
        if (characterCard.getCardData() != null && characterCard.getCardData().getDescription() != null) {
            return characterCard.getCardData().getDescription();
        }
        return characterCard.getShortDescription() != null ? characterCard.getShortDescription() : "一个有趣的角色";
    }
    
    /**
     * 获取角色性格
     */
    private String getCharacterPersonality(CharacterCard characterCard) {
        if (characterCard.getCardData() != null && characterCard.getCardData().getPersonality() != null) {
            return characterCard.getCardData().getPersonality();
        }
        return "友好、活泼";
    }
    
    /**
     * 获取角色说话风格
     */
    private String getCharacterStyle(CharacterCard characterCard) {
        if (characterCard.getCardData() != null && characterCard.getCardData().getScenario() != null) {
            return "在" + characterCard.getCardData().getScenario() + "场景中自然对话";
        }
        return "自然、亲切";
    }

    /**
     * 处理完整回复，提取ASR转写并更新对话历史
     */
    private ProcessedAiResponse processCompleteResponse(String completeResponse,
                                                        String conversationId,
                                                        String userTranscriptionFallback) {
        try {
            // 提取ASR转写内容
            Matcher matcher = ASR_PATTERN.matcher(completeResponse);
            String userTranscription = userTranscriptionFallback; // 使用回退值
            String aiResponse = completeResponse;
            
            if (matcher.find()) {
                userTranscription = matcher.group(1).trim();
                // 从AI回复中移除ASR标记
                aiResponse = completeResponse.replaceAll("\\[ASR_TRANSCRIPTION].*?\\[/ASR_TRANSCRIPTION]", "").trim();
                
                log.debug("提取到用户转写内容: {}", userTranscription);
            }
            
            // 更新对话历史
            if (!userTranscription.isEmpty()) {
                // 添加用户消息（使用转写的文本）
                chatMemory.add(conversationId, new UserMessage(userTranscription));
            }
            
            // 添加AI回复（移除ASR标记后的内容）
            chatMemory.add(conversationId, new AssistantMessage(aiResponse));
            
            log.debug("更新对话历史成功，对话ID: {}", conversationId);
            return new ProcessedAiResponse(aiResponse, userTranscription);

        } catch (Exception e) {
            log.error("处理完整回复失败", e);
            return new ProcessedAiResponse(completeResponse, userTranscriptionFallback);
        }
    }

    /**
     * AI流式事件。
     */
    @lombok.Value
    public static class AIStreamEvent {
        public enum Type { CHUNK, COMPLETED }

        Type type;
        String messageId;
        String content;
        ProcessedAiResponse processedResponse;

        public static AIStreamEvent chunk(String messageId, String content) {
            return new AIStreamEvent(Type.CHUNK, messageId, content, null);
        }

        public static AIStreamEvent completed(String messageId, ProcessedAiResponse response) {
            return new AIStreamEvent(Type.COMPLETED, messageId, null, response);
        }
    }

    public record ProcessedAiResponse(String aiResponse, String userTranscription) {}
}
