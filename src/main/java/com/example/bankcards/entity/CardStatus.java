package com.example.bankcards.entity;

/** Статусы банковской карты. */
public enum CardStatus {
    ACTIVE,   // Карта активна, доступны все операции
    BLOCKED,  // Карта заблокирована (пользователем или админом)
    EXPIRED   // Срок действия карты истёк
}
