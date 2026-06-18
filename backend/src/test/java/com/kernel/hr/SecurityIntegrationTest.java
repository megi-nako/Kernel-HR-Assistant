package com.kernel.hr;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies HTTP-level security: unauthenticated requests to /api/chat are rejected
 * by Spring Security before reaching the controller, and login with a non-existent
 * mock profile is denied with 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void chat_withoutSession_returns401() throws Exception {
        mvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"How many vacation days?\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withNonExistentProfile_returns403() throws Exception {
        mvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ghost_user_does_not_exist\"}"))
                .andExpect(status().isForbidden());
    }
}
