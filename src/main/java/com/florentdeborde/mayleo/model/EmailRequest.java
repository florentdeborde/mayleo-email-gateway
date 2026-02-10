package com.florentdeborde.mayleo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "email_request",
        indexes = {
                // Optimizes Quota checks (Daily & RPM)
                @Index(name = "idx_email_request_client_date", columnList = "api_client_id, createdAt"),
                // Optimizes the @Scheduled worker (findTop100)
                @Index(name = "idx_email_request_status_date", columnList = "status, createdAt")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailRequest {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @ManyToOne
    @JoinColumn(name = "api_client_id", nullable = false)
    private ApiClient apiClient;

    @Column(length = 255)
    private String toEmail;

    @Column(length = 5)
    private String langCode;

    private String subject;

    @Lob
    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_source", nullable = false)
    private ImageSource imageSource = ImageSource.DEFAULT;

    @Column(name = "image_path", length = 255)
    private String imagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailRequestStatus status = EmailRequestStatus.PENDING;

    @Lob private String errorMessage;
    private int retryCount = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant processedAt;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;
}
