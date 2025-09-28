package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.config.GenAIConfig;
import com.github.jwj.brilliantavern.dto.VoiceMessage;
import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.github.jwj.brilliantavern.entity.CharacterMemory;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final CharacterMemoryService characterMemoryService;
    private final com.github.jwj.brilliantavern.service.streaming.RetryService retryService;
    
    @Value("classpath:prompts/character-chat-template.st")
    private Resource promptTemplate;

    /**
     * 处理语音消息并返回包含分段的AI流式事件。
     */
    public Flux<AIStreamEvent> streamVoiceConversation(VoiceMessage voiceMessage,
                                                       CharacterCard characterCard,
                                                       String conversationId,
                                                       String messageId,
                                                       UUID userId) {
        return Flux.defer(() -> {
            try {
                if (voiceMessage.getAudioData() == null || voiceMessage.getAudioData().length == 0) {
                    String msg = "接收到空的音频数据，无法生成AI回复";
                    log.warn("{} - conversationId={}, messageId={}", msg, conversationId, messageId);
                    return Flux.error(new IllegalArgumentException(msg));
                }

                String systemPrompt = buildSystemPrompt(characterCard, voiceMessage);
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
                return processWithGenAI(historyMessages, voiceMessage, conversationId, messageId, characterCard, userId)
                        .doOnError(error -> log.error("AI处理语音消息失败", error));
            } catch (Exception e) {
                log.error("处理语音消息失败", e);
                return Flux.<AIStreamEvent>error(e);
            }
        });
    }

    /**
     * 使用Gen AI处理消息 - 真流式实现，支持重试
     */
    private Flux<AIStreamEvent> processWithGenAI(List<Content> historyMessages,
                                                 VoiceMessage voiceMessage,
                                                 String conversationId,
                                                 String messageId,
                                                 CharacterCard characterCard,
                                                 UUID userId) {
        return retryService.retryWithProgress(
                createGenAIStream(historyMessages, voiceMessage, conversationId, messageId, characterCard, userId),
                conversationId,
                messageId,
                "LLM调用",
                context -> Flux.just(retryService.createRetryProgressEvent(context))
        );
    }
    
    /**
     * 创建Gen AI流 - 单次调用逻辑
     */
    private Flux<AIStreamEvent> createGenAIStream(List<Content> historyMessages,
                                                 VoiceMessage voiceMessage,
                                                 String conversationId,
                                                 String messageId,
                                                 CharacterCard characterCard,
                                                 UUID userId) {
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
                        boolean isMemoryRetrievalRequest = false;

                        // 使用真流式API
                        var responseStream = genAIClient.models.generateContentStream(
                            genAIConfig.getVertexAi().getModel(),
                            requestContents,
                            config
                        );
                        
                        // 处理流式响应 - 使用try-with-resources确保资源正确关闭
                        try (responseStream) {
                            for (GenerateContentResponse response : responseStream) {
                                String text = response.text();
                                if (StringUtils.hasText(text)) {
                                    fullResponse.append(text);
                                    
                                    // 检测是否为单个[MEM]标签的记忆检索请求
                                    if (!isMemoryRetrievalRequest && isSingleMemTagRetrieval(fullResponse.toString())) {
                                        isMemoryRetrievalRequest = true;
                                        log.debug("检测到记忆检索请求: {}", fullResponse.toString());
                                        continue; // 不发送chunk事件，等待完整响应
                                    }
                                    
                                    // 如果不是记忆检索请求，正常发送chunk
                                    if (!isMemoryRetrievalRequest) {
                                        sink.next(AIStreamEvent.chunk(messageId, text));
                                    }
                                }
                            }
                        }
                        
                        String finalResponse = fullResponse.toString();
                        
                        // 如果是记忆检索请求，执行检索并模拟对话轮次
                        if (isMemoryRetrievalRequest) {
                            handleMemoryRetrieval(finalResponse, conversationId, characterCard, userId, sink, messageId, voiceMessage);
                        } else {
                            // 正常完成流程
                            ProcessedAiResponse processed = new ProcessedAiResponse(finalResponse, null);
                            sink.next(AIStreamEvent.completed(messageId, processed));
                            sink.complete();
                        }

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
    public String buildSystemPrompt(CharacterCard characterCard, VoiceMessage voiceMessage) {
        try {
            String template = promptTemplate.getContentAsString(StandardCharsets.UTF_8);
            
            // 优先使用语音消息中的语言设置，如果没有则使用角色卡默认设置
            String voiceLanguage = (voiceMessage != null && voiceMessage.getVoiceLanguage() != null) ? 
                voiceMessage.getVoiceLanguage() : 
                (characterCard.getVoiceLanguage() != null ? characterCard.getVoiceLanguage() : "zh");
                
            String subtitleLanguage = (voiceMessage != null && voiceMessage.getSubtitleLanguage() != null) ? 
                voiceMessage.getSubtitleLanguage() : 
                (characterCard.getSubtitleLanguage() != null ? characterCard.getSubtitleLanguage() : "zh");
            
            log.debug("构建系统提示词: voiceLanguage={}, subtitleLanguage={}", voiceLanguage, subtitleLanguage);
            
            // 手动替换占位符
            return template
                    .replace("{character_name}", characterCard.getName())
                    .replace("{character_description}", getCharacterDescription(characterCard))
                    .replace("{character_personality}", getCharacterPersonality(characterCard))
                    .replace("{character_style}", getCharacterStyle(characterCard))
                    .replace("{voice_language}", voiceLanguage)
                    .replace("{subtitle_language}", subtitleLanguage);
            
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

    /**
     * 生成简单文本响应（用于标题生成等场景）
     */
    public String generateSimpleText(String prompt) {
        try {
            log.debug("生成简单文本请求: prompt长度={}", prompt.length());
            
            Part textPart = Part.fromText(prompt);
            List<Content> contents = List.of(Content.fromParts(textPart));

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .temperature(0.7f)
                    .maxOutputTokens(200) // 进一步增加token限制，确保能生成完整标题
                    .build();

            log.debug("调用Vertex AI生成内容: model={}", genAIConfig.getVertexAi().getModel());
            
            GenerateContentResponse response = genAIClient.models.generateContent(
                genAIConfig.getVertexAi().getModel(),
                contents,
                config
            );

            if (response != null) {
                log.debug("收到AI响应: response={}", response);
                String text = response.text();
                log.debug("解析的文本内容: text={}", text);
                
                if (StringUtils.hasText(text)) {
                    String result = text.trim();
                    log.info("AI文本生成成功: result={}", result);
                    return result;
                }
            }

            log.warn("AI响应为空或格式异常: response={}", response);
            return null;

        } catch (Exception e) {
            log.error("生成简单文本失败: prompt={}", prompt, e);
            return null;
        }
    }
    
    /**
     * 检测是否为单个[MEM]标签的记忆检索请求
     */
    private boolean isSingleMemTagRetrieval(String response) {
        if (!StringUtils.hasText(response)) {
            return false;
        }
        
        String trimmed = response.trim();
        
        // 检查是否以[MEM]开头且包含结束标签
        if (trimmed.startsWith("[MEM]") && trimmed.contains("[/MEM]")) {
            // 确保不包含其他标签 (TSS, SUB, ASR, DO)
            boolean hasOtherTags = trimmed.contains("[TSS:") || 
                                 trimmed.contains("[SUB:") || 
                                 trimmed.contains("[ASR]") ||
                                 trimmed.contains("[DO]");
            
            // 检测是否有重复的[MEM]标签（表示格式错误）
            int memCount = (trimmed.length() - trimmed.replace("[MEM]", "").length()) / 5; // "[MEM]"长度为5
            boolean hasRepeatedMemTags = memCount > 1;
            
            if (hasRepeatedMemTags) {
                log.warn("检测到重复的[MEM]标签，将作为格式错误处理: {}", trimmed);
                return false; // 当作格式错误，不进入记忆检索流程
            }
            
            return !hasOtherTags;
        }
        
        return false;
    }
    
    /**
     * 处理记忆检索逻辑
     */
    private void handleMemoryRetrieval(String memTagResponse, String conversationId, 
                                     CharacterCard characterCard, UUID userId,
                                     reactor.core.publisher.FluxSink<AIStreamEvent> sink, String messageId,
                                     VoiceMessage originalVoiceMessage) {
        try {
            log.debug("开始处理记忆检索: {}", memTagResponse);
            
            log.debug("开始记忆检索流程");
            
            // 解析[MEM]标签中的查询文本
            String queryText = extractQueryFromMemTag(memTagResponse);
            if (queryText == null) {
                log.warn("无法解析MEM标签中的查询文本: {}", memTagResponse);
                sink.error(new RuntimeException("无法解析记忆检索查询"));
                return;
            }
            
            // 执行记忆检索
            String retrievalResult = executeMemoryRetrieval(queryText, characterCard, userId);
            
            // 模拟虚拟对话轮次 - 添加到对话历史但不入库
            addVirtualConversationRound(conversationId, queryText, retrievalResult);
            
            log.debug("记忆检索完成，准备生成正常回复");
            
            // 继续正常的AI对话流程 - 重新调用AI生成真正的回复
            continueNormalConversation(conversationId, characterCard, userId, sink, messageId, originalVoiceMessage);
            
        } catch (Exception e) {
            log.error("记忆检索处理失败", e);
            sink.error(e);
        }
    }
    
    /**
     * 从[MEM]标签中提取查询文本
     */
    private String extractQueryFromMemTag(String memTagResponse) {
        Pattern pattern = Pattern.compile("\\[MEM\\](.+?)\\[/MEM\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(memTagResponse);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
    
    /**
     * 执行记忆检索
     */
    private String executeMemoryRetrieval(String queryText, CharacterCard characterCard, UUID userId) {
        try {
            UUID characterCardId = characterCard.getId();
            
            log.debug("执行记忆检索: userId={}, characterCardId={}, query={}", userId, characterCardId, queryText);
            
            // 调用记忆服务检索相似记忆
            List<CharacterMemory> memories = characterMemoryService.retrieveSimilarMemories(userId, characterCardId, queryText, 3);
            
            if (memories.isEmpty()) {
                return "没有找到相关的记忆。";
            }
            
            // 格式化检索结果
            StringBuilder result = new StringBuilder("检索到以下相关记忆:\n");
            for (int i = 0; i < memories.size(); i++) {
                result.append(String.format("%d. %s\n", i + 1, memories.get(i).getMemoryContent()));
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("执行记忆检索失败: {}", queryText, e);
            return "记忆检索失败: " + e.getMessage();
        }
    }
    
    /**
     * 添加虚拟对话轮次到对话历史（不入库）
     */
    private void addVirtualConversationRound(String conversationId, String queryText, String result) {
        try {
            // 创建更自然的对话格式，作为上下文参考
            // 模拟AI内部记忆检索过程
            Content internalQuery = Content.fromParts(Part.fromText("(内部记忆检索: " + queryText + ")"));
            
            // 检索结果作为内部知识
            String memoryContext = result.isEmpty() ? "(没有找到相关记忆)" : "(记忆内容: " + result + ")";
            Content memoryResponse = Content.fromParts(Part.fromText(memoryContext));
            
            // 添加到对话历史但不持久化到数据库
            chatMemoryService.addVirtualMessageToHistory(conversationId, memoryResponse, internalQuery);
            
            log.debug("添加虚拟记忆上下文成功: conversationId={}, query={}", conversationId, queryText);
            
        } catch (Exception e) {
            log.error("添加虚拟对话轮次失败: conversationId={}", conversationId, e);
        }
    }
    
    /**
     * 继续正常的AI对话流程
     */
    private void continueNormalConversation(String conversationId, 
                                          CharacterCard characterCard, 
                                          UUID userId,
                                          reactor.core.publisher.FluxSink<AIStreamEvent> sink, 
                                          String messageId,
                                          VoiceMessage originalVoiceMessage) {
        try {
            // 重新获取包含虚拟对话的历史记录
            List<Content> updatedHistory = chatMemoryService.getHistory(conversationId);
            
            // 添加系统指令
            String systemPrompt = buildSystemPrompt(characterCard, originalVoiceMessage);
            if (StringUtils.hasText(systemPrompt)) {
                Content systemContent = Content.fromParts(Part.fromText(systemPrompt));
                updatedHistory.add(0, systemContent);
            }
            
            // 重新添加原始音频内容到请求中
            Content audioContent = buildAudioContent(originalVoiceMessage);
            updatedHistory.add(audioContent);
            
            GenerateContentConfig config = createGenerateContentConfig();
            StringBuilder fullResponse = new StringBuilder();
            
            var responseStream = genAIClient.models.generateContentStream(
                genAIConfig.getVertexAi().getModel(),
                updatedHistory,
                config
            );
            
            try (responseStream) {
                for (GenerateContentResponse response : responseStream) {
                    String text = response.text();
                    if (StringUtils.hasText(text)) {
                        fullResponse.append(text);
                        sink.next(AIStreamEvent.chunk(messageId, text));
                    }
                }
            }
            
            ProcessedAiResponse processed = new ProcessedAiResponse(fullResponse.toString(), null);
            sink.next(AIStreamEvent.completed(messageId, processed));
            
            // 清除虚拟消息缓存，避免影响后续对话
            chatMemoryService.clearVirtualHistory(conversationId);
            log.debug("已清除虚拟记忆上下文: conversationId={}", conversationId);
            
            sink.complete();
            
        } catch (Exception e) {
            log.error("继续正常对话失败", e);
            sink.error(e);
        }
    }
    
    /**
     * 创建记忆检索事件 - 使用专门的事件类型，不通过CHUNK发送
     */
    private AIStreamEvent createMemoryRetrievalEvent(String messageId, String eventType, String message) {
        // 创建一个特殊的完成事件，包含记忆检索信息
        ProcessedAiResponse memoryEventResponse = new ProcessedAiResponse(
            String.format("[MEMORY_EVENT:%s]%s[/MEMORY_EVENT]", eventType, message), 
            null
        );
        return new AIStreamEvent(AIStreamEvent.Type.COMPLETED, messageId, null, memoryEventResponse);
    }
}
