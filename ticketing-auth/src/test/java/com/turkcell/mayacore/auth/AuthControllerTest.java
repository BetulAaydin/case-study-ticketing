package com.turkcell.mayacore.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.mayacore.auth.domain.RefreshToken;
import com.turkcell.mayacore.auth.dto.AuthLoginRequest;
import com.turkcell.mayacore.auth.dto.AuthLogoutRequest;
import com.turkcell.mayacore.auth.dto.AuthRefreshRequest;
import com.turkcell.mayacore.auth.dto.AuthRegisterRequest;
import com.turkcell.mayacore.auth.repository.RefreshTokenRepository;
import com.turkcell.mayacore.auth.service.UserSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private UserSessionService userSessionService;

    @Test
    void register_shouldReturn201_withValidInput() throws Exception {
        when(userSessionService.createSession(anyLong(), anyString(), anyList())).thenReturn("sid-new");

        AuthRegisterRequest request = new AuthRegisterRequest(
                "fresh-" + System.nanoTime() + "@test.com", "ChangeMe123!");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value(not(nullValue())))
                .andExpect(jsonPath("$.data.refreshToken").value(not(nullValue())));
    }

    @Test
    void register_shouldReturn400_withInvalidEmail() throws Exception {
        AuthRegisterRequest request = new AuthRegisterRequest("not-an-email", "ChangeMe123!");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_shouldFail_withDuplicateEmail() throws Exception {
        when(userSessionService.createSession(anyLong(), anyString(), anyList())).thenReturn("sid-dup");
        String email = "dup-" + System.nanoTime() + "@test.com";
        AuthRegisterRequest request = new AuthRegisterRequest(email, "ChangeMe123!");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AUTH_EMAIL_EXISTS"));
    }

    @Test
    void login_shouldReturn200_withValidCredentials() throws Exception {
        when(userSessionService.createSession(anyLong(), anyString(), anyList())).thenReturn("sid-login");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthLoginRequest("customer@ticketing.com", "ChangeMe123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString());
    }

    @Test
    void login_shouldFail_withWrongPassword() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthLoginRequest("customer@ticketing.com", "WrongPassword!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.errorMessage").value("Invalid email or password"));
    }

    @Test
    void refresh_shouldReturn200_withValidToken() throws Exception {
        when(userSessionService.createSession(anyLong(), anyString(), anyList())).thenReturn("sid-rf");
        doNothing().when(userSessionService).saveSession(anyString(), anyLong(), anyString(), anyList());

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthLoginRequest("organizer@ticketing.com", "ChangeMe123!"))))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString());
    }

    @Test
    void refresh_shouldFail_withExpiredToken() throws Exception {
        RefreshToken expired = new RefreshToken();
        expired.setUserId(1L);
        expired.setToken("expired-rt");
        expired.setSessionId("sid-exp");
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        refreshTokenRepository.save(expired);
        doNothing().when(userSessionService).deleteSession(anyString());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRefreshRequest("expired-rt"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AUTH_REFRESH_EXPIRED"));
    }

    @Test
    void logout_shouldReturn200_andInvalidateToken() throws Exception {
        when(userSessionService.createSession(anyLong(), anyString(), anyList())).thenReturn("sid-out");
        doNothing().when(userSessionService).deleteSession(anyString());

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthLoginRequest("admin@ticketing.com", "ChangeMe123!"))))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthLogoutRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_REFRESH"));
    }
}
