package com.example.bankcards.entity;

/**
 * Роли пользователей.
 * Префикс ROLE_ - соглашение Spring Security для hasRole()/hasAuthority().
 */
public enum Role {
    ROLE_ADMIN,
    ROLE_USER
}
