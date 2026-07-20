package com.turkcell.mayacore.apigateway.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Configuration
public class SecurityExceptionHandlerConfig {

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> write(
                response,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Authentication required. Send Authorization: Bearer <accessToken> via Gateway.");
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> write(
                response,
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Yetkiniz bulunmamaktadir. Endpoint role uyumsuz (event create -> ORGANIZER/ADMIN, reservation -> CUSTOMER).");
    }

    private static void write(HttpServletResponse response,
                              HttpStatus status,
                              String code,
                              String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = """
                {"success":false,"data":null,"errorCode":"%s","errorMessage":"%s","timestamp":"%s"}
                """.formatted(code, message.replace("\"", "'"), LocalDateTime.now());
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
