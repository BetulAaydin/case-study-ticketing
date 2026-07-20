package com.turkcell.mayacore.apigateway.filter;

import com.turkcell.mayacore.apigateway.config.RateLimitProperties;
import com.turkcell.mayacore.commonlibrary.util.RedisKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public RateLimitFilter(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        LimitBucket bucket = resolveBucket(request);
        long windowId = System.currentTimeMillis() / (properties.getWindowSeconds() * 1000L);
        String key = RedisKeys.rateLimitKey(bucket.identifier(), windowId);

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(properties.getWindowSeconds()));
        }

        int remaining = Math.max(0, bucket.maxRequests() - (count != null ? count.intValue() : 0));

        response.setHeader("X-RateLimit-Limit", String.valueOf(bucket.maxRequests()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        if (count != null && count > bucket.maxRequests()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private LimitBucket resolveBucket(HttpServletRequest request) {
        String path = request.getRequestURI();
        String clientIp = resolveClientIp(request);

        if (LOGIN_PATH.equals(path) && "POST".equalsIgnoreCase(request.getMethod())) {
            return new LimitBucket("login:ip:" + clientIp, properties.getLoginMaxRequests());
        }

        Object userId = request.getAttribute("userId");
        if (userId != null) {
            return new LimitBucket("user:" + userId, properties.getAuthenticatedMaxRequests());
        }

        return new LimitBucket("anon:ip:" + clientIp, properties.getAnonymousMaxRequests());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record LimitBucket(String identifier, int maxRequests) {
    }
}
