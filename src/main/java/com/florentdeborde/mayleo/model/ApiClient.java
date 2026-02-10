package com.florentdeborde.mayleo.model;

import com.florentdeborde.mayleo.security.converter.HmacEncryptionConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "api_client")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"apiKey", "allowedDomains"})
public class ApiClient {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 64, unique = true)
    private String apiKey;

    @Column(nullable = false, length = 255)
    @Convert(converter = HmacEncryptionConverter.class)
    private String hmacSecretKey;

    @Column(nullable = false)
    private boolean enabled = true;

    private Integer dailyQuota;

    private Integer rpmLimit;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "api_client_domain",
            joinColumns = @JoinColumn(name = "api_client_id")
    )
    @Column(name = "domain")
    @Builder.Default
    private Set<String> allowedDomains = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
