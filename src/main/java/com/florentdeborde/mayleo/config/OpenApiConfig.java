package com.florentdeborde.mayleo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Mayleo Email Gateway API").version("1.0"))
                .addSecurityItem(new SecurityRequirement()
                        .addList("ApiKeyAuth")
                        .addList("SignatureAuth")
                        .addList("IdempotencyKey"))
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .name("X-API-KEY")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Client identification key"))
                        .addSecuritySchemes("SignatureAuth", new SecurityScheme()
                                .name("X-SIGNATURE")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("HMAC-SHA256 payload signature"))
                        .addSecuritySchemes("IdempotencyKey", new SecurityScheme()
                                .name("X-Idempotency-Key")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Optional UUID to prevent duplicate processing")));
    }
}
