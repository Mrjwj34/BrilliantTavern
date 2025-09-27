package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.config.GenAIConfig;
import com.github.jwj.brilliantavern.dto.VoiceMessage;
import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.github.jwj.brilliantavern.service.util.AsrMarkupProcessor;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * AI服务类，负责与Google Gen AI集成处理语音对话
 * 使用ChatMemoryService管理对话历史，支持音频转写功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final Client genAIClient;
    private final GenAIConfig genAIConfig;
    private final ChatMemoryService chatMemoryService;
    
    @Value("classpath:prompts/character-chat-template.st")
    private Resource promptTemplate;

    /**
     * 处理语音消息并返回包含分段的AI流式事件。
     */
    public Flux<AIStreamEvent> streamVoiceConversation(VoiceMessage voiceMessage,
                                                       CharacterCard characterCard,
                                                       String conversationId,
                                                       String messageId) {
        return Flux.defer(() -> {
            try {
                if (voiceMessage.getAudioData() == null || voiceMessage.getAudioData().length == 0) {
                    String msg = "接收到空的音频数据，无法生成AI回复";
                    log.warn("{} - conversationId={}, messageId={}", msg, conversationId, messageId);
                    return Flux.error(new IllegalArgumentException(msg));
                }

                String systemPrompt = buildSystemPrompt(characterCard);
                chatMemoryService.limitHistory(conversationId, 40);
                List<Content> historyMessages = chatMemoryService.getHistory(conversationId);

                log.debug("调用AI开始: conversationId={}, messageId={}, historySize={}, audioBytes={}",
                        conversationId, messageId, historyMessages.size(), voiceMessage.getAudioData().length);

                // 设置系统指令到历史消息开头
                if (StringUtils.hasText(systemPrompt)) {
                    Content systemContent = Content.fromParts(Part.fromText(systemPrompt));
                    historyMessages.add(0, systemContent);
                }

                // 使用Gen AI处理消息
                return processWithGenAI(historyMessages, voiceMessage, conversationId, messageId)
                        .doOnError(error -> log.error("AI处理语音消息失败", error));
            } catch (Exception e) {
                log.error("处理语音消息失败", e);
                return Flux.<AIStreamEvent>error(e);
            }
        });
    }

    /**
     * 使用Gen AI处理消息
     */
    private Flux<AIStreamEvent> processWithGenAI(List<Content> historyMessages,
                                                 VoiceMessage voiceMessage,
                                                 String conversationId,
                                                 String messageId) {
        return Flux.<AIStreamEvent>create(sink -> {
                    try {
                        // 构建请求内容
                        List<Content> requestContents = new ArrayList<>();
                        if (historyMessages != null && !historyMessages.isEmpty()) {
                            requestContents.addAll(historyMessages);
                        }

                        Content audioContent = buildAudioContent(voiceMessage);
                        requestContents.add(audioContent);

                        // 创建生成配置
                        GenerateContentConfig config = createGenerateContentConfig();

                        StringBuilder fullResponse = new StringBuilder();

                        // 使用新SDK的非流式API暂时代替，流式API需要进一步调研
                        GenerateContentResponse response = genAIClient.models.generateContent(
                            genAIConfig.getVertexAi().getModel(),
                            requestContents,
                            config
                        );
                        
                        String responseText = response.text();
                        if (StringUtils.hasText(responseText)) {
                            fullResponse.append(responseText);
                            // 模拟流式响应，分段发送
                            String[] chunks = responseText.split("(?<=\\.)\\s+");
                            for (String chunk : chunks) {
                                if (StringUtils.hasText(chunk.trim())) {
                                    sink.next(AIStreamEvent.chunk(messageId, chunk + " "));
                                }
                            }
                        }
                        
                        ProcessedAiResponse processed = processCompleteResponse(
                            fullResponse.toString(), conversationId, null);
                        sink.next(AIStreamEvent.completed(messageId, processed));
                        sink.complete();

                    } catch (Exception e) {
                        sink.error(e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 构建包含音频的多模态内容
     * 
     * 支持的音频格式:
     * - WAV (audio/wav)
     * - MP3 (audio/mpeg) 
     * - WebM (audio/webm)
     * - OGG (audio/ogg)
     * - M4A (audio/m4a)
     * - FLAC (audio/flac)
     * - Opus (audio/opus)
     * 
     * @param voiceMessage 包含音频数据的语音消息
     * @return 多模态Content对象，包含文本指令和音频数据
     */
    private Content buildAudioContent(VoiceMessage voiceMessage) {
        Part textPart = Part.fromText("请理解并总结用户发送的音频内容，然后以角色身份进行自然对话回复。");
        
        // 验证音频数据有效性
        if (voiceMessage.getAudioData() == null || voiceMessage.getAudioData().length == 0) {
            log.warn("接收到空的音频数据，仅使用文本部分");
            return Content.fromParts(textPart);
        }
        
        // 验证音频数据大小（避免过大的文件）
        final int MAX_AUDIO_SIZE = 10 * 1024 * 1024; // 10MB限制
        if (voiceMessage.getAudioData().length > MAX_AUDIO_SIZE) {
            log.warn("音频文件过大: {}字节 > {}字节限制，仅使用文本部分", 
                    voiceMessage.getAudioData().length, MAX_AUDIO_SIZE);
            return Content.fromParts(textPart);
        }
        
        try {
            String mimeType = getAudioMimeType(voiceMessage.getAudioFormat());
            Part audioPart = Part.fromBytes(voiceMessage.getAudioData(), mimeType);
            
            log.debug("构建多模态音频内容成功: 音频字节数={}, MIME类型={}, 格式={}", 
                    voiceMessage.getAudioData().length, mimeType, voiceMessage.getAudioFormat());
            
            return Content.fromParts(textPart, audioPart);
            
        } catch (IllegalArgumentException e) {
            log.warn("不支持的音频格式或数据格式错误: {}, 仅使用文本部分", e.getMessage());
            return Content.fromParts(textPart);
        } catch (Exception e) {
            log.error("音频数据处理发生未知错误，仅使用文本部分: {}", e.getMessage(), e);
            return Content.fromParts(textPart);
        }
    }

    /**
     * 创建生成内容配置
     */
    private GenerateContentConfig createGenerateContentConfig() {
        GenAIConfig.GenAIProperties config = genAIConfig.getVertexAi();

        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();
        
        if (config.getTemperature() != null) {
            configBuilder.temperature(config.getTemperature().floatValue());
        }
        if (config.getMaxOutputTokens() != null) {
            configBuilder.maxOutputTokens(config.getMaxOutputTokens());
        }
        
        // 设置thinking config，包含think_budget参数
        if (config.getThinkBudget() != null) {
            configBuilder.thinkingConfig(
                ThinkingConfig.builder()
                    .thinkingBudget(config.getThinkBudget())
                    .build()
            );
            log.debug("设置think_budget参数: {}", config.getThinkBudget());
        }
        
        return configBuilder.build();
    }

    /**
     * 根据音频格式获取对应的MimeType
     * 支持Gemini模型常见的音频格式
     */
    private String getAudioMimeType(String audioFormat) {
        if (audioFormat == null || audioFormat.trim().isEmpty()) {
            log.warn("音频格式为空，使用默认MIME类型");
            return "audio/wav"; // 默认使用wav格式
        }
        
        String format = audioFormat.toLowerCase().trim();
        return switch (format) {
            case "wav", "wave" -> "audio/wav";
            case "mp3", "mpeg" -> "audio/mpeg";
            case "webm" -> "audio/webm";
            case "ogg", "oga" -> "audio/ogg";
            case "m4a", "aac" -> "audio/m4a";
            case "flac" -> "audio/flac";
            case "opus" -> "audio/opus";
            case "mp4" -> "audio/mp4";
            case "3gpp", "3gp" -> "audio/3gpp";
            case "amr" -> "audio/amr";
            case "awb" -> "audio/amr-wb";
            default -> {
                log.warn("未知音频格式: {}，使用默认MIME类型", format);
                yield "audio/wav";
            }
        };
    }

    /**
     * 构建系统提示词，使用模板
     */
    public String buildSystemPrompt(CharacterCard characterCard) {
        try {
            String template = promptTemplate.getContentAsString(StandardCharsets.UTF_8);
            
            // 手动替换占位符
            return template
                    .replace("{character_name}", characterCard.getName())
                    .replace("{character_description}", getCharacterDescription(characterCard))
                    .replace("{character_personality}", getCharacterPersonality(characterCard))
                    .replace("{character_style}", getCharacterStyle(characterCard));
            
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
            AsrMarkupProcessor.Result result = AsrMarkupProcessor.process(completeResponse);

            String aiResponse = StringUtils.hasText(result.sanitizedText())
                    ? result.sanitizedText()
                    : AsrMarkupProcessor.normalizeWhitespace(completeResponse);

            String userTranscription = StringUtils.hasText(result.transcription())
                    ? result.transcription()
                    : AsrMarkupProcessor.normalizeWhitespace(userTranscriptionFallback);

            if (StringUtils.hasText(userTranscription)) {
                chatMemoryService.addUserMessage(conversationId, userTranscription);
                log.debug("提取到用户转写内容: {}", userTranscription);
            }

            if (StringUtils.hasText(aiResponse)) {
                chatMemoryService.addAssistantMessage(conversationId, aiResponse);
            }

            log.debug("更新对话历史成功，对话ID: {}", conversationId);
            return new ProcessedAiResponse(aiResponse, userTranscription);

        } catch (Exception e) {
            log.error("处理完整回复失败", e);
            return new ProcessedAiResponse(
                    AsrMarkupProcessor.normalizeWhitespace(completeResponse),
                    AsrMarkupProcessor.normalizeWhitespace(userTranscriptionFallback)
            );
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
