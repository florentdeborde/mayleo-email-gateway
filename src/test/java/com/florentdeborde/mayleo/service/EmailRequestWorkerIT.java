package com.florentdeborde.mayleo.service;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import java.util.Optional;
import com.florentdeborde.mayleo.dto.internal.PostcardHtml;
import com.florentdeborde.mayleo.model.ApiClient;
import com.florentdeborde.mayleo.model.EmailRequest;
import com.florentdeborde.mayleo.model.EmailRequestStatus;
import com.florentdeborde.mayleo.model.ImageSource;
import com.florentdeborde.mayleo.repository.ApiClientRepository;
import com.florentdeborde.mayleo.repository.EmailRequestRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("it")
@DisplayName("Integration Test - Email Processing Worker")
class EmailRequestWorkerIT {

    @Autowired
    private EmailRequestWorker emailRequestWorker;

    @Autowired
    private EmailRequestRepository repository;

    @MockitoBean
    private EmailSenderService emailSenderService;

    @Autowired
    private PostcardRenderer postcardRenderer;

    @MockitoBean
    private LockProvider lockProvider;

    @Autowired
    private ApiClientRepository apiClientRepository;

    private ApiClient testClient;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        apiClientRepository.deleteAll();


        testClient = apiClientRepository.save(ApiClient.builder()
                .id(UUID.randomUUID().toString())
                .name("worker-test-client")
                .apiKey("worker-api-key")
                .hmacSecretKey("worker-hmac-secret")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .enabled(true)
                .dailyQuota(100)
                .rpmLimit(100)
                .build());

        // Mock ShedLock to always acquire lock
        SimpleLock simpleLock = mock(SimpleLock.class);
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLock));
    }

    @Test
    @DisplayName("✅ Worker: Should pick up PENDING requests and move them to SENDING")
    void should_process_pending_requests() throws Exception {
        // Given: Create a PENDING email request
        EmailRequest pendingRequest = EmailRequest.builder()
                .id(UUID.randomUUID().toString())
                .apiClient(testClient)
                .toEmail("test@example.com")
                .subject("Test Subject")
                .message("Test Message")
                .imageSource(ImageSource.DEFAULT)
                .langCode("en")
                .createdAt(Instant.now())
                .status(EmailRequestStatus.PENDING)
                .retryCount(0)
                .build();
        repository.saveAndFlush(pendingRequest);
        
        // Verify the request was saved as PENDING
        EmailRequest savedRequest = repository.findById(pendingRequest.getId()).orElseThrow();
        assertThat(savedRequest.getStatus()).isEqualTo(EmailRequestStatus.PENDING);

        // When: The worker processes pending requests
        emailRequestWorker.processPendingRequestsAutomatically();

        // Then: The request should be marked as SENDING
        entityManager.clear(); // Clear persistence context to force fresh read
        EmailRequest processedRequest = repository.findById(pendingRequest.getId()).orElseThrow();
        
        assertThat(processedRequest.getStatus()).isEqualTo(EmailRequestStatus.SENDING);
        assertThat(processedRequest.getProcessedAt()).isNotNull();
        
        // Verify the email sender service was called
        verify(emailSenderService, times(1)).sendEmail(any(EmailRequest.class), any(PostcardHtml.class));
    }

    @Test
    @DisplayName("❌ Worker: Should mark as FAILED and increment retry count on error")
    void should_handle_processing_errors() throws Exception {
        // Given: Create a PENDING email request
        EmailRequest pendingRequest = EmailRequest.builder()
                .id(UUID.randomUUID().toString())
                .apiClient(testClient)
                .toEmail("test@example.com")
                .subject("Test Subject")
                .message("Test Message")
                .imageSource(ImageSource.DEFAULT)
                .langCode("en")
                .createdAt(Instant.now())
                .status(EmailRequestStatus.PENDING)
                .retryCount(0)
                .build();
        repository.save(pendingRequest);

        // And: The email sender service will throw an exception
        doThrow(new RuntimeException("SMTP connection failed"))
                .when(emailSenderService)
                .sendEmail(any(EmailRequest.class), any(PostcardHtml.class));

        // When: The worker processes pending requests
        emailRequestWorker.processPendingRequestsAutomatically();

        // Then: The request should be marked as FAILED
        entityManager.clear(); // Clear persistence context to force fresh read
        EmailRequest failedRequest = repository.findById(pendingRequest.getId()).orElseThrow();
        
        assertThat(failedRequest.getStatus()).isEqualTo(EmailRequestStatus.FAILED);
        assertThat(failedRequest.getRetryCount()).isEqualTo(1);
        assertThat(failedRequest.getErrorMessage()).contains("RuntimeException");
        assertThat(failedRequest.getErrorMessage()).contains("SMTP connection failed");
        
        // Verify the email sender service was called
        verify(emailSenderService, times(1)).sendEmail(any(EmailRequest.class), any(PostcardHtml.class));
    }
    @Test
    @DisplayName("✅ Worker: Should reset emails stuck in SENDING for too long (Self-Healing)")
    void should_reset_stuck_requests() {
        // Given: A request in SENDING state for > 5 minutes
        EmailRequest stuckRequest = EmailRequest.builder()
                .id(UUID.randomUUID().toString())
                .apiClient(testClient)
                .toEmail("stuck@example.com")
                .subject("Stuck Email")
                .message("I am stuck")
                .imageSource(ImageSource.DEFAULT)
                .langCode("en")
                .createdAt(Instant.now().minus(10, java.time.temporal.ChronoUnit.MINUTES))
                .processedAt(Instant.now().minus(10, java.time.temporal.ChronoUnit.MINUTES))
                .status(EmailRequestStatus.SENDING)
                .retryCount(0)
                .build();
        repository.saveAndFlush(stuckRequest);

        // When: The self-healing task runs
        emailRequestWorker.cleanupStuckRequests();

        // Then: The request should be reset to PENDING and retry count incremented
        entityManager.clear();
        EmailRequest healedRequest = repository.findById(stuckRequest.getId()).orElseThrow();

        assertThat(healedRequest.getStatus()).isEqualTo(EmailRequestStatus.PENDING);
        assertThat(healedRequest.getRetryCount()).isEqualTo(1);
        assertThat(healedRequest.getErrorMessage()).contains("Self-Healing");
    }
}