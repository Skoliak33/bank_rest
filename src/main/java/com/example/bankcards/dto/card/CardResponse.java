package com.example.bankcards.dto.card;

import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.CardStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ответ с данными карты. Номер всегда замаскирован - полный номер никогда не возвращается через API. */
public record CardResponse(
        Long id,
        String maskedNumber,
        String ownerName,
        Long ownerId,
        LocalDate expirationDate,
        CardStatus status,
        BigDecimal balance
) {
    public static CardResponse fromEntity(Card card) {
        return new CardResponse(
                card.getId(),
                card.getMaskedNumber(),
                card.getOwner().getFullName(),
                card.getOwner().getId(),
                card.getExpirationDate(),
                card.getStatus(),
                card.getBalance()
        );
    }
}
