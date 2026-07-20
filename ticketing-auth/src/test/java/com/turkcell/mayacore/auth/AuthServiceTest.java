package com.turkcell.mayacore.auth;

import com.turkcell.mayacore.auth.domain.RefreshToken;
import com.turkcell.mayacore.auth.domain.Role;
import com.turkcell.mayacore.auth.domain.User;
import com.turkcell.mayacore.auth.dto.*;
import com.turkcell.mayacore.auth.repository.RefreshTokenRepository;
import com.turkcell.mayacore.auth.repository.UserRepository;
import com.turkcell.mayacore.auth.service.AuthService;
import com.turkcell.mayacore.auth.service.UserSessionService;
import com.turkcell.mayacore.commonlibrary.exception.BusinessException;
import com.turkcell.mayacore.commonlibrary.security.JwtProperties;
import com.turkcell.mayacore.commonlibrary.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserSessionService userSessionService;

    private JwtProperties jwtProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setAccessTtlMinutes(30);
        jwtProperties.setRefreshTtlDays(7);
        authService = new AuthService(
                userRepository, refreshTokenRepository, jwtService,
                jwtProperties, passwordEncoder, userSessionService);
    }

    @Test
    void register_shouldHashPassword_andGenerateTokens() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("ChangeMe123!")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(userSessionService.createSession(eq(1L), eq("new@test.com"), anyList())).thenReturn("sid-1");
        when(jwtService.generateToken("sid-1", "new@test.com")).thenReturn("access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.register(
                new AuthRegisterRequest("new@test.com", "ChangeMe123!"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void login_shouldUpdateLastLoginAt() {
        User user = user(1L, "u@test.com", "hashed", Role.CUSTOMER);
        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ChangeMe123!", "hashed")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.findAllByUserId(1L)).thenReturn(List.of());
        when(userSessionService.createSession(eq(1L), eq("u@test.com"), anyList())).thenReturn("sid-2");
        when(jwtService.generateToken("sid-2", "u@test.com")).thenReturn("token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.login(new AuthLoginRequest("u@test.com", "ChangeMe123!"));

        assertThat(user.getLastLoginAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void refresh_shouldRotateRefreshToken() {
        RefreshToken stored = new RefreshToken();
        stored.setId(5L);
        stored.setUserId(1L);
        stored.setToken("old-refresh");
        stored.setSessionId("sid-keep");
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));

        User user = user(1L, "u@test.com", "hashed", Role.CUSTOMER);
        when(refreshTokenRepository.findByToken("old-refresh")).thenReturn(Optional.of(stored));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken("sid-keep", "u@test.com")).thenReturn("new-access");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.refresh(new AuthRefreshRequest("old-refresh"));

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isNotEqualTo("old-refresh");
        verify(refreshTokenRepository).delete(stored);
        verify(userSessionService).saveSession(eq("sid-keep"), eq(1L), eq("u@test.com"), anyList());
    }

    @Test
    void logout_shouldDeleteRefreshToken() {
        RefreshToken stored = new RefreshToken();
        stored.setToken("rt-1");
        stored.setSessionId("sid-1");
        when(refreshTokenRepository.findByToken("rt-1")).thenReturn(Optional.of(stored));

        authService.logout(new AuthLogoutRequest("rt-1"));

        verify(userSessionService).deleteSession("sid-1");
        verify(refreshTokenRepository).delete(stored);
    }

    @Test
    void login_shouldThrow_whenWrongPassword() {
        User user = user(1L, "u@test.com", "hashed", Role.CUSTOMER);
        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new AuthLoginRequest("u@test.com", "bad")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    private User user(Long id, String email, String hash, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash(hash);
        user.setRoles(Set.of(role));
        return user;
    }
}
