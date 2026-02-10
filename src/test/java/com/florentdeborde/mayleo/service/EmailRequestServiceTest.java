package com.florentdeborde.mayleo.service;

import com.florentdeborde.mayleo.dto.internal.Postcard;
import com.florentdeborde.mayleo.dto.internal.PostcardHtml;
import com.florentdeborde.mayleo.dto.request.EmailRequestDto;
import com.florentdeborde.mayleo.dto.internal.UsageStats;
import com.florentdeborde.mayleo.exception.ExceptionCode;
import com.florentdeborde.mayleo.exception.MayleoException;
import com.florentdeborde.mayleo.metrics.MayleoMetrics;
import com.florentdeborde.mayleo.model.*;
import com.florentdeborde.mayleo.repository.EmailConfigRepository;
import com.florentdeborde.mayleo.repository.EmailRequestRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test - EmailRequestService")
class EmailRequestServiceTest {

    @Mock
    private EmailRequestRepository repository;
    @Mock
    private EmailConfigRepository emailConfigRepository;
    @Mock
    private MayleoMetrics metrics;


    @InjectMocks
    private EmailRequestService emailRequestService;

    private ApiClient apiClient;
    private EmailConfig emailConfig;

    @BeforeEach
    void setUp() {
        apiClient = ApiClient.builder()
                .id("client-123")
                .name("Test Client")
                .enabled(true)
                .dailyQuota(24)
                .rpmLimit(1)
                .build();

        emailConfig = EmailConfig.builder()
                .apiClient(apiClient)
                .defaultSubject("Default Subject")
                .defaultMessage("Default Message")
                .defaultLanguage("en")
                .provider(EmailProvider.SMTP)
                .build();
    }

