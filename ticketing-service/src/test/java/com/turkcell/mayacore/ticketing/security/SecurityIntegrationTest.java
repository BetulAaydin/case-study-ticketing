package com.turkcell.mayacore.ticketing.security;

import com.turkcell.mayacore.commonlibrary.util.GatewayHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private SessionRoleResolver sessionRoleResolver;

    @Test
    void request_withoutHeaders_shouldReturn401or403() throws Exception {
        mockMvc.perform(get("/events"))
                .andExpect(status().isForbidden());
    }

    @Test
    void request_withValidHeaders_shouldReturn200() throws Exception {
        mockMvc.perform(get("/events")
                        .header(GatewayHeaders.USER_ID, "10")
                        .header(GatewayHeaders.SESSION_ID, "sid-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void publicEndpoint_shouldBeAccessibleWithoutHeaders() throws Exception {
        mockMvc.perform(get("/events/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
