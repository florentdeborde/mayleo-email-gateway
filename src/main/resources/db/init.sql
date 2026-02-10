-- =============================================================================
-- Mayleo Email Gateway
-- Database initialization script (MySQL)
-- =============================================================================

-- =============================================================================
-- Create database (MySQL on Aiven for instance)
-- Create connection to database (from DBeaver for instance)
-- Execute script
-- =============================================================================

-- =============================================================================
-- Table: api_client
-- Represents a third-party application using the Mayleo API
-- =============================================================================
CREATE TABLE IF NOT EXISTS api_client (
    id CHAR(36) NOT NULL
        COMMENT 'Technical UUID identifier of the API client',

    name VARCHAR(100) NOT NULL
        COMMENT 'Human-readable name of the client',

    api_key VARCHAR(64) NOT NULL
        COMMENT 'Unique API key used to authenticate API requests',

    hmac_secret_key VARCHAR(255) NOT NULL
        COMMENT 'AES-encrypted private secret used for HMAC-SHA256 request signing',

    enabled BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT 'Indicates whether the client is allowed to use the API',

    daily_quota INT DEFAULT 240
        COMMENT 'Maximum number of emails allowed per day (NULL = unlimited)',

    rpm_limit INT DEFAULT 10
            COMMENT 'Requests Per Minute limit (Anti-spam)',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Timestamp when the client was created',

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'Timestamp of the last update',

    PRIMARY KEY (id),
    UNIQUE KEY uk_api_client_api_key (api_key)
) ENGINE=InnoDB
COMMENT='API clients allowed to use the Mayleo Email Gateway';

-- =============================================================================
-- Table: api_client_domain
-- Whitelisted domains for an API client (Multi-domain support)
-- =============================================================================
CREATE TABLE IF NOT EXISTS api_client_domain (
    id CHAR(36) NOT NULL,
    api_client_id CHAR(36) NOT NULL,
    domain VARCHAR(255) NOT NULL
        COMMENT 'Whitelisted domain or origin (ex: https://mywebsite.com)',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_client_domain (api_client_id, domain),
    CONSTRAINT fk_client_domain_client
        FOREIGN KEY (api_client_id)
        REFERENCES api_client (id)
        ON DELETE CASCADE
) ENGINE=InnoDB
COMMENT='Authorized origins for browser-side API requests';

-- =============================================================================
-- Table: email_config
-- Stores email configuration linked to an API client
-- =============================================================================
CREATE TABLE IF NOT EXISTS email_config (
    id CHAR(36) NOT NULL
        COMMENT 'Technical UUID identifier of the email configuration',

    api_client_id CHAR(36) NOT NULL
        COMMENT 'Reference to the API client owning this configuration',

    provider VARCHAR(20) NOT NULL DEFAULT 'SMTP'
        COMMENT 'Email provider (GOOGLE, MICROSOFT, SMTP)',
    CHECK (provider IN ('GOOGLE', 'MICROSOFT', 'SMTP')),

    sender_email VARCHAR(255) DEFAULT NULL
        COMMENT 'Email address used as sender',

    oauth_client_id TEXT DEFAULT NULL
        COMMENT 'OAuth client ID (Google / Microsoft)',

    oauth_client_secret TEXT DEFAULT NULL
        COMMENT 'OAuth client secret (Google / Microsoft)',

    oauth_refresh_token TEXT DEFAULT NULL
        COMMENT 'OAuth refresh token used to obtain access tokens',

    smtp_host VARCHAR(255) DEFAULT NULL
        COMMENT 'SMTP server host',

    smtp_port INT DEFAULT NULL
        COMMENT 'SMTP server port',

    smtp_username VARCHAR(255) DEFAULT NULL
        COMMENT 'SMTP authentication username',

    smtp_password TEXT DEFAULT NULL
        COMMENT 'SMTP authentication password',

    smtp_tls BOOLEAN DEFAULT TRUE
        COMMENT 'Indicates whether TLS is enabled for SMTP',

    default_subject VARCHAR(255) NOT NULL
        DEFAULT 'Mayleo sends you a « postcard » email'
        COMMENT 'Default email subject when none is provided',
        CHECK (CHAR_LENGTH(default_subject) > 0),

    default_message TEXT
        COMMENT 'Default email body when none is provided',
        CHECK (CHAR_LENGTH(default_message) > 0),

    default_language VARCHAR(5) DEFAULT 'en'
        COMMENT 'Default language code for email content',

    enabled BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT 'Indicates whether this email configuration is active',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Timestamp when the configuration was created',

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'Timestamp of the last update',

    PRIMARY KEY (id),
    UNIQUE KEY uk_email_config_client (api_client_id),
    CONSTRAINT fk_email_config_client
        FOREIGN KEY (api_client_id)
        REFERENCES api_client (id)
        ON DELETE CASCADE
) ENGINE=InnoDB
COMMENT='Email configuration associated with an API client';

