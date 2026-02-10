package com.florentdeborde.mayleo.service;

import com.florentdeborde.mayleo.dto.internal.Postcard;
import com.florentdeborde.mayleo.dto.internal.PostcardHtml;
import com.florentdeborde.mayleo.model.ApiClient;
import com.florentdeborde.mayleo.model.EmailRequest;
import com.florentdeborde.mayleo.model.EmailRequestStatus;
import com.florentdeborde.mayleo.repository.EmailRequestRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test - EmailRequestWorker")
class EmailRequestWorkerTest {

    @Mock
    private EmailRequestRepository repository;
    @Mock
    private EmailSenderService emailSenderService;
    @Mock
    private PostcardRenderer postcardRenderer;

    @InjectMocks
    private EmailRequestWorker emailRequestWorker;

    private ApiClient apiClient;

    @BeforeEach
    void setUp() {
        apiClient = ApiClient.builder()
                .id("client-123")
                .name("Test Client")
                .enabled(true)
                .dailyQuota(24)
                .rpmLimit(1)
                .build();
    }

    @Test
    @DisplayName("✅ processPendingRequestsAutomatically: Should delegate rendering and simplified dispatch")
    void processPendingRequestsAutomatically_Success() {
        // GIVEN
        EmailRequest request = EmailRequest.builder()
                .id("req-123")
                .status(EmailRequestStatus.PENDING)
                .apiClient(apiClient)
                .build();

        PostcardHtml mockHtml = new PostcardHtml("<html></html>", new Postcard("img.jpg", true));

        when(repository.findTop100ByStatusOrderByCreatedAtAsc(EmailRequestStatus.PENDING))
                .thenReturn(List.of(request));
        when(postcardRenderer.render(eq(request), anyString())).thenReturn(mockHtml);

        // WHEN
        emailRequestWorker.processPendingRequestsAutomatically();

        // THEN
        verify(postcardRenderer).render(request, "From Mayleo");
        verify(emailSenderService).sendEmail(request, mockHtml);
        assertEquals(EmailRequestStatus.SENDING, request.getStatus());
        assertNotNull(request.getProcessedAt());
        verify(repository).saveAndFlush(request);
    }

    @Test
    @DisplayName("❌ processPendingRequestsAutomatically: Should mark as FAILED if rendering or dispatch fails")
    void processPendingRequestsAutomatically_Failure() {
        // GIVEN
        EmailRequest request = EmailRequest.builder()
                .id("fail")
                .status(EmailRequestStatus.PENDING)
                .apiClient(apiClient)
                .retryCount(0)
                .build();

        when(repository.findTop100ByStatusOrderByCreatedAtAsc(any())).thenReturn(List.of(request));

        when(postcardRenderer.render(any(), any())).thenThrow(new RuntimeException("Render error"));

        // WHEN
        emailRequestWorker.processPendingRequestsAutomatically();

        // THEN
        assertEquals(EmailRequestStatus.FAILED, request.getStatus());
        assertTrue(request.getErrorMessage().contains("Render error"));
        assertEquals(1, request.getRetryCount());
        verify(repository).save(request);
    }

    @Test
    @DisplayName("✅ processPendingRequestsAutomatically: Check ShedLock annotation presence")
    void processPendingRequestsAutomatically_shouldHaveShedLockAnnotation() throws NoSuchMethodException {
        // GIVEN: Retrieve the method from the worker class
        Method method = EmailRequestWorker.class.getDeclaredMethod("processPendingRequestsAutomatically");

        // WHEN: Look for the @SchedulerLock annotation
        SchedulerLock annotation = method.getAnnotation(SchedulerLock.class);

        // THEN: Ensure the annotation is present and correctly configured
        assertThat(annotation)
                .as("@SchedulerLock annotation is missing on the method")
                .isNotNull();
        // Since we decided to keep "EmailRequestService_..." or update it. In the worker I used "EmailRequestService_processPendingEmails".
        // Let's verify what I wrote in the Worker class.
        // It should match what I put in the class.
        assertThat(annotation.name())
                .as("The lock name must match the service and method name")
                .isEqualTo("EmailRequestService_processPendingEmails");

        assertThat(annotation.lockAtMostFor())
                .as("lockAtMostFor must be defined to prevent deadlocks")
                .isNotBlank();
    }

    @Test
    @DisplayName("✅ cleanupStuckRequests: Should reset stuck SENDING requests to PENDING")
    void cleanupStuckRequests_Success() {
        // GIVEN: 2 requests stuck in SENDING for more than 5 minutes
        EmailRequest stuck1 = EmailRequest.builder()
                .id("stuck-1")
                .status(EmailRequestStatus.SENDING)
                .retryCount(0)
                .build();

        EmailRequest stuck2 = EmailRequest.builder()
                .id("stuck-2")
                .status(EmailRequestStatus.SENDING)
                .retryCount(1)
                .build();

        when(repository.findByStatusAndProcessedAtBefore(eq(EmailRequestStatus.SENDING), any(Instant.class)))
                .thenReturn(List.of(stuck1, stuck2));

        // WHEN
        emailRequestWorker.cleanupStuckRequests();

        // THEN: Verify status reset, retry count incremented and error message set
        assertEquals(EmailRequestStatus.PENDING, stuck1.getStatus());
        assertEquals(1, stuck1.getRetryCount());
        assertEquals("Self-Healing: Reset from SENDING (Stuck)", stuck1.getErrorMessage());

        assertEquals(EmailRequestStatus.PENDING, stuck2.getStatus());
        assertEquals(2, stuck2.getRetryCount());

        // Verify they were saved back to repository
        verify(repository).save(stuck1);
        verify(repository).save(stuck2);
    }

    @Test
    @DisplayName("✅ cleanupStuckRequests: Should do nothing if no stuck requests found")
    void cleanupStuckRequests_NoStuckRequests() {
        // GIVEN
        when(repository.findByStatusAndProcessedAtBefore(eq(EmailRequestStatus.SENDING), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // WHEN
        emailRequestWorker.cleanupStuckRequests();

        // THEN
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("✅ cleanupStuckRequests: Check ShedLock annotation presence")
    void cleanupStuckRequests_shouldHaveShedLockAnnotation() throws NoSuchMethodException {
        // GIVEN
        Method method = EmailRequestWorker.class.getDeclaredMethod("cleanupStuckRequests");

        // WHEN
        SchedulerLock annotation = method.getAnnotation(SchedulerLock.class);

        // THEN
        assertThat(annotation)
                .as("@SchedulerLock annotation is missing on cleanupStuckRequests")
                .isNotNull();

        assertThat(annotation.name())
                .isEqualTo("EmailRequestService_cleanupStuckRequests");

        assertThat(annotation.lockAtMostFor())
                .isEqualTo("5m");
    }
}
