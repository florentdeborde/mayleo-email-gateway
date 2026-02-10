package com.florentdeborde.mayleo.model;

import com.florentdeborde.mayleo.security.converter.SmtpEncryptionConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailConfig {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @OneToOne
    @JoinColumn(name = "api_client_id", nullable = false)
    private ApiClient apiClient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailProvider provider = EmailProvider.SMTP;

    private String senderEmail;

    @Column(columnDefinition = "TEXT")
    private String oauthClientId;

    @Column(columnDefinition = "TEXT")
    private String oauthClientSecret;

    @Column(columnDefinition = "TEXT")
    private String oauthRefreshToken;

    private String smtpHost;

    private Integer smtpPort;

    private String smtpUsername;

    @Convert(converter = SmtpEncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String smtpPassword;

    private Boolean smtpTls = true;

    private String defaultSubject = "Hello";

    @Lob
    private String defaultMessage;

    private String defaultLanguage = "en";

    private boolean enabled = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
