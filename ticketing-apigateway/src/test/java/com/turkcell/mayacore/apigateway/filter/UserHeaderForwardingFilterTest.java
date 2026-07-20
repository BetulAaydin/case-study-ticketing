package com.turkcell.mayacore.apigateway.filter;

import com.turkcell.mayacore.commonlibrary.util.GatewayHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserHeaderForwardingFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private final UserHeaderForwardingFilter filter = new UserHeaderForwardingFilter();

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void withJwtDetails_shouldInjectUserAndSessionHeaders_andStripAuthorization() throws Exception {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject("u@test.com")
                .claim("sid", "sid-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of());
        auth.setDetails(Map.of("userId", "99", "sessionId", "sid-1"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of(HttpHeaders.AUTHORIZATION, "Accept")));
        when(request.getHeader(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            if (HttpHeaders.AUTHORIZATION.equals(name)) return "Bearer t";
            if ("Accept".equals(name)) return "application/json";
            return null;
        });
        when(request.getHeaders(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            if (HttpHeaders.AUTHORIZATION.equals(name)) {
                return Collections.enumeration(List.of("Bearer t"));
            }
            if ("Accept".equals(name)) {
                return Collections.enumeration(List.of("application/json"));
            }
            return Collections.emptyEnumeration();
        });

        filter.doFilterInternal(request, response, filterChain);

        verify(request).setAttribute("userId", "99");
        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), eq(response));

        HttpServletRequest wrapped = captor.getValue();
        assertThat(wrapped.getHeader(GatewayHeaders.USER_ID)).isEqualTo("99");
        assertThat(wrapped.getHeader(GatewayHeaders.SESSION_ID)).isEqualTo("sid-1");
        assertThat(wrapped.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(wrapped.getHeader("Accept")).isEqualTo("application/json");
        assertThat(Collections.list(wrapped.getHeaders(GatewayHeaders.USER_ID))).containsExactly("99");
        assertThat(Collections.list(wrapped.getHeaders(HttpHeaders.AUTHORIZATION))).isEmpty();
        assertThat(Collections.list(wrapped.getHeaderNames()))
                .contains(GatewayHeaders.USER_ID, GatewayHeaders.SESSION_ID, "Accept")
                .doesNotContain(HttpHeaders.AUTHORIZATION);
    }

    @Test
    void withoutJwt_shouldStillStripAuthorizationHeader() throws Exception {
        SecurityContextHolder.clearContext();
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of(HttpHeaders.AUTHORIZATION)));
        when(request.getHeader(anyString())).thenAnswer(inv ->
                HttpHeaders.AUTHORIZATION.equals(inv.getArgument(0)) ? "Bearer leaked" : null);
        when(request.getHeaders(anyString())).thenAnswer(inv ->
                HttpHeaders.AUTHORIZATION.equals(inv.getArgument(0))
                        ? Collections.enumeration(List.of("Bearer leaked"))
                        : Collections.emptyEnumeration());

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), eq(response));
        assertThat(captor.getValue().getHeader(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(captor.getValue().getHeader(GatewayHeaders.USER_ID)).isNull();
    }

    @Test
    void jwtWithoutMapDetails_shouldTreatAsUnauthenticatedPath() throws Exception {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject("u@test.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getHeaders(anyString())).thenReturn(Collections.emptyEnumeration());

        filter.doFilterInternal(request, response, filterChain);

        verify(request, never()).setAttribute(eq("userId"), any());
        verify(filterChain).doFilter(any(HttpServletRequest.class), eq(response));
    }
}
