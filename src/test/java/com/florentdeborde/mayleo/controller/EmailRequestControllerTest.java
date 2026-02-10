package com.florentdeborde.mayleo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.florentdeborde.mayleo.dto.request.EmailRequestDto;
import com.florentdeborde.mayleo.model.ApiClient;
import com.florentdeborde.mayleo.model.ImageSource;
import com.florentdeborde.mayleo.repository.ApiClientRepository;
import com.florentdeborde.mayleo.service.EmailRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmailRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Unit Test - EmailRequestController")
class EmailRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailRequestService emailRequestService;

    @MockitoBean
    private ApiClientRepository apiClientRepository;

    private final String allowedDomain = "https://allowed-domain.com";

    private EmailRequestDto createValidDto() {
        return EmailRequestDto.builder()
                .langCode("en")
                .subject("Test Subject")
                .message("Test Message Content")
                .toEmail("recipient@example.com")
                .imageSource(ImageSource.DEFAULT)
                .imagePath("path/to/image.jpg")
                .build();
    }

    private ApiClient createMockClient() {
        return ApiClient.builder()
                .id("test-id")
                .name("test-client")
                .build();
    }

    @Test
    @DisplayName("✅ Should return 202 ACCEPTED and UUID when request is valid")
    void createEmailRequest_Success() throws Exception {
        // GIVEN
        String generatedId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();
        EmailRequestDto validDto = createValidDto();
        ApiClient mockClient = createMockClient();

        when(emailRequestService.createEmailRequest(any(ApiClient.class), any(EmailRequestDto.class), any(String.class)))
                .thenReturn(generatedId);

        // WHEN & THEN
        mockMvc.perform(post("/email-request")
                        .requestAttr("authenticatedClient", mockClient)
                        .header("Origin", allowedDomain)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(generatedId));
    }

    @Test
    @DisplayName("❌ Should return 400 BAD REQUEST when langCode format is invalid")
    void createEmailRequest_InvalidLangCode() throws Exception {
        // GIVEN
        EmailRequestDto invalidDto = createValidDto();
        invalidDto.setLangCode("ENGLISH"); // Fails @Pattern regex ^[a-z]{2}$

        // WHEN & THEN
        mockMvc.perform(post("/email-request")
                        .requestAttr("authenticatedClient", createMockClient())
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("❌ Should return 400 BAD REQUEST when toEmail is malformed")
    void createEmailRequest_InvalidEmail() throws Exception {
        // GIVEN
        EmailRequestDto invalidDto = createValidDto();
        invalidDto.setToEmail("not-an-email-address"); // Fails @Email validation

        // WHEN & THEN
        mockMvc.perform(post("/email-request")
                        .requestAttr("authenticatedClient", createMockClient())
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("❌ Should return 400 BAD REQUEST when imageSource is missing")
    void createEmailRequest_NullImageSource() throws Exception {
        // GIVEN
        EmailRequestDto invalidDto = createValidDto();
        invalidDto.setImageSource(null); // Fails @NotNull validation

        // WHEN & THEN
        mockMvc.perform(post("/email-request")
                        .requestAttr("authenticatedClient", createMockClient())
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("❌ Should return 400 BAD REQUEST when API Key header is missing")
    void createEmailRequest_MissingHeader() throws Exception {
        // GIVEN
        EmailRequestDto dto = createValidDto();

        // WHEN & THEN - No X-API-KEY header provided
        mockMvc.perform(post("/email-request")
                        .header("Origin", allowedDomain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }
}