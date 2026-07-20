package com.turkcell.mayacore.ticketing.security;

import com.turkcell.mayacore.commonlibrary.util.GatewayHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

/**
 * Rejects client-forged identity headers unless the shared gateway secret is present.
 */
@Component
public class TrustedGatewayHeadersFilter extends OncePerRequestFilter {

    private static final Set<String> IDENTITY_HEADERS = Set.of(
            GatewayHeaders.USER_ID.toLowerCase(),
            GatewayHeaders.SESSION_ID.toLowerCase()
    );

    private final String sharedSecret;

    public TrustedGatewayHeadersFilter(
            @Value("${ticketing.gateway.shared-secret:ticketing-local-gateway-secret}") String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String secret = request.getHeader(GatewayHeaders.GATEWAY_SECRET);
        if (sharedSecret.equals(secret)) {
            filterChain.doFilter(request, response);
            return;
        }
        filterChain.doFilter(new StrippingRequestWrapper(request), response);
    }

    private static final class StrippingRequestWrapper extends HttpServletRequestWrapper {

        StrippingRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            if (name != null && IDENTITY_HEADERS.contains(name.toLowerCase())) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (name != null && IDENTITY_HEADERS.contains(name.toLowerCase())) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames()).stream()
                    .filter(n -> !IDENTITY_HEADERS.contains(n.toLowerCase()))
                    .toList();
            return Collections.enumeration(names);
        }
    }
}
