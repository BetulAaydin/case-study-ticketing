package com.turkcell.mayacore.commonlibrary.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;

public class JwtService {

    private final SecretKey signingKey;
    private final long accessTtlMs;

    public JwtService(JwtProperties properties) {
        byte[] decoded = Base64.getDecoder().decode(properties.getSecret());
        this.signingKey = new SecretKeySpec(decoded, "HmacSHA256");
        this.accessTtlMs = properties.getAccessTtlMinutes() * 60 * 1000;
    }

    public String generateToken(Long uid, String email, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTtlMs);

        return Jwts.builder()
                .subject(email)
                .claim("uid", uid)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
