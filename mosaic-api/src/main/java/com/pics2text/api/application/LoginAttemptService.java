package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private final StringRedisTemplate redisTemplate;
    private final int maximumAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(
            StringRedisTemplate redisTemplate,
            @Value("${identity.login.maximum-attempts:5}") int maximumAttempts,
            @Value("${identity.login.lock-duration:15m}") Duration lockDuration) {
        this.redisTemplate = redisTemplate;
        this.maximumAttempts = maximumAttempts;
        this.lockDuration = lockDuration;
    }

    public void verifyAllowed(String username, String clientIp) {
        String value = redisTemplate.opsForValue().get(key(username, clientIp));
        if (value != null && Long.parseLong(value) >= maximumAttempts) {
            throw new ApiException("LOGIN_RATE_LIMITED", "登录失败次数过多，请稍后重试", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    public void recordFailure(String username, String clientIp) {
        String key = key(username, clientIp);
        Long failures = redisTemplate.opsForValue().increment(key);
        if (failures != null && failures == 1) {
            redisTemplate.expire(key, lockDuration);
        }
    }

    public void clear(String username, String clientIp) {
        redisTemplate.delete(key(username, clientIp));
    }

    private String key(String username, String clientIp) {
        return "pics2text:auth:failure:" + sha256(username + "|" + clientIp);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
