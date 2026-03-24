package com.example.bankcards.dto;

import java.time.LocalDateTime;

/** Единый формат ответа об ошибке для всех эндпоинтов. */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String message,
        Object details
) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(LocalDateTime.now(), status, message, null);
    }

    public static ErrorResponse of(int status, String message, Object details) {
        return new ErrorResponse(LocalDateTime.now(), status, message, details);
    }
}
