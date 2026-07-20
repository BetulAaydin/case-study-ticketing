package com.turkcell.mayacore.apigateway.converter;

import com.turkcell.mayacore.commonlibrary.util.RedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtUserSessionConverterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private JwtUserSessionConverter converter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        converter = new JwtUserSessionConverter(redisTemplate);
    }

    @Test
    void convert_shouldLoadSessionAndAuthorities() {
        Jwt jwt = jwtWithSid("sid-abc");
        when(hashOperations.entries(RedisKeys.userSessionKey("sid-abc"))).thenReturn(Map.of(
                "userId", "7",
                "email", "org@test.com",
                "roles", "ORGANIZER,ADMIN"
        ));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(token.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("ORGANIZER", "ADMIN");
        assertThat(token.getDetails()).isEqualTo(Map.of("userId", "7", "sessionId", "sid-abc"));
    }

    @Test
    void convert_shouldThrow_whenSidMissing() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject("u@test.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("Missing session identifier");
    }

    @Test
    void convert_shouldThrow_whenSessionMissingInRedis() {
        Jwt jwt = jwtWithSid("sid-gone");
        when(hashOperations.entries(RedisKeys.userSessionKey("sid-gone"))).thenReturn(Map.of());

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("Session expired or revoked");
    }

    private static Jwt jwtWithSid(String sid) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("sid", sid)
                .subject("user@test.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
