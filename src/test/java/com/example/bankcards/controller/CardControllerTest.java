package com.example.bankcards.controller;

import com.example.bankcards.config.SecurityConfig;
import com.example.bankcards.dto.card.CardResponse;
import com.example.bankcards.entity.CardStatus;
import com.example.bankcards.security.CustomUserDetailsService;
import com.example.bankcards.security.JwtTokenProvider;
import com.example.bankcards.service.CardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardController.class)
@Import(SecurityConfig.class)
class CardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardService cardService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void getCards_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/cards"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void createCard_asAdmin_shouldReturn201() throws Exception {
        CardResponse response = new CardResponse(
                1L, "**** **** **** 1234", "Test User", 1L,
                LocalDate.now().plusYears(3), CardStatus.ACTIVE, BigDecimal.ZERO);
        when(cardService.createCard(any())).thenReturn(response);

        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedNumber").value("**** **** **** 1234"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void createCard_asUser_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void deleteCard_asUser_shouldReturn403() throws Exception {
        mockMvc.perform(delete("/api/cards/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void deleteCard_asAdmin_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/cards/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void activateCard_asAdmin_shouldReturnCard() throws Exception {
        CardResponse response = new CardResponse(
                1L, "**** **** **** 1234", "Test User", 1L,
                LocalDate.now().plusYears(3), CardStatus.ACTIVE, BigDecimal.ZERO);
        when(cardService.activateCard(eq(1L))).thenReturn(response);

        mockMvc.perform(patch("/api/cards/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void activateCard_asUser_shouldReturn403() throws Exception {
        mockMvc.perform(patch("/api/cards/1/activate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void getCardById_asUser_shouldReturnCard() throws Exception {
        CardResponse response = new CardResponse(
                1L, "**** **** **** 1234", "Test User", 1L,
                LocalDate.now().plusYears(3), CardStatus.ACTIVE, BigDecimal.ZERO);
        when(cardService.getCardById(eq(1L), any())).thenReturn(response);

        mockMvc.perform(get("/api/cards/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.maskedNumber").value("**** **** **** 1234"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void transfer_asUser_shouldReturnCard() throws Exception {
        CardResponse response = new CardResponse(
                1L, "**** **** **** 1234", "Test User", 1L,
                LocalDate.now().plusYears(3), CardStatus.ACTIVE, new BigDecimal("800.00"));
        when(cardService.transfer(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/cards/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceCardId\":1,\"targetCardId\":2,\"amount\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(800.00));
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void transfer_invalidBody_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/cards/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceCardId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void blockCard_asUser_shouldReturnCard() throws Exception {
        CardResponse response = new CardResponse(
                1L, "**** **** **** 1234", "Test User", 1L,
                LocalDate.now().plusYears(3), CardStatus.BLOCKED, BigDecimal.ZERO);
        when(cardService.blockCard(eq(1L), any())).thenReturn(response);

        mockMvc.perform(patch("/api/cards/1/block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }
}
