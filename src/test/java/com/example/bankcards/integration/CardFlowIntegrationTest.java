package com.example.bankcards.integration;

import com.example.bankcards.dto.auth.LoginRequest;
import com.example.bankcards.dto.auth.LoginResponse;
import com.example.bankcards.dto.auth.RegisterRequest;
import com.example.bankcards.dto.card.CardCreateRequest;
import com.example.bankcards.dto.card.TransferRequest;
import com.example.bankcards.dto.user.UserResponse;
import com.example.bankcards.entity.CardStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Интеграционный тест: полный flow на реальном PostgreSQL через Testcontainers.
 * Проверяет: регистрацию, логин, создание карт, перевод, блокировку, права доступа,
 * а также что Liquibase миграции корректно работают на PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class CardFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bankcards_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;
    private String adminToken;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        // Логинимся админом (создан миграцией 003)
        adminToken = login("admin@bank.com", "admin123");
    }

    @Test
    void fullCardLifecycle() {
        // 1. Регистрация пользователя
        RegisterRequest registerReq = new RegisterRequest("flow@test.com", "password123", "Flow User");
        ResponseEntity<UserResponse> registerResp = post("/api/auth/register", registerReq, null, UserResponse.class);
        assertEquals(HttpStatus.CREATED, registerResp.getStatusCode());
        Long userId = registerResp.getBody().id();
        assertNotNull(userId);

        // 2. Логин пользователем
        String userToken = login("flow@test.com", "password123");
        assertNotNull(userToken);

        // 3. ADMIN создаёт 2 карты для пользователя
        CardCreateRequest cardReq = new CardCreateRequest(userId, null);
        ResponseEntity<Map> card1Resp = post("/api/cards", cardReq, adminToken, Map.class);
        ResponseEntity<Map> card2Resp = post("/api/cards", cardReq, adminToken, Map.class);
        assertEquals(HttpStatus.CREATED, card1Resp.getStatusCode());
        assertEquals(HttpStatus.CREATED, card2Resp.getStatusCode());

        Long card1Id = ((Number) card1Resp.getBody().get("id")).longValue();
        Long card2Id = ((Number) card2Resp.getBody().get("id")).longValue();

        // Проверяем маскирование номера
        String masked = (String) card1Resp.getBody().get("maskedNumber");
        assertTrue(masked.startsWith("**** **** **** "));

        // 4. USER видит свои карты
        ResponseEntity<Map> userCards = get("/api/cards?page=0&size=10", userToken, Map.class);
        assertEquals(HttpStatus.OK, userCards.getStatusCode());
        assertEquals(2, ((java.util.List<?>) userCards.getBody().get("content")).size());

        // 5. USER НЕ может создать карту (403)
        ResponseEntity<Map> forbiddenCreate = post("/api/cards", cardReq, userToken, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenCreate.getStatusCode());

        // 6. USER НЕ может видеть пользователей (403)
        ResponseEntity<Map> forbiddenUsers = get("/api/users", userToken, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenUsers.getStatusCode());

        // 7. Без токена - 401
        ResponseEntity<Map> unauthorized = get("/api/cards", null, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getStatusCode());
    }

    @Test
    void transferBetweenOwnCards() {
        // Подготовка: создаём юзера и 2 карты
        RegisterRequest registerReq = new RegisterRequest("transfer@test.com", "password123", "Transfer User");
        ResponseEntity<UserResponse> registerResp = post("/api/auth/register", registerReq, null, UserResponse.class);
        Long userId = registerResp.getBody().id();
        String userToken = login("transfer@test.com", "password123");

        CardCreateRequest cardReq = new CardCreateRequest(userId, null);
        Long card1Id = ((Number) post("/api/cards", cardReq, adminToken, Map.class).getBody().get("id")).longValue();
        Long card2Id = ((Number) post("/api/cards", cardReq, adminToken, Map.class).getBody().get("id")).longValue();

        // Устанавливаем баланс напрямую через SQL (в реальном приложении - через пополнение)
        // Для интеграционного теста используем сервис напрямую - но тут мы тестируем через HTTP,
        // поэтому перевод с нулевым балансом должен дать ошибку
        TransferRequest insufficientReq = new TransferRequest(card1Id, card2Id, new BigDecimal("100"));
        ResponseEntity<Map> insufficientResp = post("/api/cards/transfer", insufficientReq, userToken, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, insufficientResp.getStatusCode());
        assertEquals("Insufficient funds on source card", insufficientResp.getBody().get("message"));

        // Перевод на ту же карту - ошибка
        TransferRequest sameCardReq = new TransferRequest(card1Id, card1Id, new BigDecimal("100"));
        ResponseEntity<Map> sameCardResp = post("/api/cards/transfer", sameCardReq, userToken, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, sameCardResp.getStatusCode());
        assertEquals("Source and target cards must be different", sameCardResp.getBody().get("message"));
    }

    @Test
    void blockAndActivateFlow() {
        // Подготовка
        RegisterRequest registerReq = new RegisterRequest("block@test.com", "password123", "Block User");
        Long userId = post("/api/auth/register", registerReq, null, UserResponse.class).getBody().id();
        String userToken = login("block@test.com", "password123");
        Long cardId = ((Number) post("/api/cards", new CardCreateRequest(userId, null), adminToken, Map.class)
                .getBody().get("id")).longValue();

        // USER блокирует свою карту
        ResponseEntity<Map> blockResp = patch("/api/cards/" + cardId + "/block", userToken, Map.class);
        assertEquals(HttpStatus.OK, blockResp.getStatusCode());
        assertEquals("BLOCKED", blockResp.getBody().get("status"));

        // USER НЕ может активировать (403)
        ResponseEntity<Map> activateForbidden = patch("/api/cards/" + cardId + "/activate", userToken, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, activateForbidden.getStatusCode());

        // ADMIN активирует
        ResponseEntity<Map> activateResp = patch("/api/cards/" + cardId + "/activate", adminToken, Map.class);
        assertEquals(HttpStatus.OK, activateResp.getStatusCode());
        assertEquals("ACTIVE", activateResp.getBody().get("status"));
    }

    // Утилиты для HTTP-запросов

    private String login(String email, String password) {
        LoginRequest req = new LoginRequest(email, password);
        ResponseEntity<LoginResponse> resp = post("/api/auth/login", req, null, LoginResponse.class);
        return resp.getBody().token();
    }

    private <T> ResponseEntity<T> post(String path, Object body, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(baseUrl + path, HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }

    private <T> ResponseEntity<T> get(String path, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(baseUrl + path, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private <T> ResponseEntity<T> patch(String path, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(baseUrl + path, HttpMethod.PATCH, new HttpEntity<>(headers), responseType);
    }
}
