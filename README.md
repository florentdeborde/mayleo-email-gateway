# 📨 Mayleo Email Gateway

![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![Java: 17](https://img.shields.io/badge/Java-17+-blue.svg)
![Spring Boot: 3.x](https://img.shields.io/badge/Spring_Boot-3.x-brightgreen.svg)

**Mayleo** is a high-performance, multi-tenant Email Gateway specifically engineered to centralize and secure the dispatch of **digital postcards** via **HMAC-signed requests** and visual-rich communications.

By abstracting the complexities of SMTP management and asset linking, Mayleo allows developers to send personalized, localized digital postcards via a unified API. It ensures strict isolation between clients through **HMAC integrity validation**, **AES-encrypted configurations** and rigorous **domain-whitelisted routing**, making it the perfect bridge between your application logic and your users' inboxes.

## 📑 Table of Contents

- [🎯 Core Features](#-core-features)
- [🧩 How it works](#-how-it-works)
- [📂 Project structure](#-project-structure)
- [🧱 Tech Stack](#-tech-stack)
- [🚀 Onboarding a New Client](#-onboarding-a-new-client)
- [🗺️ Roadmap & Future Evolutions](#-roadmap--future-evolutions)
- [📜 License](#-license)

## 🎯 Core Features

- **Multi-Tenant Gateway**: Robust management of multiple independent clients with isolated API keys and strictly dedicated routing.
- **Advanced Security & Integrity**:
    - **HMAC Request Signing**: Per-client signature validation to guarantee payload integrity and prevent man-in-the-middle tampering.
    - **Domain Whitelisting**: Strict **Origin header validation** ensuring API keys are only functional from authorized client environments.
    - **Developer Flexibility**: Environment-aware security flags to toggle HMAC validation in local/dev environments for easier integration testing.
- **Dedicated SMTP Routing**:
    - **Client-Specific Delivery**: Every email is routed exclusively through the client's own SMTP provider, ensuring total isolation of sender reputation.
    - **At-Rest Security**: Sensitive credentials (SMTP passwords & HMAC secrets) are **AES-256 encrypted** in the database via JPA converters, ensuring a "Zero-Knowledge" storage approach.
- **Traffic & Reliability Control**:
    - **Smart Idempotency**: Use the `X-Idempotency-Key` header to prevent duplicate processing. Safe for high-concurrency environments and network retries.
    - **RPM Protection**: Per-client Requests Per Minute limits to prevent accidental or malicious flooding.
    - **Daily Quotas**: Strict management of daily sending volumes to ensure budget and provider compliance.
    - **Proactive Rejection**: Integrated logic returning `429 Too Many Requests` status before resources are consumed.
    - **Self-Healing Mechanism**: A scheduled background process (via ShedLock) monitors tasks stuck in the `SENDING` state. If a worker crashes, the system automatically resets these tasks to `PENDING` for retry.
- **Digital Postcard Engine**: Specialized support for visual communication, linking `imageSource` and `imagePath` to create rich, branded email experiences.
- **High-Performance Architecture**:
    - **Distributed Lock (ShedLock)**: Prevents race conditions and duplicate email sending when running multiple instances of Mayleo.
    - **Multi-Level Caching**: SMTP configurations and Mailer sessions are cached to ensure sub-millisecond dispatch logic and reduced database overhead.
    - **Async Processing**: A resilient background queuing system (with `PENDING`, `SENDING`, `SENT`, `FAILED` tracking) handles delivery without blocking your main application.
- **Smart Localization**: Native support for `langCode` to deliver postcards in the recipient's language.

## 🧩 How it works

1. **Security & Integrity**:
    - **Authentication**: Requests are strictly authenticated via the `X-API-KEY` header.
    - **Integrity Check**: The system validates the `X-SIGNATURE` using an **HMAC-SHA256** algorithm.
    - **Domain Shield**: The `Origin` header is verified against the client's authorized domains.
2. **Traffic & Integrity Layer**:
    - **Idempotency Guard**: The first action in the service logic. If `X-Idempotency-Key` is recognized, Mayleo returns the existing UUID immediately, skipping all further processing.
   - **Quota Management**: Real-time check of **RPM** and **Daily Quotas**. Over-limit requests are rejected with a `429` status.
3. **Dynamic Decryption & Caching**:
    - Client configuration is retrieved and sensitive credentials are **decrypted on-the-fly**.
    - The SMTP session (`JavaMailSender`) is stored in a **multi-level cache** for immediate reuse.
4. **Queueing**: The request is persisted as an `email_request` with a `PENDING` status to ensure zero data loss.
5. **Async Dispatch**: A background worker picks up requests. Thanks to **ShedLock**, only one instance processes the queue at a time in multi-node deployments. It assembles the **Digital Postcard** and dispatches it.

## 📂 Project structure
```
com.florentdeborde.mayleo
├── config/
├── controller/
├── dto/
├── exception/
├── metrics/
├── model/
├── repository/
├── security/
└── service/
```

## 🧱 Tech stack
**Java 17** • **Spring Boot 3** • **Spring Data JPA** • **Jakarta Mail** • **Scheduled Tasks**

## 🚀 Onboarding a New Client

Before starting the gateway, ensure you have defined the following environment variables:

| Variable          | Usage           | Description                                                     |
|:------------------|:----------------|:----------------------------------------------------------------|
| `MAYLEO_KEY_SMTP` | **AES-256**     | 32-character key used to encrypt/decrypt SMTP passwords.        |
| `MAYLEO_KEY_HMAC` | **AES-256**     | 32-character key used to encrypt/decrypt Clients' HMAC secrets. |
| `MAYLEO_KEY_SALT` | **Salt**        | Secret salt for API key hashing.                                |
| `DB_HOST`         | **Connection**  | Database server hostname or IP address.                         |
| `DB_PORT`         | **Connection**  | Database server port (e.g., `3306` for MySQL).                  |
| `DB_NAME`         | **Connection**  | Name of the schema/database for Mayleo.                         |
| `DB_USERNAME`     | **Credentials** | Username used to connect to the database.                       |
| `DB_PASSWORD`     | **Credentials** | Password used to connect to the database.                       |

### Configuration
To register a new service in Mayleo, execute these three queries in sequence. These examples use placeholders that you **must replace with your generated values**.

> [!IMPORTANT] 
> **Security Requirement**:
> 1. **Identification**: `api_key` must be **SHA-256 Hashed** with `MAYLEO_KEY_SALT`.
> 2. **Integrity**: `hmac_secret` must be **AES-256 Encrypted** with `MAYLEO_KEY_HMAC`.
> 3. **Delivery**: `smtp_password` must be **AES-256 Encrypted** with `MAYLEO_KEY_SMTP`.

#### 1️⃣ Step: Identity & Quotas

Create the client profile and set its operational boundaries (Anti-spam).

```sql
-- Replace 'YOUR_CLIENT_NAME', 'YOUR_HASHED_KEY', 'YOUR_ENCRYPTED_HMAC'
INSERT INTO api_client (id, name, api_key, hmac_secret_key, enabled, daily_quota, rpm_limit)
VALUES (
    UUID(), 
    'YOUR_CLIENT_NAME', 
    'YOUR_HASHED_KEY',           -- SHA256(raw_api_key + salt)
    'YOUR_ENCRYPTED_HMAC',       -- AES(raw_hmac_secret, key_hmac)
    TRUE, 
    500, -- Daily email quota
    20   -- Requests per minute limit
);
```

#### 2️⃣ Step: Security Whitelisting

Restricts API access to authorized origins to prevent unauthorized third-party calls.

```sql
-- Replace 'https://your-app-domain.com' and 'YOUR_CLIENT_NAME'
INSERT INTO api_client_domain (id, api_client_id, domain)
SELECT UUID(), id, 'https://your-app-domain.com'
FROM api_client 
WHERE name = 'YOUR_CLIENT_NAME';
```

#### 3️⃣ Step: Dedicated SMTP Relay

Connect the client's dedicated SMTP infrastructure to ensure independent sender reputation.

```sql
-- Replace SMTP details and 'YOUR_ENCRYPTED_PASSWORD'
INSERT INTO email_config (
    id, api_client_id, provider, sender_email, 
    smtp_host, smtp_port, smtp_username, smtp_password, 
    smtp_tls, default_subject, default_message, default_language
)
SELECT 
    UUID(), id, 'SMTP', 'contact@domain.com',
    'smtp.provider.com', 587, 'smtp_user', 
    'YOUR_ENCRYPTED_PASSWORD',   -- AES(raw_smtp_password, key_smtp)
    TRUE, 
    'Default Postcard Subject', 
    'Default message content.', 
    'en'
FROM api_client 
WHERE name = 'YOUR_CLIENT_NAME';
```

#### 4️⃣ Step: Client Integration (HMAC Signature)

Every request must include the cryptographic signature in the headers. Here is the logic the client must implement:

1. **Payload**: Take the raw JSON body of the request.
2. **Secret**: Use the plain-text `hmac_secret` assigned to the client.
3. **Algorithm**: Compute an **HMAC-SHA256** hash.
4. **Header**: Send the result in the `X-SIGNATURE` header.

> [!TIP]
> **Example (Node.js)**:
> ```javascript
> const crypto = require('crypto');
> const signature = crypto.createHmac('sha256', HMAC_SECRET)
>                         .update(JSON.stringify(payload))
>                         .digest('hex');
> ```

### ⚡ Request
To send an email, send a `POST` request to the email endpoint.

```bash
  curl -X 'POST' \
    'http://localhost:8080/email-request' \
    -H 'Accept: text/plain' \
    -H 'X-API-KEY: API_KEY' \
    -H 'X-SIGNATURE: SIGNATURE' \
    -H 'X-Idempotency-Key: unique-request-id-123' \
    -H 'Origin: https://your-app-domain.com' \
    -H 'Content-Type: application/json' \
    -d '{
    "langCode": "en",
    "subject": "Hello from Mayleo!",
    "message": "This is a test email.",
    "toEmail": "your_to_email@example.com",
    "imageSource": "DEFAULT",
    "imagePath": "postcards/postcard-0.jpg"
  }'
```
### ✅ Response (Success)

```json
{
  "id": "8537cc8d-bc1b-437f-a393-0614b7c52d40"
}
```

### ❌ Response (Error example)

```json
{
  "error": "RPM_LIMIT_EXCEEDED",
  "message": "Request per minute limit exceeded"
}
```

### 🔑 Security Checklist Summary

Before moving to production, verify:

- [ ] **AES Key Length**: `MAYLEO_KEY_SMTP` and `MAYLEO_KEY_HMAC` must be exactly 32 characters (256 bits).
- [ ] **Hashing**: API Keys are SHA-256 + Salted.
- [ ] **Encryption**: All at-rest credentials use AES-256.
- [ ] **Whitelisting**: Origin header strictly matches `api_client_domain`.
- [ ] **Status**: The `enabled` flag in `api_client` is set to `TRUE`.

## 🗺️ Roadmap & Future Evolutions

The project is actively maintained. Upcoming milestones include:

- **Isolated Asset Storage**: Implementation of per-client `storage_config` (S3/Azure/Local).
- **Asset Management API**: Endpoints for clients to upload/manage images directly.
- **Webhook Notifications**: Real-time callbacks for `SENT` or `FAILED` status updates.
- **Delivery Analytics**: Track delivery success rates and SMTP performance per client.
- **Observability**: Native integration with **Prometheus & Grafana** for real-time traffic monitoring.
- **Circuit Breaker**: Automatic suspension of traffic to failing SMTP providers (via Resilience4j).
- **Advanced Templating**: Support for **Thymeleaf** or **Mustache** for complex dynamic content generation.

## 📜 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.