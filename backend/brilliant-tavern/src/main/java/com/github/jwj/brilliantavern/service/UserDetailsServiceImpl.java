package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.entity.User;
import com.github.jwj.brilliantavern.repository.UserRepository;
import com.github.jwj.brilliantavern.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户详情服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        log.debug("加载用户详情: {}", username);
        return UserPrincipal.create(user);
    }

    /**
     * 根据用户ID加载用户详情
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + userId));

        return UserPrincipal.create(user);
    }
}
