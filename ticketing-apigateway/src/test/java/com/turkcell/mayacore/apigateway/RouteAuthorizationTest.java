package com.turkcell.mayacore.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RouteAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void authEndpoint_shouldBeAccessibleWithoutToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ticket/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"x\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void eventsEndpoint_shouldReturn401_withoutToken() throws Exception {
        mockMvc.perform(get("/api/ticket/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eventsEndpoint_shouldReturn403_forCustomer() throws Exception {
        mockMvc.perform(post("/api/ticket/events")
                        .with(jwt().authorities(new SimpleGrantedAuthority("CUSTOMER"))
                                .jwt(j -> j.claim("sid", "sid-c").subject("c@test.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void eventsEndpoint_shouldAllowOrganizer() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ticket/events")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ORGANIZER"))
                                .jwt(j -> j.claim("sid", "sid-o").subject("o@test.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"venue\":\"V\",\"startsAt\":\"2026-09-01T19:00:00\",\"endsAt\":\"2026-09-01T22:00:00\",\"capacity\":10}"))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("ORGANIZER must pass coarse-grained AuthZ")
                .isNotIn(401, 403);
    }

    @Test
    void reservationEndpoint_shouldReturn403_forOrganizer() throws Exception {
        mockMvc.perform(post("/api/ticket/events/1/reservations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ORGANIZER"))
                                .jwt(j -> j.claim("sid", "sid-o2").subject("o@test.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seats\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicEndpoint_shouldBeAccessibleWithoutToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ticket/events/public")).andReturn();
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }
}
