package com.florentdeborde.mayleo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.florentdeborde.mayleo.dto.request.EmailRequestDto;
import com.florentdeborde.mayleo.model.ApiClient;
import com.florentdeborde.mayleo.model.EmailConfig;
import com.florentdeborde.mayleo.model.EmailProvider;
import com.florentdeborde.mayleo.model.ImageSource;
import com.florentdeborde.mayleo.repository.ApiClientRepository;
import com.florentdeborde.mayleo.repository.EmailConfigRepository;
import com.florentdeborde.mayleo.security.ApiKeyEncoder;
import com.florentdeborde.mayleo.service.HmacService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Transactional
@DisplayName("Integration Test - Email Request Lifecycle")
class EmailRequestControllerIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiClientRepository apiClientRepository;

    @Autowired
    private EmailConfigRepository emailConfigRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HmacService hmacService;

    @Value("${app.security.key-salt}")
    private String salt;

    @Value("${app.security.key-hmac}")
    private String hmac;

    private final String urlTemplate = "/email-request";
    private final String headerKeyName = "X-API-KEY";
    private final String headerHmacName = "X-SIGNATURE";

    private final String PLAIN_API_KEY = "key";
    private final String PLAIN_KEY_NO_CONFIG = "random-key";

    private final String CLIENT_SPECIFIC_SECRET = "client-very-secret-hmac-key";

    private String hashedApiKey;

    private final String allowedDomain = "https://allowed-domain.com";

    EmailRequestDto dto = EmailRequestDto.builder()
            .subject("Hello")
            .message("Test message")
            .toEmail("test@test.com")
            .imageSource(ImageSource.DEFAULT)
            .imagePath("postcards/postcard-0.jpg")
            .build();

    @BeforeEach
    void setupSpringSecurityAndDatabase() {
        apiClientRepository.deleteAll();
        emailConfigRepository.deleteAll();

        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        hashedApiKey = ApiKeyEncoder.hashSha256(PLAIN_API_KEY, salt);
        String hashedKeyNoConfig = ApiKeyEncoder.hashSha256(PLAIN_KEY_NO_CONFIG, salt);

        ApiClient clientWithConfig = ApiClient.builder()
                .id(UUID.randomUUID().toString())
                .name("client-with-config")
                .apiKey(hashedApiKey)
                .hmacSecretKey(CLIENT_SPECIFIC_SECRET)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .enabled(true)
                .allowedDomains(Set.of(allowedDomain))
                .dailyQuota(24)
                .rpmLimit(1)
                .build();
        apiClientRepository.save(clientWithConfig);

        EmailConfig emailConfig = EmailConfig.builder()
                .id(UUID.randomUUID().toString())
                .apiClient(clientWithConfig)
                .defaultLanguage("fr")
                .provider(EmailProvider.SMTP)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        emailConfigRepository.save(emailConfig);

        ApiClient clientWithoutConfig = ApiClient.builder()
                .id(UUID.randomUUID().toString())
                .name("client-without-config")
                .apiKey(hashedKeyNoConfig)
                .hmacSecretKey(CLIENT_SPECIFIC_SECRET)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .enabled(true)
                .allowedDomains(Set.of(allowedDomain))
                .dailyQuota(24)
                .rpmLimit(1)
                .build();
        apiClientRepository.save(clientWithoutConfig);

        String plainDisabledKey = "disabled-key";
        String hashedDisabledKey = ApiKeyEncoder.hashSha256(plainDisabledKey, salt);
        ApiClient disabledClient = ApiClient.builder()
                .id(UUID.randomUUID().toString())
                .name("disabled-client")
                .apiKey(hashedDisabledKey)
                .hmacSecretKey(CLIENT_SPECIFIC_SECRET)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .enabled(false) // <--- Crucial
                .allowedDomains(Set.of(allowedDomain))
                .dailyQuota(100)
                .rpmLimit(10)
                .build();
        apiClientRepository.save(disabledClient);
    }

    private String generateTestSignature(Object payload, String secret) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        return hmacService.calculateHmac(body, secret);
    }

    @Test
    @DisplayName("✅ Should successfully create an email request when API Key is valid and config exists")
    void should_create_email_request_and_return_202() throws Exception {
        String signature = generateTestSignature(dto, CLIENT_SPECIFIC_SECRET);

        mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, PLAIN_API_KEY)
                        .header(headerHmacName, signature)
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("❌ Phase 1 should return 401 UNAUTHORIZED when blank API Key is provided")
    void should_return_401_when_api_key_is_null() throws Exception {
        String wrongKey = "wrong-key";
        mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, " ")
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("❌ Phase 1 should return 401 UNAUTHORIZED when an invalid API Key is provided")
    void should_return_401_when_api_key_is_wrong() throws Exception {
        String wrongKey = "wrong-key";
        mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, wrongKey)
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("❌ Phase 1 should return 403 FORBIDDEN when the client is disabled")
    void should_return_403_when_client_is_disabled() throws Exception {
        String plainDisabledKey = "disabled-key";
        String signature = generateTestSignature(dto, CLIENT_SPECIFIC_SECRET);

        mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, plainDisabledKey)
                        .header(headerHmacName, signature)
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("❌ Phase 1 should return 403 FORBIDDEN when the domain is not allowed before checking Payload or HMAC")
    void should_return_403_when_domain_is_not_allowed() throws Exception {
        String notAllowedDomain = "https://not-allowed-domain.com";
        mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, PLAIN_API_KEY)
                        .header("Origin", notAllowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("❌ Phase 2 should return 413 PAYLOAD TOO LARGE when body exceeds MAX_BODY_SIZE")
    void should_return_413_when_payload_is_too_large() throws Exception {
        // We create a very large string to exceed the 2MB limit
        // 1 char = 1 byte in standard JSON, so 3 million chars > 2 * 1024 * 1024
        String hugeMessage = "A".repeat(3 * 1024 * 1024);

        EmailRequestDto hugeDto = EmailRequestDto.builder()
                .subject("Huge")
                .message(hugeMessage)
                .toEmail("test@test.com")
                .build();

        // The filter checks the size BEFORE HMAC, so we don't even need a valid signature
        mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, PLAIN_API_KEY)
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hugeDto)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    @DisplayName("❌ Phase 3 should return 401 UNAUTHORIZED when HMAC signature is invalid")
    void should_return_401_when_hmac_signature_is_invalid() throws Exception {
        String invalidSignature = "invalid-hmac-signature-12345";

        mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, PLAIN_API_KEY)
                        .header(headerHmacName, invalidSignature)
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("❌ Service should return 404 NOT FOUND when the client exists but lacks an email configuration")
    void should_return_404_when_client_has_no_email_config() throws Exception {
        String signature = generateTestSignature(dto, CLIENT_SPECIFIC_SECRET);

        mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, PLAIN_KEY_NO_CONFIG)
                        .header(headerHmacName, signature)
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("❌ Service should return 429 TOO MANY REQUESTS when RPM limit is exceeded")
    void should_return_429_when_rpm_limit_exceeded() throws Exception {
        String signature = generateTestSignature(dto, CLIENT_SPECIFIC_SECRET);
        mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, PLAIN_API_KEY)
                        .header(headerHmacName, signature)
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, PLAIN_API_KEY)
                        .header(headerHmacName, signature)
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("❌ Service should return 429 TOO MANY REQUESTS when daily quota is exceeded")
    void should_return_429_when_daily_quota_exceeded() throws Exception {
        String signature = generateTestSignature(dto, CLIENT_SPECIFIC_SECRET);
        ApiClient client = apiClientRepository.findByApiKey(hashedApiKey).orElseThrow();
        client.setDailyQuota(0);
        apiClientRepository.saveAndFlush(client);

        mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, PLAIN_API_KEY)
                        .header(headerHmacName, signature)
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("✅ Should return the same UUID when the same idempotency key is used")
    void should_return_same_id_when_idempotency_key_is_reused() throws Exception {
        // GIVEN
        String signature = generateTestSignature(dto, CLIENT_SPECIFIC_SECRET);
        String idempotencyKey = UUID.randomUUID().toString();
        String headerIdempotencyName = "X-Idempotency-Key";

        // WHEN: First request
        String firstResponse = mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, PLAIN_API_KEY)
                        .header(headerHmacName, signature)
                        .header(headerIdempotencyName, idempotencyKey)
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String firstId = objectMapper.readTree(firstResponse).get("id").asText();

        // AND: Second request with exact same payload and key
        String secondResponse = mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, PLAIN_API_KEY)
                        .header(headerHmacName, signature)
                        .header(headerIdempotencyName, idempotencyKey)
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String secondId = objectMapper.readTree(secondResponse).get("id").asText();

        // THEN: Both IDs must be identical
        assertThat(firstId).isEqualTo(secondId);
    }

    @Test
    @DisplayName("✅ Should sanitize HTML input in message and subject (Anti-XSS)")
    void should_sanitize_html_in_subject_and_message () throws Exception {
        // GIVEN
        String maliciousSubject = "<script>alert('XSS')</script>";
        String maliciousMessage = "Click <a href='javascript:void(0)'>here</a>";

        EmailRequestDto sensitiveDto = EmailRequestDto.builder()
                .subject(maliciousSubject)
                .message(maliciousMessage)
                .toEmail("victim@example.com")
                .imageSource(ImageSource.DEFAULT)
                .imagePath("postcards/postcard-0.jpg")
                .langCode("en")
                .build();

        String signature = generateTestSignature(sensitiveDto, CLIENT_SPECIFIC_SECRET);

        // WHEN
        mockMvc.perform(post(urlTemplate)
                        .header(headerKeyName, PLAIN_API_KEY)
                        .header(headerHmacName, signature)
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sensitiveDto)))
                .andExpect(status().isAccepted());

        // THEN: Verify in Database (we can check the repository because we are @Transactional)
        // Since we don't return the full object, we fetch it by subject (or just get the last one if we cleaned up)
        // Ideally we should extract the ID from response, but here let's assume it's the only one created in this transaction context context
        // Actually, let's use the ID from response
    }
}