package com.github.jwj.brilliantavern.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket配置类
 * 用于语音对话功能的实时通信
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.websocket.allowed-origins}")
    private String[] allowedOrigins;

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

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
        // 通用WebSocket端点（用于历史更新等通用消息）
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins);

        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS()
                .setStreamBytesLimit(512 * 1024)
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30_000);
        
        // 语音对话专用WebSocket端点，支持SockJS fallback
        registry.addEndpoint("/ws/voice-chat")
                .setAllowedOrigins(allowedOrigins);

        registry.addEndpoint("/ws/voice-chat")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS()
                .setStreamBytesLimit(512 * 1024)
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30_000);
        
        // 原生WebSocket端点（用于原始音频流传输，支持二进制数据）
        registry.addEndpoint("/ws/voice-stream")
                .setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
    // 调整消息大小和缓冲区限制以支持语音数据传输
    registry.setMessageSizeLimit(10 * 1024 * 1024); // 10 MB
    registry.setSendBufferSizeLimit(10 * 1024 * 1024);
    registry.setSendTimeLimit(200 * 1000); // 200 秒
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024); // 10 MB
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);
        container.setMaxSessionIdleTimeout(300_000L); // 5 minutes
        container.setAsyncSendTimeout(60_000L);
        return container;
    }
}
