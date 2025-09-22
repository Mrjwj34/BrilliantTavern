package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.dto.JwtResponse;
import com.github.jwj.brilliantavern.dto.LoginRequest;
import com.github.jwj.brilliantavern.dto.RegisterRequest;
import com.github.jwj.brilliantavern.entity.User;
import com.github.jwj.brilliantavern.exception.BusinessException;
import com.github.jwj.brilliantavern.repository.UserRepository;
import com.github.jwj.brilliantavern.security.UserPrincipal;
import com.github.jwj.brilliantavern.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 用户注册
     */
    @Transactional
    public JwtResponse register(RegisterRequest request) {
        log.info("用户注册请求: {}", request.getUsername());

        // 参数校验
        validateRegisterRequest(request);

        // 检查用户名和邮箱是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }

        // 创建用户
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        user = userRepository.save(user);
        log.info("用户注册成功: {}", user.getUsername());

        // 生成JWT token
        UserPrincipal userPrincipal = UserPrincipal.create(user);
        String token = jwtUtil.generateToken(userPrincipal, user.getId());

        return JwtResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .expiresAt(jwtUtil.getExpirationTime())
                .build();
    }

    /**
     * 用户登录
     */
    @Transactional(readOnly = true)
    public JwtResponse login(LoginRequest request) {
        log.info("用户登录请求: {}", request.getUsername());

        // 认证
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 获取用户信息
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findByUsername(userPrincipal.getUsername())
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 生成JWT token
        String token = jwtUtil.generateToken(userPrincipal, user.getId());

        log.info("用户登录成功: {}", user.getUsername());

        return JwtResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .expiresAt(jwtUtil.getExpirationTime())
                .build();
    }

    /**
     * 校验注册请求参数
     */
    private void validateRegisterRequest(RegisterRequest request) {
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BusinessException("密码不能为空");
        }

        if (request.getPassword().length() < 6) {
            throw new BusinessException("密码长度不能少于6位");
        }
    }
}
