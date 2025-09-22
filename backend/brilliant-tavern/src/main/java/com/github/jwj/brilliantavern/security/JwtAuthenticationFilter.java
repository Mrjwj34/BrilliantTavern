package com.github.jwj.brilliantavern.security;

import com.github.jwj.brilliantavern.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    @Value("${jwt.header}")
    private String tokenHeader;

    @Value("${jwt.token-prefix}")
    private String tokenPrefix;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain chain) throws ServletException, IOException {
        
        String requestHeader = request.getHeader(tokenHeader);
        
        String username = null;
        String authToken = null;
        
        if (requestHeader != null && requestHeader.startsWith(tokenPrefix)) {
            authToken = requestHeader.substring(tokenPrefix.length());
            try {
                username = jwtUtil.getUsernameFromToken(authToken);
            } catch (Exception e) {
                log.warn("无法从token中获取用户名: {}", e.getMessage());
            }
        }
        
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            
            if (jwtUtil.validateToken(authToken, userDetails)) {
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("用户 {} 已通过JWT认证", username);
            }
        }
        
        chain.doFilter(request, response);
    }
}
