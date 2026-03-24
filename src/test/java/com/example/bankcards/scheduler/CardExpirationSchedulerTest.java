package com.example.bankcards.scheduler;

import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.CardStatus;
import com.example.bankcards.entity.User;
import com.example.bankcards.repository.CardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardExpirationSchedulerTest {

    @Mock
    private CardRepository cardRepository;

    @InjectMocks
    private CardExpirationScheduler scheduler;

    @Test
    void expireCards_shouldSetExpiredStatus() {
        User owner = User.builder().id(1L).fullName("Test").build();

        Card expiredCard1 = Card.builder()
                .id(1L).owner(owner).status(CardStatus.ACTIVE)
                .expirationDate(LocalDate.now().minusDays(10))
                .build();
        Card expiredCard2 = Card.builder()
                .id(2L).owner(owner).status(CardStatus.ACTIVE)
                .expirationDate(LocalDate.now().minusMonths(1))
                .build();

        when(cardRepository.findByStatusAndExpirationDateBefore(eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(expiredCard1, expiredCard2));

        scheduler.expireCards();

        // Оба должны стать EXPIRED
        assertEquals(CardStatus.EXPIRED, expiredCard1.getStatus());
        assertEquals(CardStatus.EXPIRED, expiredCard2.getStatus());

        // saveAll вызван с обеими картами
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Card>> captor = ArgumentCaptor.forClass(List.class);
        verify(cardRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void expireCards_noExpiredCards_shouldDoNothing() {
        when(cardRepository.findByStatusAndExpirationDateBefore(eq(CardStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of());

        scheduler.expireCards();

        // saveAll не должен вызываться
        verify(cardRepository, never()).saveAll(any());
    }
}
