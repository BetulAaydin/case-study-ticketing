package com.turkcell.mayacore.auth.service;

import com.turkcell.mayacore.auth.domain.RefreshToken;
import com.turkcell.mayacore.auth.domain.Role;
import com.turkcell.mayacore.auth.domain.User;
import com.turkcell.mayacore.auth.dto.*;
import com.turkcell.mayacore.auth.repository.RefreshTokenRepository;
import com.turkcell.mayacore.auth.repository.UserRepository;
import com.turkcell.mayacore.commonlibrary.exception.BusinessException;
import com.turkcell.mayacore.commonlibrary.security.JwtProperties;
import com.turkcell.mayacore.commonlibrary.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionService userSessionService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       PasswordEncoder passwordEncoder,
                       UserSessionService userSessionService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.userSessionService = userSessionService;
    }

    @Transactional
    public AuthResponse register(AuthRegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(
                    "AUTH_EMAIL_EXISTS",
                    "Email already registered: " + request.email(),
                    HttpStatus.CONFLICT);
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // Public register is always CUSTOMER; ADMIN/ORGANIZER only via seed / ops.
        user.setRoles(Set.of(Role.CUSTOMER));
        user = userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(AuthLoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(
                        "AUTH_INVALID_CREDENTIALS",
                        "Invalid email or password",
                        HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(
                    "AUTH_INVALID_CREDENTIALS",
                    "Invalid email or password",
                    HttpStatus.UNAUTHORIZED);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        invalidateExistingSessions(user.getId());

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(AuthRefreshRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new BusinessException(
                        "AUTH_INVALID_REFRESH",
                        "Invalid refresh token",
                        HttpStatus.UNAUTHORIZED));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            userSessionService.deleteSession(stored.getSessionId());
            refreshTokenRepository.delete(stored);
            throw new BusinessException(
                    "AUTH_REFRESH_EXPIRED",
                    "Refresh token expired",
                    HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException(
                        "AUTH_USER_NOT_FOUND",
                        "User not found",
                        HttpStatus.NOT_FOUND));

        String sessionId = stored.getSessionId();
        List<String> roles = user.getRoles().stream().map(Role::name).toList();
        userSessionService.saveSession(sessionId, user.getId(), user.getEmail(), roles);

        String accessToken = jwtService.generateToken(sessionId, user.getEmail());

        refreshTokenRepository.delete(stored);

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUserId(user.getId());
        newRefreshToken.setToken(UUID.randomUUID().toString());
        newRefreshToken.setSessionId(sessionId);
        newRefreshToken.setExpiresAt(LocalDateTime.now().plusDays(jwtProperties.getRefreshTtlDays()));
        refreshTokenRepository.save(newRefreshToken);

        return new AuthResponse(accessToken, newRefreshToken.getToken(), jwtProperties.getAccessTtlMinutes());
    }

    @Transactional
    public void logout(AuthLogoutRequest request) {
        refreshTokenRepository.findByToken(request.refreshToken())
                .ifPresent(rt -> {
                    userSessionService.deleteSession(rt.getSessionId());
                    refreshTokenRepository.delete(rt);
                });
    }

    private void invalidateExistingSessions(Long userId) {
        refreshTokenRepository.findAllByUserId(userId).forEach(rt -> {
            userSessionService.deleteSession(rt.getSessionId());
        });
        refreshTokenRepository.deleteByUserId(userId);
    }

    private AuthResponse buildAuthResponse(User user) {
        List<String> roles = user.getRoles().stream().map(Role::name).toList();

        String sessionId = userSessionService.createSession(user.getId(), user.getEmail(), roles);
        String accessToken = jwtService.generateToken(sessionId, user.getEmail());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setSessionId(sessionId);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(jwtProperties.getRefreshTtlDays()));
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, refreshToken.getToken(), jwtProperties.getAccessTtlMinutes());
    }
}
