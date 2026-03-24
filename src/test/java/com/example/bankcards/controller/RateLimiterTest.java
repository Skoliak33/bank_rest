package com.example.bankcards.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тест rate limiter на /api/auth/login.
 * Поднимает полный контекст с H2 (профиль test), чтобы Resilience4j был активен.
 * После 5 запросов в минуту - 429 Too Many Requests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimiterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void login_rateLimited_shouldReturn429() throws Exception {
        String body = "{\"email\":\"admin@bank.com\",\"password\":\"wrong\"}";

        // 5 запросов - в пределах лимита (могут быть 401, это нормально)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));
        }

        // 6-й запрос - должен вернуть 429
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many login attempts, please try again later"));
    }
}
