package com.turkcell.mayacore.ticketing.service;

import com.turkcell.mayacore.commonlibrary.exception.BusinessException;
import com.turkcell.mayacore.commonlibrary.util.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class IdempotencyService {

    private static final long TTL_SECONDS = 86400;
    private static final String FIELD_REQUEST_HASH = "requestHash";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_RESPONSE_BODY = "responseBody";
    private static final String FIELD_RESPONSE_STATUS = "responseStatus";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public CachedResponse checkIdempotency(String key, String endpoint, String requestHash) {
        String redisKey = RedisKeys.idempotencyKey(key, endpoint);
        Map<Object, Object> existing = redisTemplate.opsForHash().entries(redisKey);

        if (existing.isEmpty()) {
            Boolean created = redisTemplate.opsForHash().putIfAbsent(redisKey, FIELD_STATUS, STATUS_PROCESSING);
            if (Boolean.TRUE.equals(created)) {
                redisTemplate.opsForHash().put(redisKey, FIELD_REQUEST_HASH, requestHash);
                redisTemplate.expire(redisKey, Duration.ofSeconds(TTL_SECONDS));
                return null;
            }
            existing = redisTemplate.opsForHash().entries(redisKey);
        }

        String status = (String) existing.get(FIELD_STATUS);
        String storedHash = (String) existing.get(FIELD_REQUEST_HASH);

        if (STATUS_COMPLETED.equals(status)) {
            if (storedHash != null && !storedHash.equals(requestHash)) {
                throw new BusinessException(
                        "IDEMPOTENCY_KEY_MISMATCH",
                        "Key already used with different payload");
            }
            String body = (String) existing.get(FIELD_RESPONSE_BODY);
            int code = Integer.parseInt((String) existing.get(FIELD_RESPONSE_STATUS));
            return new CachedResponse(body, code);
        }

        if (STATUS_PROCESSING.equals(status)) {
            throw new BusinessException(
                    "IDEMPOTENCY_IN_PROGRESS",
                    "Concurrent request in progress");
        }

        if (STATUS_FAILED.equals(status)) {
            redisTemplate.opsForHash().put(redisKey, FIELD_STATUS, STATUS_PROCESSING);
            redisTemplate.opsForHash().put(redisKey, FIELD_REQUEST_HASH, requestHash);
            return null;
        }

        redisTemplate.opsForHash().put(redisKey, FIELD_STATUS, STATUS_PROCESSING);
        redisTemplate.opsForHash().put(redisKey, FIELD_REQUEST_HASH, requestHash);
        redisTemplate.expire(redisKey, Duration.ofSeconds(TTL_SECONDS));
        return null;
    }

    public void complete(String key, String endpoint, String responseBody, int statusCode) {
        String redisKey = RedisKeys.idempotencyKey(key, endpoint);
        redisTemplate.opsForHash().put(redisKey, FIELD_STATUS, STATUS_COMPLETED);
        redisTemplate.opsForHash().put(redisKey, FIELD_RESPONSE_BODY, responseBody);
        redisTemplate.opsForHash().put(redisKey, FIELD_RESPONSE_STATUS, String.valueOf(statusCode));
        redisTemplate.expire(redisKey, Duration.ofSeconds(TTL_SECONDS));
    }

    public void fail(String key, String endpoint) {
        String redisKey = RedisKeys.idempotencyKey(key, endpoint);
        redisTemplate.opsForHash().put(redisKey, FIELD_STATUS, STATUS_FAILED);
        redisTemplate.expire(redisKey, Duration.ofSeconds(TTL_SECONDS));
    }
}
