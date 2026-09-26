package com.pics2text.api.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.persistence.UserEntity;
import com.pics2text.api.infrastructure.persistence.UserMapper;
import com.pics2text.identity.domain.GlobalRole;
import com.pics2text.identity.domain.PasswordPolicy;
import com.pics2text.identity.domain.User;
import com.pics2text.identity.domain.UsernamePolicy;
import com.pics2text.identity.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("Dummy-Password-Only-2026!");
    }

    public long count() {
        return userMapper.selectCount(null);
    }

    @Transactional
    public User create(String username, String password, String displayName, GlobalRole role) {
        String normalizedUsername = UsernamePolicy.normalize(username);
        if (!UsernamePolicy.valid(normalizedUsername)) {
            throw new ApiException("USERNAME_INVALID", "用户名格式不正确", HttpStatus.BAD_REQUEST);
        }
        if (!PasswordPolicy.valid(password)) {
            throw new ApiException(
                    "PASSWORD_WEAK",
                    "密码需为12至128位，并包含大写字母、小写字母、数字和特殊字符",
                    HttpStatus.BAD_REQUEST);
        }
        String normalizedDisplayName = displayName == null ? "" : displayName.trim();
        if (normalizedDisplayName.isBlank() || normalizedDisplayName.length() > 100) {
            throw new ApiException("DISPLAY_NAME_INVALID", "显示名称不能为空且不能超过100个字符", HttpStatus.BAD_REQUEST);
        }

        Instant now = Instant.now();
        UserEntity entity = new UserEntity(
                UUID.randomUUID(),
                normalizedUsername,
                passwordEncoder.encode(password),
                normalizedDisplayName,
                role.name(),
                UserStatus.ACTIVE.name(),
                now,
                now);
        try {
            userMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ApiException("USERNAME_EXISTS", "用户名已存在", HttpStatus.CONFLICT);
        }
        return toDomain(entity);
    }

    public Optional<User> findByUsername(String username) {
        UserEntity entity = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, UsernamePolicy.normalize(username)));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    public Optional<User> findById(String userId) {
        try {
            return Optional.ofNullable(userMapper.selectById(UUID.fromString(userId))).map(this::toDomain);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public User require(UUID userId) {
        return Optional.ofNullable(userMapper.selectById(userId))
                .map(this::toDomain)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
    }

    public List<User> list() {
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .orderByAsc(UserEntity::getCreatedAt)
                        .last("LIMIT 200"))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    public long countActiveAdministrators() {
        return userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getGlobalRole, GlobalRole.ADMIN.name())
                .eq(UserEntity::getStatus, UserStatus.ACTIVE.name()));
    }

    @Transactional
    public User changeStatus(UUID userId, UserStatus status) {
        UserEntity entity = userMapper.selectById(userId);
        if (entity == null) {
            throw new ApiException("USER_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        entity.setStatus(status.name());
        entity.setUpdatedAt(Instant.now());
        userMapper.updateById(entity);
        return toDomain(entity);
    }

    public boolean passwordMatches(User user, String password) {
        return passwordEncoder.matches(password, user == null ? dummyPasswordHash : user.passwordHash());
    }

    private User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getDisplayName(),
                GlobalRole.valueOf(entity.getGlobalRole()),
                UserStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
