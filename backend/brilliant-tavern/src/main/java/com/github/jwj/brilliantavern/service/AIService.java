package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.github.jwj.brilliantavern.dto.VoiceMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    private static final Pattern ASR_PATTERN = Pattern.compile("\\[ASR_TRANSCRIPTION\\](.*?)\\[/ASR_TRANSCRIPTION\\]");

    /**
     * 处理语音消息并返回AI回复流
     * 
     * @param voiceMessage 语音消息
     * @param characterCard 角色卡信息
     * @param conversationId 对话ID
     * @return AI回复流
     */
    public Flux<String> processVoiceMessage(VoiceMessage voiceMessage, 
                                          CharacterCard characterCard,
                                          String conversationId) {
        try {
            // 构建系统提示词
            String systemPrompt = buildSystemPrompt(characterCard);
            
            // TODO: 这里应该集成实际的音频处理，暂时模拟用户说了"你好"
            String simulatedUserText = "你好，我刚发送了一段语音消息。";
            
            // 获取历史对话
            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.addAll(chatMemory.get(conversationId));
            messages.add(new UserMessage(simulatedUserText));
            
            // 构建提示
            Prompt prompt = new Prompt(messages);
            
            // 调用模型并处理流式响应
            StringBuilder fullResponse = new StringBuilder();
            
            return chatModel.stream(prompt)
                .map(response -> response.getResult().getOutput().getText())
                .doOnNext(content -> {
                    fullResponse.append(content);
                    log.debug("AI回复片段: {}", content);
                })
                .doOnComplete(() -> {
                    // 处理完整回复，提取ASR转写并更新对话历史
                    String completeResponse = fullResponse.toString();
                    processCompleteResponse(completeResponse, conversationId, simulatedUserText);
                })
                .doOnError(error -> log.error("AI处理失败", error));
                
        } catch (Exception e) {
            log.error("处理语音消息失败", e);
            return Flux.error(e);
        }
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
            // 降级方案：使用简单的字符串拼接
            return buildFallbackPrompt(characterCard);
        }
    }
    
    /**
     * 降级方案：简单的提示词构建
     */
    private String buildFallbackPrompt(CharacterCard characterCard) {
        return String.format(
            "你是一个名为 %s 的虚拟角色。%s\n性格特点：%s\n说话风格：%s\n" +
            "请完全代入这个角色进行对话，并在回复后用[ASR_TRANSCRIPTION]标记转写用户语音内容。",
            characterCard.getName(),
            getCharacterDescription(characterCard),
            getCharacterPersonality(characterCard),
            getCharacterStyle(characterCard)
        );
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
    private void processCompleteResponse(String completeResponse, String conversationId, String userTranscriptionFallback) {
        try {
            // 提取ASR转写内容
            Matcher matcher = ASR_PATTERN.matcher(completeResponse);
            String userTranscription = userTranscriptionFallback; // 使用回退值
            String aiResponse = completeResponse;
            
            if (matcher.find()) {
                userTranscription = matcher.group(1).trim();
                // 从AI回复中移除ASR标记
                aiResponse = completeResponse.replaceAll("\\[ASR_TRANSCRIPTION\\].*?\\[/ASR_TRANSCRIPTION\\]", "").trim();
                
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
            
        } catch (Exception e) {
            log.error("处理完整回复失败", e);
        }
    }

    /**
     * 清除指定对话的历史记录
     */
    public void clearConversationHistory(String conversationId) {
        try {
            chatMemory.clear(conversationId);
            log.info("清除对话历史成功，对话ID: {}", conversationId);
        } catch (Exception e) {
            log.error("清除对话历史失败，对话ID: {}", conversationId, e);
        }
    }
}
