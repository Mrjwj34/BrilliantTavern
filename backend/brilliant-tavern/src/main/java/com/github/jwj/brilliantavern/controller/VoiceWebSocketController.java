package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.dto.VoiceMessage;
import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.github.jwj.brilliantavern.service.AIService;
import com.github.jwj.brilliantavern.service.CharacterCardService;
import com.github.jwj.brilliantavern.service.tts.TTSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.Map;
import java.util.UUID;

/**
 * 语音WebSocket控制器
 * 处理完整的语音对话工作流程：音频接收 → AI处理 → TTS生成 → 返回音频
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class VoiceWebSocketController {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final AIService aiService;
    private final CharacterCardService characterCardService;
    private final TTSService ttsService;

    /**
     * 处理语音消息 - 完整的语音对话流程
     */
    @MessageMapping("/voice/{sessionId}")
    public void handleVoiceMessage(@DestinationVariable String sessionId, @Payload Map<String, Object> voiceData) {
        log.debug("收到语音消息，会话ID: {}", sessionId);

        try {
            // 1. 解析语音消息数据 (直接处理二进制数据)
            VoiceMessage voiceMessage = parseVoiceMessage(voiceData);
            String characterCardId = (String) voiceData.get("characterCardId");
            
            if (characterCardId == null || voiceMessage.getAudioData() == null) {
                sendErrorMessage(sessionId, "缺少必要参数：characterCardId或audioData");
                return;
            }

            // 2. 获取角色卡信息  
            CharacterCard characterCard = characterCardService.findById(UUID.fromString(characterCardId));
            
            if (characterCard == null) {
                sendErrorMessage(sessionId, "角色卡不存在：" + characterCardId);
                return;
            }

            // 3. 发送处理开始通知
            sendProcessingStarted(sessionId);

            // 4. 调用AI服务处理语音消息（流式响应）
            // TODO: 改为按句分割ai流式响应并异步(如使用redis消息队列)tts, 前端按序列播放
            aiService.processVoiceMessage(voiceMessage, characterCard, sessionId)
                .collectList()
                .doOnNext(textSegments -> {
                    // 5. 合并AI回复文本
                    String fullResponse = String.join("", textSegments);
                    log.debug("AI完整回复: {}", fullResponse);
                    
                    // 6. 发送文本回复
                    sendTextResponse(sessionId, fullResponse);
                })
                .flatMap(textSegments -> {
                    // 7. 合并文本并调用TTS生成语音
                    String fullResponse = String.join("", textSegments);
                    String voiceId = characterCard.getTtsVoiceId() != null ? characterCard.getTtsVoiceId() : "default";
                    return ttsService.textToSpeech(fullResponse, voiceId);
                })
                .subscribe(
                    audioBytes -> {
                        // 8. 发送音频回复
                        sendAudioResponse(sessionId, audioBytes);
                        sendProcessingCompleted(sessionId);
                    },
                    error -> {
                        log.error("处理语音消息失败，会话ID: {}", sessionId, error);
                        sendErrorMessage(sessionId, "语音处理失败: " + error.getMessage());
                    }
                );

        } catch (Exception e) {
            log.error("处理语音消息异常，会话ID: {}", sessionId, e);
            sendErrorMessage(sessionId, "系统异常: " + e.getMessage());
        }
    }

    /**
     * 解析语音消息数据 - 直接处理原始音频字节流
     */
    private VoiceMessage parseVoiceMessage(Map<String, Object> voiceData) {
        // 直接获取二进制音频数据 (前端需要发送byte[]格式)
        byte[] audioData = (byte[]) voiceData.get("audioData");
        String audioFormat = (String) voiceData.getOrDefault("audioFormat", "wav");
        
        return VoiceMessage.builder()
            .audioData(audioData)
            .audioFormat(audioFormat)
            .messageId((String) voiceData.get("messageId"))
            .timestamp((Long) voiceData.getOrDefault("timestamp", System.currentTimeMillis()))
            .build();
    }

    /**
     * 发送处理开始通知
     */
    private void sendProcessingStarted(String sessionId) {
        Map<String, Object> message = Map.of(
            "type", "PROCESSING_STARTED",
            "timestamp", System.currentTimeMillis(),
            "sessionId", sessionId
        );
        messagingTemplate.convertAndSend("/topic/voice/" + sessionId, message);
    }

    /**
     * 发送文本回复
     */
    private void sendTextResponse(String sessionId, String text) {
        Map<String, Object> message = Map.of(
            "type", "TEXT_RESPONSE",
            "text", text,
            "timestamp", System.currentTimeMillis(),
            "sessionId", sessionId
        );
        messagingTemplate.convertAndSend("/topic/voice/" + sessionId, message);
    }

    /**
     * 发送音频回复 - 直接发送原始音频字节流
     */
    private void sendAudioResponse(String sessionId, byte[] audioBytes) {
        Map<String, Object> message = Map.of(
            "type", "AUDIO_RESPONSE",
            "audioData", audioBytes, // 直接发送二进制数据
            "audioFormat", "wav",
            "timestamp", System.currentTimeMillis(),
            "sessionId", sessionId
        );
        messagingTemplate.convertAndSend("/topic/voice/" + sessionId, message);
    }

    /**
     * 发送处理完成通知
     */
    private void sendProcessingCompleted(String sessionId) {
        Map<String, Object> message = Map.of(
            "type", "PROCESSING_COMPLETED",
            "timestamp", System.currentTimeMillis(),
            "sessionId", sessionId
        );
        messagingTemplate.convertAndSend("/topic/voice/" + sessionId, message);
    }

    /**
     * 发送错误消息
     */
    private void sendErrorMessage(String sessionId, String errorMessage) {
        Map<String, Object> message = Map.of(
            "type", "ERROR",
            "error", errorMessage,
            "timestamp", System.currentTimeMillis(),
            "sessionId", sessionId
        );
        messagingTemplate.convertAndSend("/topic/voice/" + sessionId, message);
    }
}
