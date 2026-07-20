package com.turkcell.mayacore.apigateway.filter;

import com.turkcell.mayacore.apigateway.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class RateLimitFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private RateLimitProperties properties;
    private RateLimitFilter filter;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setLoginMaxRequests(5);
        properties.setAuthenticatedMaxRequests(100);
        properties.setAnonymousMaxRequests(50);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        filter = new RateLimitFilter(redisTemplate, properties);

        responseBody = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @Test
    void whenDisabled_shouldSkipRedisAndContinue() throws Exception {
        properties.setEnabled(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void loginPath_shouldUseLoginBucket_andSetHeaders() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/ticket/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.8, 203.0.113.1");
        when(valueOperations.increment(anyString())).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).contains("login:ip:203.0.113.8");
        verify(redisTemplate).expire(anyString(), eq(Duration.ofSeconds(60)));
        verify(response).setHeader("X-RateLimit-Limit", "5");
        verify(response).setHeader("X-RateLimit-Remaining", "4");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticatedRequest_shouldUseUserBucket() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/ticket/events");
        when(request.getMethod()).thenReturn("GET");
        when(request.getAttribute("userId")).thenReturn("42");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(valueOperations.increment(anyString())).thenReturn(2L);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).contains("user:42");
        verify(response).setHeader("X-RateLimit-Limit", "100");
        verify(filterChain).doFilter(request, response);
        verify(redisTemplate, never()).expire(anyString(), any());
    }

    @Test
    void anonymousRequest_shouldUseAnonIpBucket() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/ticket/events/public");
        when(request.getMethod()).thenReturn("GET");
        when(request.getAttribute("userId")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(valueOperations.increment(anyString())).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).contains("anon:ip:203.0.113.10");
        verify(response).setHeader("X-RateLimit-Limit", "50");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void whenLimitExceeded_shouldReturn429_andNotContinue() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/ticket/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        when(valueOperations.increment(anyString())).thenReturn(6L);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        verify(response).setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
        assertThat(responseBody.toString()).contains("Rate limit exceeded");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void blankForwardedFor_shouldFallBackToRemoteAddr() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/ticket/events/public");
        when(request.getMethod()).thenReturn("GET");
        when(request.getAttribute("userId")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getRemoteAddr()).thenReturn("9.9.9.9");
        when(valueOperations.increment(anyString())).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).contains("anon:ip:9.9.9.9");
    }
}
