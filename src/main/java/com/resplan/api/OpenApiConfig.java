package com.resplan.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Описание API в формате OpenAPI 3; интерактивная документация – /swagger-ui.html */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "ResPlan REST API", version = "0.9.0",
                description = "Календарно-сетевое и ресурсное планирование проектов: варианты использования UC-1 – UC-9"),
        security = @SecurityRequirement(name = "bearer"))
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
