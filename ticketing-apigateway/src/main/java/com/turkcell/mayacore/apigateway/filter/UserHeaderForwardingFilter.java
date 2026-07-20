package com.turkcell.mayacore.apigateway.filter;

import com.turkcell.mayacore.commonlibrary.util.GatewayHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

@Component
public class UserHeaderForwardingFilter extends OncePerRequestFilter {

    private final String gatewaySharedSecret;

    public UserHeaderForwardingFilter(
            @Value("${ticketing.gateway.shared-secret:ticketing-local-gateway-secret}") String gatewaySharedSecret) {
        this.gatewaySharedSecret = gatewaySharedSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Map<String, String> extraHeaders = new LinkedHashMap<>();
        extraHeaders.put(GatewayHeaders.GATEWAY_SECRET, gatewaySharedSecret);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth
                && jwtAuth.getDetails() instanceof Map<?, ?> details) {

            String userId = (String) details.get("userId");
            String sessionId = (String) details.get("sessionId");
            extraHeaders.put(GatewayHeaders.USER_ID, userId);
            extraHeaders.put(GatewayHeaders.SESSION_ID, sessionId);
            request.setAttribute("userId", userId);
        }

        filterChain.doFilter(
                new HeaderMutatingRequestWrapper(request, extraHeaders, Set.of(HttpHeaders.AUTHORIZATION)),
                response);
    }

    private static class HeaderMutatingRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String> addedHeaders;
        private final Set<String> removedHeaders;

        HeaderMutatingRequestWrapper(HttpServletRequest request,
                                     Map<String, String> addedHeaders,
                                     Set<String> removedHeaders) {
            super(request);
            this.addedHeaders = addedHeaders;
            this.removedHeaders = removedHeaders;
        }

        @Override
        public String getHeader(String name) {
            if (removedHeaders.contains(name)) return null;
            String added = addedHeaders.get(name);
            return added != null ? added : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (removedHeaders.contains(name)) return Collections.emptyEnumeration();
            String added = addedHeaders.get(name);
            if (added != null) return Collections.enumeration(List.of(added));
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                String name = original.nextElement();
                if (!removedHeaders.contains(name)) {
                    names.add(name);
                }
            }
            names.addAll(addedHeaders.keySet());
            return Collections.enumeration(names);
        }
    }
}
