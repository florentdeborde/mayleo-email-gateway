package com.florentdeborde.mayleo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "storage_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageConfig {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @ManyToOne
    @JoinColumn(name = "api_client_id", nullable = false)
    private ApiClient apiClient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StorageProvider provider;

    @Column(length = 512, nullable = false)
    private String baseUrl;

    @Column(length = 255, nullable = false)
    private String rootPath;

    @Column(length = 100)
    private String credentialsRef; // secret manager or env reference

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;
}
