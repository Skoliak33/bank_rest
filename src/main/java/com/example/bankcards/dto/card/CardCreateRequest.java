package com.example.bankcards.dto.card;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CardCreateRequest(
        @NotNull(message = "Owner ID is required")
        Long ownerId,

        @FutureOrPresent(message = "Expiration date must be in the future")
        LocalDate expirationDate
) {}
