package com.github.jwj.brilliantavern.repository;

import com.github.jwj.brilliantavern.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户数据访问层
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * 通过用户名查找用户
     *
     * @param username 用户名
     * @return 用户对象
     */
    Optional<User> findByUsername(String username);

    /**
     * 通过邮箱查找用户
     *
     * @param email 邮箱地址
     * @return 用户对象
     */
    Optional<User> findByEmail(String email);

    /**
     * 检查用户名是否已存在
     *
     * @param username 用户名
     * @return 是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否已存在
     *
     * @param email 邮箱地址
     * @return 是否存在
     */
    boolean existsByEmail(String email);
}
