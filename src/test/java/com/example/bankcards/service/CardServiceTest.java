package com.example.bankcards.service;

import com.example.bankcards.dto.card.CardCreateRequest;
import com.example.bankcards.dto.card.CardResponse;
import com.example.bankcards.dto.card.TransferRequest;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.CardStatus;
import com.example.bankcards.entity.Role;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.CardOperationException;
import com.example.bankcards.exception.InsufficientFundsException;
import com.example.bankcards.exception.ResourceNotFoundException;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.security.CustomUserDetails;
import com.example.bankcards.util.CardNumberUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CardNumberUtil cardNumberUtil;

    @InjectMocks
    private CardService cardService;

    private User user;
    private User otherUser;
    private CustomUserDetails userDetails;
    private Card sourceCard;
    private Card targetCard;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("user@test.com")
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .build();

        otherUser = User.builder()
                .id(2L)
                .email("other@test.com")
                .fullName("Other User")
                .role(Role.ROLE_USER)
                .build();

        userDetails = new CustomUserDetails(user);

        sourceCard = Card.builder()
                .id(1L)
                .maskedNumber("**** **** **** 1111")
                .owner(user)
                .status(CardStatus.ACTIVE)
                .balance(new BigDecimal("1000.00"))
                .expirationDate(LocalDate.now().plusYears(1))
                .build();

        targetCard = Card.builder()
                .id(2L)
                .maskedNumber("**** **** **** 2222")
                .owner(user)
                .status(CardStatus.ACTIVE)
                .balance(new BigDecimal("500.00"))
                .expirationDate(LocalDate.now().plusYears(1))
                .build();
    }

    @Test
    void createCard_shouldCreateSuccessfully() {
        CardCreateRequest request = new CardCreateRequest(1L, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cardNumberUtil.generateCardNumber()).thenReturn("1234567890121234");
        when(cardNumberUtil.encrypt(any())).thenReturn("encrypted");
        when(cardNumberUtil.mask(any())).thenReturn("**** **** **** 1234");
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
            Card card = inv.getArgument(0);
            card.setId(1L);
            return card;
        });

        CardResponse response = cardService.createCard(request);

        assertNotNull(response);
        assertEquals("**** **** **** 1234", response.maskedNumber());
        verify(cardRepository).save(any(Card.class));
    }

    @Test
    void createCard_userNotFound_shouldThrow() {
        CardCreateRequest request = new CardCreateRequest(99L, null);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cardService.createCard(request));
    }

    @Test
    void transfer_shouldTransferSuccessfully() {
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("200.00"));

        when(cardRepository.findById(1L)).thenReturn(Optional.of(sourceCard));
        when(cardRepository.findById(2L)).thenReturn(Optional.of(targetCard));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        CardResponse response = cardService.transfer(request, userDetails);

        assertNotNull(response);
        assertEquals(new BigDecimal("800.00"), sourceCard.getBalance());
        assertEquals(new BigDecimal("700.00"), targetCard.getBalance());
        verify(cardRepository, times(2)).save(any(Card.class));
    }

    @Test
    void transfer_insufficientFunds_shouldThrow() {
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("2000.00"));

        when(cardRepository.findById(1L)).thenReturn(Optional.of(sourceCard));
        when(cardRepository.findById(2L)).thenReturn(Optional.of(targetCard));

        assertThrows(InsufficientFundsException.class,
                () -> cardService.transfer(request, userDetails));
    }

    @Test
    void transfer_sameCard_shouldThrow() {
        TransferRequest request = new TransferRequest(1L, 1L, new BigDecimal("100.00"));

        assertThrows(CardOperationException.class,
                () -> cardService.transfer(request, userDetails));
    }

    @Test
    void transfer_blockedSourceCard_shouldThrow() {
        sourceCard.setStatus(CardStatus.BLOCKED);
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("100.00"));

        when(cardRepository.findById(1L)).thenReturn(Optional.of(sourceCard));
        when(cardRepository.findById(2L)).thenReturn(Optional.of(targetCard));

        assertThrows(CardOperationException.class,
                () -> cardService.transfer(request, userDetails));
    }

    @Test
    void transfer_wrongOwner_shouldThrow() {
        sourceCard.setOwner(otherUser);
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("100.00"));

        when(cardRepository.findById(1L)).thenReturn(Optional.of(sourceCard));
        when(cardRepository.findById(2L)).thenReturn(Optional.of(targetCard));

        assertThrows(CardOperationException.class,
                () -> cardService.transfer(request, userDetails));
    }

    @Test
    void blockCard_userOwnsCard_shouldBlock() {
        when(cardRepository.findById(1L)).thenReturn(Optional.of(sourceCard));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        CardResponse response = cardService.blockCard(1L, userDetails);

        assertEquals(CardStatus.BLOCKED, sourceCard.getStatus());
    }

    @Test
    void blockCard_userDoesNotOwnCard_shouldThrow() {
        sourceCard.setOwner(otherUser);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(sourceCard));

        assertThrows(AccessDeniedException.class,
                () -> cardService.blockCard(1L, userDetails));
    }

    @Test
    void blockCard_alreadyBlocked_shouldThrow() {
        sourceCard.setStatus(CardStatus.BLOCKED);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(sourceCard));

        assertThrows(CardOperationException.class,
                () -> cardService.blockCard(1L, userDetails));
    }

    @Test
    void getCardById_ownerAccess_shouldReturnCard() {
        when(cardRepository.findById(1L)).thenReturn(Optional.of(sourceCard));

        CardResponse response = cardService.getCardById(1L, userDetails);

        assertNotNull(response);
        assertEquals(1L, response.id());
    }

    @Test
    void getCardById_nonOwner_shouldThrow() {
        sourceCard.setOwner(otherUser);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(sourceCard));

        assertThrows(AccessDeniedException.class,
                () -> cardService.getCardById(1L, userDetails));
    }

    @Test
    void transfer_optimisticLock_shouldThrow() {
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("100.00"));

        when(cardRepository.findById(1L)).thenReturn(Optional.of(sourceCard));
        when(cardRepository.findById(2L)).thenReturn(Optional.of(targetCard));
        // Имитируем конкурентное изменение: save бросает OptimisticLockingFailureException
        when(cardRepository.save(sourceCard))
                .thenThrow(new OptimisticLockingFailureException("Row was updated by another transaction"));

        assertThrows(OptimisticLockingFailureException.class,
                () -> cardService.transfer(request, userDetails));
    }
}
