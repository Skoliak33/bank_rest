package com.example.bankcards.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация Swagger UI / OpenAPI.
 * SecurityScheme "bearerAuth" - позволяет вводить JWT-токен в Swagger UI
 * через кнопку Authorize для тестирования защищённых эндпоинтов.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Bank Cards Management API",
                version = "1.0",
                description = "REST API для управления банковскими картами"
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
