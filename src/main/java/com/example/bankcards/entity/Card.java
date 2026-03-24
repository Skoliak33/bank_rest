package com.example.bankcards.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Сущность банковской карты.
 * Номер карты хранится в двух видах:
 *   encryptedNumber - AES-256-GCM шифрование (для безопасного хранения)
 *   maskedNumber    - маска **** **** **** 1234 (для отображения в API без расшифровки)
 */
@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Полный номер карты, зашифрованный AES-256-GCM (base64) */
    @Column(name = "encrypted_number", nullable = false)
    private String encryptedNumber;

    /** Маска номера для отображения: **** **** **** 1234 */
    @Column(name = "masked_number", nullable = false)
    private String maskedNumber;

    /** Владелец карты. LAZY - загружается только при обращении */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CardStatus status = CardStatus.ACTIVE;

    /** BigDecimal для точных денежных расчётов */
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /** Optimistic locking: предотвращает одновременные изменения баланса (гонки при переводах) */
    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
