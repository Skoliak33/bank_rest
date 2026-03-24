package com.example.bankcards.dto.user;

import com.example.bankcards.entity.Role;
import com.example.bankcards.entity.User;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        Role role
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );
    }
}
