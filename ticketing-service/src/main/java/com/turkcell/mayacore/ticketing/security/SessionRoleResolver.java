package com.turkcell.mayacore.ticketing.security;

import com.turkcell.mayacore.commonlibrary.util.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class SessionRoleResolver {

    private final StringRedisTemplate redisTemplate;

    public SessionRoleResolver(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAdmin(String sessionId) {
        return resolveRoles(sessionId).contains("ADMIN");
    }

    public List<String> resolveRoles(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Collections.emptyList();
        }

        Map<Object, Object> sessionData = redisTemplate.opsForHash()
                .entries(RedisKeys.userSessionKey(sessionId));

        Object roles = sessionData.get("roles");
        if (roles == null || roles.toString().isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(roles.toString().split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .toList();
    }
}
