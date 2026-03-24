package com.example.bankcards.scheduler;

import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.CardStatus;
import com.example.bankcards.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Планировщик автоматической просрочки карт.
 * Ежедневно в полночь проверяет активные карты с истёкшим сроком действия
 * и переводит их в статус EXPIRED.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CardExpirationScheduler {

    private final CardRepository cardRepository;

    @Scheduled(cron = "0 0 0 * * *") // каждый день в 00:00
    @Transactional
    public void expireCards() {
        List<Card> expiredCards = cardRepository
                .findByStatusAndExpirationDateBefore(CardStatus.ACTIVE, LocalDate.now());

        if (!expiredCards.isEmpty()) {
            expiredCards.forEach(card -> card.setStatus(CardStatus.EXPIRED));
            cardRepository.saveAll(expiredCards);
            log.info("[SCHEDULER] Expired {} cards", expiredCards.size());
        }
    }
}
