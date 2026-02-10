package com.florentdeborde.mayleo.service;

import com.florentdeborde.mayleo.dto.internal.Postcard;
import com.florentdeborde.mayleo.dto.internal.PostcardHtml;
import com.florentdeborde.mayleo.metrics.MayleoMetrics;
import com.florentdeborde.mayleo.model.*;
import com.florentdeborde.mayleo.repository.EmailConfigRepository;
import com.florentdeborde.mayleo.repository.EmailRequestRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.hibernate.validator.internal.util.Contracts.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test - EmailSenderService")
class EmailSenderServiceTest {

    @Mock
    private MailSenderFactory mailSenderFactory;
    @Mock
    private EmailRequestRepository emailRequestRepository;
    @Mock
    private EmailConfigRepository emailConfigRepository;
    @Mock
    private JavaMailSender mockMailSender;
    @Mock
    private MayleoMetrics metrics;

    @InjectMocks
    private EmailSenderService emailSenderService;

    private EmailRequest request;
    private PostcardHtml postcardHtml;
    private EmailConfig emailConfig;

    private final String REQUEST_ID = "req-123";
    private final String CLIENT_ID = "client-789";
    private final int MAX_RETRIES = 3;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailSenderService, "maxRetries", MAX_RETRIES);

        ApiClient apiClient = ApiClient.builder().id(CLIENT_ID).build();

        request = EmailRequest.builder()
                .id(REQUEST_ID)
                .apiClient(apiClient)
                .toEmail("recipient@example.com")
                .subject("Hello")
                .retryCount(0)
                .status(EmailRequestStatus.SENDING)
                .build();

        postcardHtml = new PostcardHtml("<html></html>", new Postcard("path/to/img.jpg", true));

        emailConfig = EmailConfig.builder()
                .apiClient(apiClient)
                .senderEmail("sender@client.com")
                .provider(EmailProvider.SMTP)
                .senderEmail("sender@client.com")
                .smtpHost("smtp.test.com")
                .smtpPort(587)
                .smtpUsername("user")
                .smtpPassword("password")
                .build();
    }

    @Test
    @DisplayName("✅ sendEmail: Should send successfully and update status to SENT")
    void sendEmail_Success_ShouldUpdateStatusToSent() {
        // GIVEN
        MimeMessage mockMimeMessage = mock(MimeMessage.class);

        when(emailConfigRepository.findByApiClient(any())).thenReturn(Optional.of(emailConfig));
        when(mailSenderFactory.getSender(eq(CLIENT_ID), eq(emailConfig))).thenReturn(mockMailSender);
        when(mockMailSender.createMimeMessage()).thenReturn(mockMimeMessage);
        when(emailRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        // WHEN
        emailSenderService.sendEmail(request, postcardHtml);

        // THEN
        verify(mockMailSender).send(any(MimeMessage.class));
        verify(emailRequestRepository).save(argThat(req -> req.getStatus() == EmailRequestStatus.SENT));
        verify(metrics).recordEmailDelivery(MayleoMetrics.STATUS_SENT);
    }

    @Test
    @DisplayName("❌ sendEmail: Should reschedule to PENDING on failure if retries remain")
    void sendEmail_Failure_ShouldReschedule() {
        // GIVEN
        when(emailConfigRepository.findByApiClient(any())).thenReturn(Optional.of(emailConfig));
        when(mailSenderFactory.getSender(anyString(), any())).thenReturn(mockMailSender);
        when(mockMailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(emailRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        doThrow(new RuntimeException("SMTP Connection Error")).when(mockMailSender).send(any(MimeMessage.class));

        // WHEN
        emailSenderService.sendEmail(request, postcardHtml);

        // THEN
        verify(emailRequestRepository).save(argThat(req ->
                req.getStatus() == EmailRequestStatus.PENDING && req.getRetryCount() == 1
        ));
        verify(metrics).recordEmailDelivery(MayleoMetrics.STATUS_FAILED);
    }

    @Test
    @DisplayName("❌ sendEmail: Should mark as FAILED when max retries are reached")
    void sendEmail_MaxRetries_ShouldMarkAsFailed() {
        // GIVEN
        request.setRetryCount(MAX_RETRIES - 1); // 2 sur 3

        when(emailConfigRepository.findByApiClient(any())).thenReturn(Optional.of(emailConfig));
        when(mailSenderFactory.getSender(anyString(), any())).thenReturn(mockMailSender);
        when(mockMailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(emailRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        doThrow(new RuntimeException("Last try failure")).when(mockMailSender).send(any(MimeMessage.class));

        // WHEN
        emailSenderService.sendEmail(request, postcardHtml);

        // THEN
        verify(emailRequestRepository).save(argThat(req ->
                req.getStatus() == EmailRequestStatus.FAILED && req.getRetryCount() == MAX_RETRIES
        ));
    }

    @Test
    @DisplayName("♻ invalidateConfigCache: Should clear local cache and call factory invalidation")
    void invalidateConfigCache_ShouldClearCacheAndPropagate() {
        // GIVEN
        String clientId = "client-789";
        when(emailConfigRepository.findByApiClient(any())).thenReturn(Optional.of(emailConfig));
        when(mailSenderFactory.getSender(anyString(), any())).thenReturn(mockMailSender);
        when(mockMailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(emailRequestRepository.findById(anyString())).thenReturn(Optional.of(request));

        // First call to fill intern cache
        emailSenderService.sendEmail(request, postcardHtml);
        verify(emailConfigRepository, times(1)).findByApiClient(any());

        // WHEN & THEN
        emailSenderService.invalidateConfigCache(clientId);
        verify(mailSenderFactory).invalidateSenderCache(clientId);

        // WHEN & THEN
        emailSenderService.sendEmail(request, postcardHtml);
        verify(emailConfigRepository, times(2)).findByApiClient(any());
    }

    @Test
    @DisplayName("❌ sendEmail: Should throw exception and update status when configuration is incomplete")
    void sendEmail_IncompleteConfig_ShouldFail() {
        // GIVEN
        emailConfig.setSmtpHost(null); // Incomplete config

        when(emailConfigRepository.findByApiClient(any())).thenReturn(Optional.of(emailConfig));
        when(emailRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        ArgumentCaptor<EmailRequest> requestCaptor = ArgumentCaptor.forClass(EmailRequest.class);

        // WHEN
        emailSenderService.sendEmail(request, postcardHtml);

        // THEN
        verify(emailRequestRepository).save(requestCaptor.capture());

        EmailRequest savedRequest = requestCaptor.getValue();
        assertEquals(EmailRequestStatus.PENDING, savedRequest.getStatus());
        assertTrue(savedRequest.getErrorMessage().contains("incomplete"), "Error message should mention 'incomplete'");
        assertEquals(1, savedRequest.getRetryCount());

        verify(metrics).recordEmailDelivery(MayleoMetrics.STATUS_FAILED);
        verifyNoInteractions(mailSenderFactory);
    }
}