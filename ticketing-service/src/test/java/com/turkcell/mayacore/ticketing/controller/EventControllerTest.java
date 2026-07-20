package com.turkcell.mayacore.ticketing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.mayacore.commonlibrary.util.GatewayHeaders;
import com.turkcell.mayacore.ticketing.dto.EventCreateRequest;
import com.turkcell.mayacore.ticketing.dto.EventUpdateRequest;
import com.turkcell.mayacore.ticketing.security.SessionRoleResolver;
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

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private SessionRoleResolver sessionRoleResolver;

    private final LocalDateTime starts = LocalDateTime.of(2026, 9, 1, 19, 0);
    private final LocalDateTime ends = LocalDateTime.of(2026, 9, 1, 22, 0);

    @Test
    void createEvent_shouldReturn201() throws Exception {
        when(sessionRoleResolver.isAdmin(anyString())).thenReturn(false);
        EventCreateRequest request = new EventCreateRequest("Jazz Night", "Club", starts, ends, 50);

        mockMvc.perform(post("/events")
                        .header(GatewayHeaders.USER_ID, "10")
                        .header(GatewayHeaders.SESSION_ID, "sid-org")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Jazz Night"))
                .andExpect(jsonPath("$.data.published").value(false));
    }

    @Test
    void updateEvent_shouldReturn200() throws Exception {
        Long eventId = createEventAsOwner(10L);
        EventUpdateRequest update = new EventUpdateRequest("Jazz Night Updated", null, null, null, null);

        mockMvc.perform(put("/events/" + eventId)
                        .header(GatewayHeaders.USER_ID, "10")
                        .header(GatewayHeaders.SESSION_ID, "sid-org")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Jazz Night Updated"));
    }

    @Test
    void publishEvent_shouldReturn200() throws Exception {
        Long eventId = createEventAsOwner(10L);

        mockMvc.perform(post("/events/" + eventId + "/publish")
                        .header(GatewayHeaders.USER_ID, "10")
                        .header(GatewayHeaders.SESSION_ID, "sid-org"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.published").value(true));
    }

    @Test
    void listEvents_shouldReturn200() throws Exception {
        createEventAsOwner(10L);

        mockMvc.perform(get("/events")
                        .header(GatewayHeaders.USER_ID, "10")
                        .header(GatewayHeaders.SESSION_ID, "sid-org")
                        .param("ownerId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void publicSearch_shouldReturn200_withoutAuth() throws Exception {
        mockMvc.perform(get("/events/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void createEvent_shouldReturn403_withoutUserHeader() throws Exception {
        EventCreateRequest request = new EventCreateRequest("Jazz Night", "Club", starts, ends, 50);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    private Long createEventAsOwner(long ownerId) throws Exception {
        EventCreateRequest request = new EventCreateRequest("Jazz Night", "Club", starts, ends, 50);
        MvcResult result = mockMvc.perform(post("/events")
                        .header(GatewayHeaders.USER_ID, String.valueOf(ownerId))
                        .header(GatewayHeaders.SESSION_ID, "sid-org")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }
}
