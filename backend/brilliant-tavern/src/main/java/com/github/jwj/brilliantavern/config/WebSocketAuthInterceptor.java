package com.github.jwj.brilliantavern.config;

import com.github.jwj.brilliantavern.util.JwtUtil;
import com.github.jwj.brilliantavern.security.UserPrincipal;
import com.github.jwj.brilliantavern.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * WebSocket认证拦截器
 * 在WebSocket连接时验证JWT token并设置用户身份
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                
                try {
                    if (!jwtUtil.isTokenExpired(token)) {
                        String username = jwtUtil.getUsernameFromToken(token);
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        
                        if (userDetails instanceof UserPrincipal userPrincipal) {
                            // 验证token是否有效
                            if (jwtUtil.validateToken(token, userDetails)) {
                                // 创建一个自定义的Principal，使用用户ID作为name
                                Authentication auth = new UsernamePasswordAuthenticationToken(
                                    new UserIdPrincipal(userPrincipal.getId().toString(), userPrincipal), 
                                    null, 
                                    userPrincipal.getAuthorities());
                                accessor.setUser(auth);
                                
                                log.info("WebSocket认证成功: userId={}, username={}", 
                                        userPrincipal.getId(), userPrincipal.getUsername());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("WebSocket认证失败: {}", e.getMessage());
                }
            } else {
                log.warn("WebSocket连接缺少Authorization header");
            }
        }
        
        return message;
    }

    /**
     * 自定义Principal，使用用户ID作为name用于WebSocket用户会话管理
     */
    private static class UserIdPrincipal implements Principal {
        private final String userId;
        private final UserPrincipal userPrincipal;

        public UserIdPrincipal(String userId, UserPrincipal userPrincipal) {
            this.userId = userId;
            this.userPrincipal = userPrincipal;
        }

        @Override
        public String getName() {
            return userId; // 返回用户ID而不是用户名
        }

        public UserPrincipal getUserPrincipal() {
            return userPrincipal;
        }
    }
}