package com.turkcell.mayacore.ticketing.service;

import com.turkcell.mayacore.commonlibrary.exception.BusinessException;
import com.turkcell.mayacore.commonlibrary.util.RedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        idempotencyService = new IdempotencyService(redisTemplate);
    }

    @Test
    void check_firstRequest_shouldReturnNull() {
        String redisKey = RedisKeys.idempotencyKey("k1", "/events/1/reservations");
        when(hashOperations.entries(redisKey)).thenReturn(Map.of());
        when(hashOperations.putIfAbsent(eq(redisKey), eq("status"), eq("PROCESSING"))).thenReturn(true);

        CachedResponse result = idempotencyService.checkIdempotency("k1", "/events/1/reservations", "hash-a");

        assertThat(result).isNull();
        verify(hashOperations).put(redisKey, "requestHash", "hash-a");
        verify(redisTemplate).expire(eq(redisKey), any(Duration.class));
    }

    @Test
    void check_completedRequest_shouldReturnCachedResponse() {
        String redisKey = RedisKeys.idempotencyKey("k1", "/events/1/reservations");
        when(hashOperations.entries(redisKey)).thenReturn(Map.of(
                "status", "COMPLETED",
                "requestHash", "hash-a",
                "responseBody", "{\"id\":1}",
                "responseStatus", "201"
        ));

        CachedResponse result = idempotencyService.checkIdempotency("k1", "/events/1/reservations", "hash-a");

        assertThat(result).isNotNull();
        assertThat(result.responseBody()).isEqualTo("{\"id\":1}");
        assertThat(result.statusCode()).isEqualTo(201);
    }

    @Test
    void check_differentHash_shouldThrow() {
        String redisKey = RedisKeys.idempotencyKey("k1", "/events/1/reservations");
        when(hashOperations.entries(redisKey)).thenReturn(Map.of(
                "status", "COMPLETED",
                "requestHash", "hash-a",
                "responseBody", "{}",
                "responseStatus", "201"
        ));

        assertThatThrownBy(() ->
                idempotencyService.checkIdempotency("k1", "/events/1/reservations", "hash-b"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("IDEMPOTENCY_KEY_MISMATCH");
    }

    @Test
    void check_processingRequest_shouldThrow() {
        String redisKey = RedisKeys.idempotencyKey("k1", "/events/1/reservations");
        when(hashOperations.entries(redisKey)).thenReturn(Map.of(
                "status", "PROCESSING",
                "requestHash", "hash-a"
        ));

        assertThatThrownBy(() ->
                idempotencyService.checkIdempotency("k1", "/events/1/reservations", "hash-a"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("IDEMPOTENCY_IN_PROGRESS");
    }

    @Test
    void complete_shouldSetCompletedWithResponse() {
        String redisKey = RedisKeys.idempotencyKey("k1", "/events/1/reservations");

        idempotencyService.complete("k1", "/events/1/reservations", "{\"ok\":true}", 201);

        verify(hashOperations).put(redisKey, "status", "COMPLETED");
        verify(hashOperations).put(redisKey, "responseBody", "{\"ok\":true}");
        verify(hashOperations).put(redisKey, "responseStatus", "201");
    }

    @Test
    void fail_shouldSetFailedStatus() {
        String redisKey = RedisKeys.idempotencyKey("k1", "/events/1/reservations");

        idempotencyService.fail("k1", "/events/1/reservations");

        verify(hashOperations).put(redisKey, "status", "FAILED");
    }

    @Test
    void check_failedRequest_shouldAllowRetry() {
        String redisKey = RedisKeys.idempotencyKey("k1", "/events/1/reservations");
        Map<Object, Object> existing = new HashMap<>();
        existing.put("status", "FAILED");
        existing.put("requestHash", "hash-a");
        when(hashOperations.entries(redisKey)).thenReturn(existing);

        CachedResponse result = idempotencyService.checkIdempotency("k1", "/events/1/reservations", "hash-a");

        assertThat(result).isNull();
        verify(hashOperations).put(redisKey, "status", "PROCESSING");
    }
}
