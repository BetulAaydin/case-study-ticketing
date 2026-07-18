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

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse register(AuthRegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("AUTH_EMAIL_EXISTS", "Email already registered: " + request.email());
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRoles(Set.of(request.role() != null ? request.role() : Role.CUSTOMER));
        user = userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(AuthLoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("AUTH_INVALID_CREDENTIALS", "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("AUTH_INVALID_CREDENTIALS", "Invalid email or password");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        refreshTokenRepository.deleteByUserId(user.getId());

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(AuthRefreshRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new BusinessException("AUTH_INVALID_REFRESH", "Invalid refresh token"));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new BusinessException("AUTH_REFRESH_EXPIRED", "Refresh token expired");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException("AUTH_USER_NOT_FOUND", "User not found"));

        refreshTokenRepository.delete(stored);

        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(AuthLogoutRequest request) {
        refreshTokenRepository.findByToken(request.refreshToken())
                .ifPresent(refreshTokenRepository::delete);
    }

    private AuthResponse buildAuthResponse(User user) {
        List<String> roles = user.getRoles().stream().map(Role::name).toList();
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), roles);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(jwtProperties.getRefreshTtlDays()));
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, refreshToken.getToken(), jwtProperties.getAccessTtlMinutes());
    }
}