-- =============================================================================
-- Table: storage_config
-- Stores storage configuration linked to an API client
-- =============================================================================
CREATE TABLE storage_config (
    id CHAR(36) NOT NULL,
    api_client_id CHAR(36) NOT NULL,

    provider VARCHAR(20) NOT NULL
        COMMENT 'LOCAL, S3, CLOUDINARY',

    base_url VARCHAR(512) NOT NULL
        COMMENT 'Public base URL used in emails',

    root_path VARCHAR(255) NOT NULL
        COMMENT 'Client-specific folder or prefix',

    credentials_ref VARCHAR(100) DEFAULT NULL
        COMMENT 'Reference to env / secret manager key',

    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_storage_config_client (api_client_id),
    CONSTRAINT fk_storage_client
        FOREIGN KEY (api_client_id)
        REFERENCES api_client(id)
        ON DELETE CASCADE
);

-- =============================================================================
-- Table: email_request
-- Stores incoming email requests to be processed asynchronously
-- =============================================================================
CREATE TABLE IF NOT EXISTS email_request (
    id CHAR(36) NOT NULL
        COMMENT 'Technical UUID identifier of the email request',

    api_client_id CHAR(36) NOT NULL
        COMMENT 'Reference to the API client who sent the request',

    to_email VARCHAR(255) NOT NULL
        COMMENT 'Destination email address',

    lang_code VARCHAR(5) DEFAULT NULL
        COMMENT 'Requested language code (NULL = use client default language)',

    subject VARCHAR(255) DEFAULT NULL
        COMMENT 'Email subject used for this request',

    message TEXT NOT NULL
        COMMENT 'Custom email body provided by the client',

    image_source VARCHAR(20) NOT NULL DEFAULT 'DEFAULT'
        COMMENT 'Image source (DEFAULT, CLIENT_STORAGE)',

    image_path VARCHAR(255) DEFAULT NULL
        COMMENT 'Relative image path or filename (ex: postcard-01.jpg)',

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        COMMENT 'Processing status (PENDING, SENDING, SENT, FAILED)',
    CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),

    error_message TEXT DEFAULT NULL
        COMMENT 'Error message if the email processing failed',

    retry_count INT NOT NULL DEFAULT 0
        COMMENT 'Number of retry attempts for this request',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Timestamp when the request was created',

    processed_at TIMESTAMP DEFAULT NULL
        COMMENT 'Timestamp when the request was processed',

    idempotency_key CHAR(36)
        COMMENT 'Optional key provided by client to ensure request idempotency',

    PRIMARY KEY (id),

    KEY idx_email_request_status (status),
    KEY idx_email_request_client (api_client_id),

    CONSTRAINT fk_email_request_client
        FOREIGN KEY (api_client_id)
        REFERENCES api_client (id)
        ON DELETE CASCADE
) ENGINE=InnoDB
COMMENT='Incoming email requests processed asynchronously';

CREATE INDEX idx_email_request_client_created
ON email_request (api_client_id, created_at);

CREATE INDEX idx_email_request_status_created
ON email_request (status, created_at);

CREATE UNIQUE INDEX idx_email_request_idempotency
ON email_request (api_client_id, idempotency_key);

-- =============================================================================
-- Table: shedlock
-- Used by ShedLock to synchronize scheduled tasks across multiple instances.
-- Ensures that only one instance executes a specific task at a time.
-- =============================================================================
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- =============================================================================
-- Sample data
-- =============================================================================

-- Insert sample data for api_client
INSERT INTO api_client (id, name, api_key, hmac_secret_key, enabled, daily_quota, rpm_limit)
VALUES ('11111111-1111-1111-1111-111111111111', 'MAYLEO', 'SALTED_AND_HASHED_API_KEY', 'AES_ENCRYPTED_HMAC_SECRET_KEY', TRUE, 240, 10);

-- Insert sample data for api_client_domain
INSERT INTO api_client_domain (id, api_client_id, domain)
VALUES (UUID(), '11111111-1111-1111-1111-111111111111', 'http://localhost:8080');

-- Insert sample data for email_config
INSERT INTO email_config (
    id,
    api_client_id,
    provider,
    sender_email,
    oauth_client_id,
    oauth_client_secret,
    oauth_refresh_token,
    smtp_host,
    smtp_port,
    smtp_username,
    smtp_password,
    smtp_tls,
    default_subject,
    default_message,
    default_language,
    enabled
)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'SMTP',
    'florentdeborde.mayleo@gmail.com',
    NULL,
    NULL,
    NULL,
    'smtp.gmail.com',
    '587',
    'florentdeborde.mayleo@gmail.com',
    'AES_ENCRYPTED_SMTP_PASSWORD',
    TRUE,
    'Mayleo sends you a « postcard » email',
    'This is a « postcard » from Mayleo.',
    'en',
    TRUE
);

INSERT INTO storage_config (
    id,
    api_client_id,
    provider,
    base_url,
    root_path,
    credentials_ref,
    enabled
)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    'LOCAL',
    '/',
    'postcards/',
    NULL,
    TRUE
);