    @Test
    @DisplayName("✅ createEmailRequest: Should use fallback values from config when DTO is empty")
    void createEmailRequest_SuccessWithFallbacks() {
        // GIVEN: A DTO with missing subject and message
        EmailRequestDto dto = EmailRequestDto.builder()
                .toEmail("recipient@example.com")
                .imageSource(ImageSource.DEFAULT)
                .imagePath("postcards/postcard-1.jpg")
                .langCode("en")
                .build();
        UsageStats mockStats = new UsageStats(0L, 0L);

        when(repository.getUsageStats(eq(apiClient), any(Instant.class), any(Instant.class)))
                .thenReturn(mockStats);
        when(emailConfigRepository.findByApiClient(apiClient)).thenReturn(Optional.of(emailConfig));
        when(repository.save(any(EmailRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN: Creating the request
        String id = emailRequestService.createEmailRequest(apiClient, dto, null);

        // THEN: Verify fallbacks were applied from mockConfig
        assertNotNull(id);
        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(repository).save(captor.capture());

        EmailRequest saved = captor.getValue();
        assertEquals(emailConfig.getDefaultSubject(), saved.getSubject());
        assertEquals(emailConfig.getDefaultMessage(), saved.getMessage());
        assertEquals(EmailProvider.SMTP, emailConfig.getProvider());
        assertEquals(EmailRequestStatus.PENDING, saved.getStatus());
        assertEquals(apiClient, saved.getApiClient());
        assertEquals(apiClient.getName(), saved.getApiClient().getName());
        assertEquals(apiClient.getApiKey(), saved.getApiClient().getApiKey());
        assertEquals(apiClient.getId(), saved.getApiClient().getId());
        assertEquals("en", saved.getLangCode());
        assertEquals(ImageSource.DEFAULT, saved.getImageSource());

        verify(metrics).recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_RECEIVED);
        verify(metrics).recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_ACCEPTED);
    }

    @Test
    @DisplayName("❌ createEmailRequest: Should throw EMAIL_CONFIG_NOT_FOUND when config is missing")
    void createEmailRequest_NoConfig() {
        UsageStats mockStats = new UsageStats(0L, 0L);

        when(emailConfigRepository.findByApiClient(apiClient)).thenReturn(Optional.empty());
        when(repository.getUsageStats(eq(apiClient), any(Instant.class), any(Instant.class)))
                .thenReturn(mockStats);

        MayleoException ex = assertThrows(MayleoException.class, () ->
                emailRequestService.createEmailRequest(apiClient, new EmailRequestDto(), null)
        );
        assertEquals(ExceptionCode.EMAIL_CONFIG_NOT_FOUND, ex.getExceptionCode());

        verify(metrics).recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_RECEIVED);
        verify(metrics).recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_ERR_CONFIG_NOT_FOUND);
        verify(metrics, never()).recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_ACCEPTED);
    }

    @Test
    @DisplayName("❌ createEmailRequest: Should throw RPM_LIMIT_EXCEEDED when rate limit is reached")
    void createEmailRequest_RpmLimitExceeded() {
        // GIVEN: Client has a limit of 10 RPM, and has already sent 10 emails in the last minute
        apiClient.setRpmLimit(10);
        UsageStats usageStats = new UsageStats(50L, 10L); // 50 today, 10 this minute

        // Mocking the new optimized single-call method
        when(repository.getUsageStats(eq(apiClient), any(Instant.class), any(Instant.class)))
                .thenReturn(usageStats);

        // WHEN & THEN: Attempting to create a request should trigger the RPM exception
        MayleoException ex = assertThrows(MayleoException.class, () ->
                emailRequestService.createEmailRequest(apiClient, new EmailRequestDto(), null)
        );
        assertEquals(ExceptionCode.RPM_LIMIT_EXCEEDED, ex.getExceptionCode());

        verify(repository, never()).save(any());
        verify(metrics).recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_RECEIVED);
        verify(metrics).recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_ERR_RPM);
        verify(metrics, never()).recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_ACCEPTED);
    }

    @Test
    @DisplayName("❌ createEmailRequest: Should throw DAILY_QUOTA_EXCEEDED when daily quota is reached")
    void createEmailRequest_DailyQuotaExceeded() {
        // GIVEN: Client has a daily quota of 1000, and has already reached it
        apiClient.setDailyQuota(1000);
        UsageStats usageStats = new UsageStats(1000L, 0L); // 1000 today, only 0 this minute

        // Mocking the new optimized single-call method
        when(repository.getUsageStats(eq(apiClient), any(Instant.class), any(Instant.class)))
                .thenReturn(usageStats);

        // WHEN & THEN: Attempting to create a request should trigger the Daily Quota exception
        MayleoException ex = assertThrows(MayleoException.class, () ->
                emailRequestService.createEmailRequest(apiClient, new EmailRequestDto(), null)
        );
        assertEquals(ExceptionCode.DAILY_QUOTA_EXCEEDED, ex.getExceptionCode());

        verify(repository, never()).save(any());
        verify(metrics).recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_RECEIVED);
        verify(metrics).recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_ERR_DAILY_QUOTA);
        verify(metrics, never()).recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_ACCEPTED);
    }

    @Test
    @DisplayName("✅ createEmailRequest: Should return existing ID when idempotency key is found")
    void createEmailRequest_Idempotency_Found() {
        // GIVEN
        String key = "unique-key-123";
        String existingId = "req-already-exists";
        EmailRequest existingRequest = EmailRequest.builder().id(existingId).build();
        EmailRequestDto dto = new EmailRequestDto();

        when(repository.findByApiClientAndIdempotencyKey(apiClient, key))
                .thenReturn(Optional.of(existingRequest));

        // WHEN
        String resultId = emailRequestService.createEmailRequest(apiClient, dto, key);

        // THEN
        assertEquals(existingId, resultId);
        verify(repository, never()).getUsageStats(any(), any(), any());
        verify(repository, never()).save(any());
        verify(metrics, never()).recordApiRequest(any(), any());
    }

    @Test
    @DisplayName("️❌ createEmailRequest: Should recover and return existing ID on concurrent race condition")
    void createEmailRequest_Idempotency_RaceCondition() {
        // GIVEN: Prepare data for a simulated collision
        String key = "race-key";
        String existingId = "id-saved-by-concurrent-thread";
        EmailRequest existingRequest = EmailRequest.builder().id(existingId).build();
        UsageStats mockStats = new UsageStats(0L, 0L);

        // MOCK: First call (initial check) returns empty,
        // second call (inside catch block) returns the request saved by the other thread
        when(repository.findByApiClientAndIdempotencyKey(eq(apiClient), eq(key)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingRequest));

        when(repository.getUsageStats(any(), any(), any())).thenReturn(mockStats);
        when(emailConfigRepository.findByApiClient(apiClient)).thenReturn(Optional.of(emailConfig));

        // MOCK: Simulate the DB rejecting the insert due to the UNIQUE constraint
        when(repository.save(any(EmailRequest.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate entry for idempotency key"));

        // WHEN: Attempting to create the email request
        String resultId = emailRequestService.createEmailRequest(apiClient, new EmailRequestDto(), key);

        // THEN: The service should gracefully recover and return the ID from the database
        assertThat(resultId).isEqualTo(existingId);

        // VERIFY: Ensure findBy was called twice (Initial + Recovery) and save was attempted once
        verify(repository, times(2)).findByApiClientAndIdempotencyKey(eq(apiClient), eq(key));
        verify(repository).save(any());
    }

    @Test
    @DisplayName("✅ createEmailRequest: Should sanitize HTML inputs to prevent XSS")
    void createEmailRequest_ShouldSanitizeHtmlInputs() {
        // GIVEN
        String unsafeSubject = "<script>alert('XSS')</script>";
        String unsafeMessage = "Click <a href='http://evil.com'>here</a>";

        EmailRequestDto dto = EmailRequestDto.builder()
                .toEmail("test@example.com")
                .subject(unsafeSubject)
                .message(unsafeMessage)
                .langCode("en")
                .imageSource(ImageSource.DEFAULT)
                .imagePath("test.jpg")
                .build();

        UsageStats mockStats = new UsageStats(0L, 0L);

        when(repository.getUsageStats(any(), any(), any())).thenReturn(mockStats);
        when(emailConfigRepository.findByApiClient(apiClient)).thenReturn(Optional.of(emailConfig));
        when(repository.save(any(EmailRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN
        emailRequestService.createEmailRequest(apiClient, dto, null);

        // THEN
        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(repository).save(captor.capture());

        EmailRequest saved = captor.getValue();
        assertEquals("&lt;script&gt;alert(&#39;XSS&#39;)&lt;/script&gt;", saved.getSubject());
        assertEquals("Click &lt;a href=&#39;http://evil.com&#39;&gt;here&lt;/a&gt;", saved.getMessage());
    }


}