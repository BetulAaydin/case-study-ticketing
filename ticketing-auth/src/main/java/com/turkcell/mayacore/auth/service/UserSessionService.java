package com.turkcell.mayacore.auth.service;

import com.turkcell.mayacore.commonlibrary.security.JwtProperties;
import com.turkcell.mayacore.commonlibrary.util.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserSessionService {

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public UserSessionService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    public String createSession(Long userId, String email, List<String> roles) {
        String sessionId = UUID.randomUUID().toString();
        saveSession(sessionId, userId, email, roles);
        return sessionId;
    }

    /**
     * Session verisini ayni sessionId ile yeniden yazar ve TTL'i sifirlar.
     * Refresh akisinda Redis session TTL ile silinmis olabilecegi icin
     * sadece expire uzatmak yerine hash yeniden olusturulur.
     */
    public void saveSession(String sessionId, Long userId, String email, List<String> roles) {
        String key = RedisKeys.userSessionKey(sessionId);

        Map<String, String> sessionData = Map.of(
                "userId", String.valueOf(userId),
                "email", email,
                "roles", String.join(",", roles)
        );

        redisTemplate.opsForHash().putAll(key, sessionData);
        redisTemplate.expire(key, Duration.ofMinutes(jwtProperties.getAccessTtlMinutes()));
    }

    public void deleteSession(String sessionId) {
        if (sessionId != null) {
            redisTemplate.delete(RedisKeys.userSessionKey(sessionId));
        }
    }
}
