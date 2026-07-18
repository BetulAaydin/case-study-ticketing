package com.turkcell.mayacore.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMinutes
) {
    public AuthResponse(String accessToken, String refreshToken, long expiresInMinutes) {
        this(accessToken, refreshToken, "Bearer", expiresInMinutes);
    }
}
