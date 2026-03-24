package com.example.bankcards.service;

import com.example.bankcards.dto.auth.LoginRequest;
import com.example.bankcards.dto.auth.LoginResponse;
import com.example.bankcards.dto.auth.RegisterRequest;
import com.example.bankcards.dto.user.UserResponse;
import com.example.bankcards.entity.Role;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.BadRequestException;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Сервис аутентификации: логин (выдача JWT) и регистрация новых пользователей.
 * Регистрация открытая - создаётся USER. Админ создаёт пользователей через UserService.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        String token = jwtTokenProvider.generateToken(authentication);
        return new LoginResponse(token);
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(Role.ROLE_USER)
                .build();

        User saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }
}
