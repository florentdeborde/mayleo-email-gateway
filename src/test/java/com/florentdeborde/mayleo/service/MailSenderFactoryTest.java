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
    @DisplayName("✅ getSender: Should configure SMTP with STARTTLS for port 587")
    void getSender_Smtp587_ShouldConfigureStartTls() {
        // GIVEN
        String clientId = "client-587";
        EmailConfig config = EmailConfig.builder()
                .provider(EmailProvider.SMTP)
                .smtpHost("smtp.custom.com")
                .smtpPort(587)
                .smtpUsername("user-smtp")
                .smtpPassword("pass-smtp")
                .smtpTls(true)
                .build();

        // WHEN
        JavaMailSenderImpl impl = (JavaMailSenderImpl) factory.getSender(clientId, config);

        // THEN
        assertEquals(587, impl.getPort());
        Properties props = impl.getJavaMailProperties();
        assertEquals(true, props.get("mail.smtp.starttls.enable"));
        assertEquals("5000", props.get("mail.smtp.timeout"));
        assertEquals("false", props.get("mail.smtp.ssl.enable"), "SSL should be explicitly disabled for port 587");
    }

    @Test
    @DisplayName("✅ getSender: Should configure SMTP with SSL for port 465")
    void getSender_Smtp465_ShouldConfigureSsl() {
        // GIVEN
        String clientId = "client-465";
        EmailConfig config = EmailConfig.builder()
                .provider(EmailProvider.SMTP)
                .smtpHost("smtp.brevo.com")
                .smtpPort(465)
                .smtpUsername("user-465")
                .smtpPassword("pass-465")
                .smtpTls(true) // Explicitly set but logic should prioritize SSL for 465
                .build();

        // WHEN
        JavaMailSenderImpl impl = (JavaMailSenderImpl) factory.getSender(clientId, config);

        // THEN
        assertEquals(465, impl.getPort());
        Properties props = impl.getJavaMailProperties();

        assertEquals("true", props.get("mail.smtp.ssl.enable"));
        assertEquals("465", props.get("mail.smtp.socketFactory.port"));
        assertEquals("javax.net.ssl.SSLSocketFactory", props.get("mail.smtp.socketFactory.class"));
        assertEquals("false", props.get("mail.smtp.starttls.enable"), "STARTTLS should be disabled when SSL is active");
    }

    @Test
    @DisplayName("✅ getSender: Should auto-configure Google host and port")
    void getSender_Google_ShouldAutoConfigure() {
        // GIVEN
        EmailConfig googleConfig = EmailConfig.builder()
                .provider(EmailProvider.GOOGLE)
                .smtpUsername("test@gmail.com")
                .smtpPassword("app-password")
                .smtpPort(587) // Added to prevent unboxing NPE
                .smtpTls(true)  // Added to prevent Properties NPE
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
                .smtpPort(587) // Added to prevent unboxing NPE
                .smtpTls(true)  // Added to prevent Properties NPE
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
        assertNotSame(firstInstance, secondInstance, "A new instance should be created after cache invalidation");
    }
}