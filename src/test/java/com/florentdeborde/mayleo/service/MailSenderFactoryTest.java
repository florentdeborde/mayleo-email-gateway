package com.florentdeborde.mayleo.service;

import com.florentdeborde.mayleo.model.EmailConfig;
import com.florentdeborde.mayleo.model.EmailProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Test - MailSenderFactory")
class MailSenderFactoryTest {

    private MailSenderFactory factory;

    @BeforeEach
    void setUp() {
        factory = new MailSenderFactory();
    }

    @Test
    @DisplayName("✅ getSender: Should configure SMTP and cache instance")
    void getSender_Smtp_ShouldConfigureAndCache() {
        // GIVEN
        String clientId = "client-123";
        EmailConfig config = EmailConfig.builder()
                .provider(EmailProvider.SMTP)
                .smtpHost("smtp.custom.com")
                .smtpPort(587)
                .smtpUsername("user-smtp")
                .smtpPassword("pass-smtp")
                .smtpTls(true)
                .build();

        // WHEN
        JavaMailSender result1 = factory.getSender(clientId, config);
        JavaMailSender result2 = factory.getSender(clientId, config);

        // THEN - Test du Cache
        assertSame(result1, result2, "La Factory doit retourner la même instance mise en cache");

        // Verify configuration
        JavaMailSenderImpl impl = (JavaMailSenderImpl) result1;
        assertEquals("smtp.custom.com", impl.getHost());
        assertEquals(587, impl.getPort());
        assertEquals("user-smtp", impl.getUsername());

        Properties props = impl.getJavaMailProperties();
        assertEquals(true, props.get("mail.smtp.starttls.enable"));
        assertEquals("5000", props.get("mail.smtp.timeout"));
    }

    @Test
    @DisplayName("✅ getSender: Should auto-configure Google host and port")
    void getSender_Google_ShouldAutoConfigure() {
        // GIVEN
        EmailConfig googleConfig = EmailConfig.builder()
                .provider(EmailProvider.GOOGLE)
                .smtpUsername("test@gmail.com")
                .smtpPassword("app-password")
                .smtpTls(true)
                .build();

        // WHEN
        JavaMailSenderImpl impl = (JavaMailSenderImpl) factory.getSender("google-client", googleConfig);

        // THEN
        assertEquals("smtp.gmail.com", impl.getHost());
        assertEquals(587, impl.getPort());
        assertEquals("test@gmail.com", impl.getUsername());
    }

    @Test
    @DisplayName("✅ getSender: Should auto-configure Microsoft host and port")
    void getSender_Microsoft_ShouldAutoConfigure() {
        // GIVEN
        EmailConfig msConfig = EmailConfig.builder()
                .provider(EmailProvider.MICROSOFT)
                .smtpUsername("test@outlook.com")
                .smtpPassword("pass")
                .smtpTls(true)
                .build();

        // WHEN
        JavaMailSenderImpl impl = (JavaMailSenderImpl) factory.getSender("ms-client", msConfig);

        // THEN
        assertEquals("smtp.office365.com", impl.getHost());
        assertEquals(587, impl.getPort());
    }

    @Test
    @DisplayName("♻ invalidateCache: Should allow recreating sender after cache removal")
    void invalidateSenderCache_ShouldRemoveFromSenderCache() {
        // GIVEN
        String clientId = "to-be-removed";
        EmailConfig config = EmailConfig.builder()
                .provider(EmailProvider.SMTP)
                .smtpHost("localhost")
                .smtpPort(25)
                .smtpTls(true)
                .smtpUsername("user")
                .smtpPassword("pass")
                .build();
        JavaMailSender firstInstance = factory.getSender(clientId, config);

        // WHEN
        factory.invalidateSenderCache(clientId);
        JavaMailSender secondInstance = factory.getSender(clientId, config);

        // THEN
        assertNotSame(firstInstance, secondInstance, "After cache removal, a new instance should be created");
    }
}