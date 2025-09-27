package com.github.jwj.brilliantavern.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * 简单的 STOMP 入站日志拦截器，用于排查消息路由问题。
 */
@Slf4j
@Component
public class StompInboundLoggingInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor != null) {
            SimpMessageType messageType = accessor.getMessageType();
            StompCommand command = accessor.getCommand();
            String destination = accessor.getDestination();
            String sessionId = accessor.getSessionId();
            if (log.isDebugEnabled()) {
                log.debug("[STOMP-IN] type={}, command={}, destination={}, sessionId={}, headers={}",
                        messageType, command, destination, sessionId, accessor.toNativeHeaderMap());
            }
        } else if (log.isDebugEnabled()) {
            log.debug("[STOMP-IN] 无法获取消息头, message={}", message);
        }
        return message;
    }
}
