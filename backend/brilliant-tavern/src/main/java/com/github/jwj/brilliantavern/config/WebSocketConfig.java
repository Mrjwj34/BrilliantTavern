package com.github.jwj.brilliantavern.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket配置类
 * 用于语音对话功能的实时通信
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.websocket.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理，订阅前缀
        config.enableSimpleBroker("/topic", "/queue");
        // 设置应用程序前缀
        config.setApplicationDestinationPrefixes("/app");
        // 设置用户消息前缀
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册WebSocket端点，支持SockJS fallback
        registry.addEndpoint("/ws/voice-chat")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
        
        // 原生WebSocket端点（用于原始音频流传输，支持二进制数据）
        registry.addEndpoint("/ws/voice-stream")
                .setAllowedOrigins(allowedOrigins);
    }
}
