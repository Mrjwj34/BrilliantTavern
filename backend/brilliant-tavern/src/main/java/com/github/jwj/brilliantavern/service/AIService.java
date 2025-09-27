package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.config.VertexAIConfig;
import com.github.jwj.brilliantavern.dto.VoiceMessage;
import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.github.jwj.brilliantavern.service.util.AsrMarkupProcessor;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.google.cloud.vertexai.generativeai.ResponseStream;
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
 * AI服务类，负责与Google Vertex AI集成处理语音对话
 * 使用ChatMemoryService管理对话历史，支持音频转写功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final VertexAI vertexAI;
    private final VertexAIConfig vertexAIConfig;
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

                // 创建生成模型
                GenerativeModel model = createGenerativeModel(systemPrompt);

                log.debug("调用AI开始: conversationId={}, messageId={}, historySize={}, audioBytes={}",
                        conversationId, messageId, historyMessages.size(), voiceMessage.getAudioData().length);

                // 创建聊天会话并发送消息
                return processWithVertexAI(model, historyMessages, voiceMessage, conversationId, messageId)
                        .doOnError(error -> log.error("AI处理语音消息失败", error));
            } catch (Exception e) {
                log.error("处理语音消息失败", e);
                return Flux.<AIStreamEvent>error(e);
            }
        });
    }

    /**
     * 使用Vertex AI处理消息
     */
    private Flux<AIStreamEvent> processWithVertexAI(GenerativeModel model,
                                                    List<Content> historyMessages,
                                                    VoiceMessage voiceMessage,
                                                    String conversationId,
                                                    String messageId) {
        return Flux.<AIStreamEvent>create(sink -> {
                    List<Content> requestContents = new ArrayList<>();
                    if (historyMessages != null && !historyMessages.isEmpty()) {
                        requestContents.addAll(historyMessages);
                    }

                    Content audioContent = buildAudioContent(voiceMessage);
                    requestContents.add(audioContent);

                    ResponseStream<GenerateContentResponse> responseStream = null;
                    StringBuilder fullResponse = new StringBuilder();

                    try {
                        responseStream = model.generateContentStream(requestContents);

                        for (GenerateContentResponse responseChunk : responseStream) {
                            String delta = ResponseHandler.getText(responseChunk);
                            if (StringUtils.hasText(delta)) {
                                fullResponse.append(delta);
                                sink.next(AIStreamEvent.chunk(messageId, delta));
                            }
                        }

                        ProcessedAiResponse processed = processCompleteResponse(fullResponse.toString(), conversationId, null);
                        sink.next(AIStreamEvent.completed(messageId, processed));
                        sink.complete();
                    } catch (Exception e) {
                        sink.error(e);
                    } finally {
                        // ResponseStream在当前SDK中不支持显式关闭，依赖GC即可
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 构建包含音频的内容
     */
    private Content buildAudioContent(VoiceMessage voiceMessage) {
        String mimeType = getAudioMimeType(voiceMessage.getAudioFormat());

        Part textPart = Part.newBuilder()
                .setText("请理解并总结用户发送的音频内容，然后以角色身份进行自然对话回复。")
                .build();

        Part audioPart = Part.newBuilder()
                .setInlineData(
                    com.google.cloud.vertexai.api.Blob.newBuilder()
                            .setMimeType(mimeType)
                            .setData(com.google.protobuf.ByteString.copyFrom(voiceMessage.getAudioData()))
                )
                .build();

        return Content.newBuilder()
                .setRole("user")
                .addParts(textPart)
                .addParts(audioPart)
                .build();
    }

    /**
     * 创建生成模型
     */
    private GenerativeModel createGenerativeModel(String systemPrompt) {
        VertexAIConfig.VertexAIProperties config = vertexAIConfig.getVertexAi();

        GenerationConfig.Builder generationConfigBuilder = GenerationConfig.newBuilder();
        if (config.getTemperature() != null) {
            generationConfigBuilder.setTemperature(config.getTemperature().floatValue());
        }
        if (config.getMaxOutputTokens() != null) {
            generationConfigBuilder.setMaxOutputTokens(config.getMaxOutputTokens());
        }
        return new GenerativeModel.Builder()
                .setModelName(config.getModel())
                .setVertexAi(vertexAI)
                .setGenerationConfig(generationConfigBuilder.build())
                .setSystemInstruction(Content.newBuilder()
                        .addParts(Part.newBuilder().setText(systemPrompt))
                        .build())
                .build();
    }

    /**
     * 根据音频格式获取对应的MimeType
     */
    private String getAudioMimeType(String audioFormat) {
        if (audioFormat == null) {
            return "application/octet-stream";
        }
        
        return switch (audioFormat.toLowerCase()) {
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "webm" -> "audio/webm";
            case "ogg" -> "audio/ogg";
            case "m4a" -> "audio/m4a";
            default -> "application/octet-stream";
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
