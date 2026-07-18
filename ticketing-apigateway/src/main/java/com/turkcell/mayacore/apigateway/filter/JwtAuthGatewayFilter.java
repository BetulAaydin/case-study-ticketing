package com.turkcell.mayacore.apigateway.filter;

import com.turkcell.mayacore.commonlibrary.security.JwtService;
import com.turkcell.mayacore.commonlibrary.util.GatewayHeaders;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String USER_ID_ATTR = "userId";

    private final JwtService jwtService;

    public JwtAuthGatewayFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    private record AuthRule(String pattern, HttpMethod method, Set<String> allowedRoles) {}

    private static final List<AuthRule> AUTH_RULES = List.of(
            new AuthRule("/api/events/**", HttpMethod.POST, Set.of("ORGANIZER", "ADMIN")),
            new AuthRule("/api/events/**", HttpMethod.PUT, Set.of("ORGANIZER", "ADMIN")),
            new AuthRule("/api/events", HttpMethod.GET, Set.of()),
            new AuthRule("/api/events/*/reservations", HttpMethod.POST, Set.of("CUSTOMER")),
            new AuthRule("/api/reservations/*/confirm", HttpMethod.POST, Set.of("CUSTOMER")),
            new AuthRule("/api/reservations/*/cancel", HttpMethod.POST, Set.of("CUSTOMER"))
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (isPublicPath(path, method)) {
            return chain.filter(stripAuthHeader(exchange));
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "AUTH_MISSING_TOKEN", "Authorization header required");
        }

        String token = authHeader.substring(7);
        if (!jwtService.validateToken(token)) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "Invalid or expired token");
        }

        Claims claims = jwtService.extractClaims(token);
        Number uidNum = claims.get("uid", Number.class);
        long uid = uidNum.longValue();
        String email = claims.getSubject();
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);

        if (!isAuthorized(path, method, roles)) {
            return writeError(exchange, HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "Insufficient permissions");
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(GatewayHeaders.USER_ID, String.valueOf(uid))
                .header(GatewayHeaders.USER_EMAIL, email)
                .header(GatewayHeaders.USER_ROLES, String.join(",", roles))
                .headers(h -> h.remove(HttpHeaders.AUTHORIZATION))
                .build();

        exchange.getAttributes().put(USER_ID_ATTR, String.valueOf(uid));

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isPublicPath(String path, HttpMethod method) {
        if (PATH_MATCHER.match("/api/auth/**", path)) return true;
        if (PATH_MATCHER.match("/actuator/**", path)) return true;
        if (method == HttpMethod.GET && PATH_MATCHER.match("/api/events/public/**", path)) return true;
        return false;
    }

    private boolean isAuthorized(String path, HttpMethod method, List<String> userRoles) {
        for (AuthRule rule : AUTH_RULES) {
            if (rule.method() == method && PATH_MATCHER.match(rule.pattern(), path)) {
                if (rule.allowedRoles().isEmpty()) {
                    return true;
                }
                return userRoles.stream().anyMatch(rule.allowedRoles()::contains);
            }
        }
        return false;
    }

    private ServerWebExchange stripAuthHeader(ServerWebExchange exchange) {
        if (exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION) != null) {
            ServerHttpRequest req = exchange.getRequest().mutate()
                    .headers(h -> h.remove(HttpHeaders.AUTHORIZATION))
                    .build();
            return exchange.mutate().request(req).build();
        }
        return exchange;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"errorCode\":\"%s\",\"errorMessage\":\"%s\",\"data\":null,\"timestamp\":\"%s\"}"
                .formatted(code, message, LocalDateTime.now());
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
