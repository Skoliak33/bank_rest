package com.example.bankcards.aspect;

import com.example.bankcards.dto.card.CardResponse;
import com.example.bankcards.entity.CardStatus;
import com.example.bankcards.entity.Role;
import com.example.bankcards.entity.User;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.security.CustomUserDetails;
import com.example.bankcards.service.CardService;
import com.example.bankcards.util.CardNumberUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тест AOP аудит-аспекта. Поднимает полный контекст (H2),
 * чтобы AOP-прокси были активны, и проверяет что:
 * 1) Аудит-лог пишется для успешных и неуспешных операций
 * 2) null-аргументы (status=null) не вызывают NPE
 * 3) Pageable-аргументы корректно логируются
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuditAspectTest {

    @Autowired
    private CardService cardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardNumberUtil cardNumberUtil;

    private User testUser;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("audit-test@test.com")
                .password("$2a$10$dummy")
                .fullName("Audit Test User")
                .role(Role.ROLE_USER)
                .build());

        userDetails = new CustomUserDetails(testUser);

        // Устанавливаем SecurityContext для аудита
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cardRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getCards_withNullStatus_shouldNotThrowNPE(CapturedOutput output) {
        // Именно этот сценарий вызывал NPE: status=null передаётся в getCards
        assertDoesNotThrow(() ->
                cardService.getCards(null, userDetails, PageRequest.of(0, 10)));

        assertTrue(output.getOut().contains("[AUDIT]"));
        assertTrue(output.getOut().contains("action=getCards"));
        assertTrue(output.getOut().contains("status=SUCCESS"));
    }

    @Test
    void getCards_withStatusFilter_shouldLogArgs(CapturedOutput output) {
        cardService.getCards("ACTIVE", userDetails, PageRequest.of(0, 5));

        assertTrue(output.getOut().contains("[AUDIT]"));
        assertTrue(output.getOut().contains("action=getCards"));
        assertTrue(output.getOut().contains("ACTIVE"));
    }

    @Test
    void blockCard_nonExistent_shouldLogFailure(CapturedOutput output) {
        try {
            cardService.blockCard(999L, userDetails);
        } catch (Exception ignored) {
            // Ожидаем ResourceNotFoundException
        }

        assertTrue(output.getOut().contains("[AUDIT]"));
        assertTrue(output.getOut().contains("action=blockCard"));
        assertTrue(output.getOut().contains("status=FAILED"));
    }

    @Test
    void createCard_shouldLogSuccessWithUser(CapturedOutput output) {
        // Переключаемся на ADMIN для создания карт
        User admin = userRepository.save(User.builder()
                .email("audit-admin@test.com")
                .password("$2a$10$dummy")
                .fullName("Audit Admin")
                .role(Role.ROLE_ADMIN)
                .build());
        CustomUserDetails adminDetails = new CustomUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities()));

        CardResponse card = cardService.createCard(
                new com.example.bankcards.dto.card.CardCreateRequest(testUser.getId(), LocalDate.now().plusYears(3)));

        assertTrue(output.getOut().contains("[AUDIT]"));
        assertTrue(output.getOut().contains("user=audit-admin@test.com"));
        assertTrue(output.getOut().contains("action=createCard"));
        assertTrue(output.getOut().contains("status=SUCCESS"));
    }

    @Test
    void transfer_insufficientFunds_shouldLogFailure(CapturedOutput output) {
        // Создаём 2 карты с нулевым балансом
        var card1 = cardRepository.save(com.example.bankcards.entity.Card.builder()
                .encryptedNumber(cardNumberUtil.encrypt("1111222233334444"))
                .maskedNumber("**** **** **** 4444")
                .owner(testUser)
                .expirationDate(LocalDate.now().plusYears(1))
                .status(CardStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .build());
        var card2 = cardRepository.save(com.example.bankcards.entity.Card.builder()
                .encryptedNumber(cardNumberUtil.encrypt("5555666677778888"))
                .maskedNumber("**** **** **** 8888")
                .owner(testUser)
                .expirationDate(LocalDate.now().plusYears(1))
                .status(CardStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .build());

        try {
            cardService.transfer(
                    new com.example.bankcards.dto.card.TransferRequest(card1.getId(), card2.getId(), new BigDecimal("100")),
                    userDetails);
        } catch (Exception ignored) {
        }

        assertTrue(output.getOut().contains("[AUDIT]"));
        assertTrue(output.getOut().contains("action=transfer"));
        assertTrue(output.getOut().contains("status=FAILED"));
        assertTrue(output.getOut().contains("Insufficient funds"));
    }
}
