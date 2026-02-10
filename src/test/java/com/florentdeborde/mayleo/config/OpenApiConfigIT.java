package com.florentdeborde.mayleo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@DisplayName("Integration Test - OpenAPI Configuration")
class OpenApiConfigIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("✅ OpenAPI: Should expose security schemes in API documentation")
    @SuppressWarnings("unchecked")
    void openApi_shouldExposeSecuritySchemes() {
        // WHEN: Calling the default SpringDoc OpenAPI JSON endpoint
        ResponseEntity<Map> response = restTemplate.getForEntity("/v3/api-docs", Map.class);

        // THEN: The response should be successful
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        Map<String, Object> body = response.getBody();

        // Check Metadata
        Map<String, Object> info = (Map<String, Object>) body.get("info");
        assertThat(info.get("title")).isEqualTo("Mayleo Email Gateway API");

        // Check Security Components
        Map<String, Object> components = (Map<String, Object>) body.get("components");
        assertThat(components).containsKey("securitySchemes");

        Map<String, Object> schemes = (Map<String, Object>) components.get("securitySchemes");

        // Validate X-API-KEY config
        assertThat(schemes).containsKey("ApiKeyAuth");
        Map<String, Object> apiKeyAuth = (Map<String, Object>) schemes.get("ApiKeyAuth");
        assertThat(apiKeyAuth.get("name")).isEqualTo("X-API-KEY");
        assertThat(apiKeyAuth.get("in")).isEqualTo("header");

        // Validate X-SIGNATURE config
        assertThat(schemes).containsKey("SignatureAuth");
        Map<String, Object> sigAuth = (Map<String, Object>) schemes.get("SignatureAuth");
        assertThat(sigAuth.get("name")).isEqualTo("X-SIGNATURE");
        assertThat(sigAuth.get("in")).isEqualTo("header");

        // Validate X-IDEMPOTENCY-KEY config
        assertThat(schemes).containsKey("IdempotencyKey");
        Map<String, Object> idemAuth = (Map<String, Object>) schemes.get("IdempotencyKey");
        assertThat(idemAuth.get("name")).isEqualTo("X-Idempotency-Key");
        assertThat(idemAuth.get("in")).isEqualTo("header");
        assertThat(idemAuth.get("description")).asString().contains("prevent duplicate");
    }
}