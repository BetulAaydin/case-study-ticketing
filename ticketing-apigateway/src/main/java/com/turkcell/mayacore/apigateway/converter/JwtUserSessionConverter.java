package com.turkcell.mayacore.apigateway.converter;

import com.turkcell.mayacore.commonlibrary.util.RedisKeys;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

@Component
public class JwtUserSessionConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final StringRedisTemplate redisTemplate;

    public JwtUserSessionConverter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String sessionId = jwt.getClaimAsString("sid");
        if (sessionId == null) {
            throw new InvalidBearerTokenException("Missing session identifier");
        }

        Map<Object, Object> sessionData = redisTemplate.opsForHash()
                .entries(RedisKeys.userSessionKey(sessionId));

        if (sessionData.isEmpty()) {
            throw new InvalidBearerTokenException("Session expired or revoked");
        }

        String userId = (String) sessionData.get("userId");
        String email = (String) sessionData.get("email");
        String rolesStr = (String) sessionData.get("roles");

        var authorities = Arrays.stream(rolesStr.split(","))
                .map(SimpleGrantedAuthority::new)
                .toList();

        JwtAuthenticationToken authToken = new JwtAuthenticationToken(jwt, authorities);
        authToken.setDetails(Map.of("userId", userId, "sessionId", sessionId));

        return authToken;
    }
}
