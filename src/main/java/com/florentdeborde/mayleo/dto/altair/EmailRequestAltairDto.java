package com.florentdeborde.mayleo.dto.altair;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class EmailRequestAltairDto {
    private String id;
    private String toEmail;
    private String subject;
    private String langCode;
    private String status;
    private int retryCount;
    private String apiClientName;
    private Instant createdAt;
    private Instant processedAt;
    private String errorMessage;
}
