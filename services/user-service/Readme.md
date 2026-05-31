# User Service

Manages user identity, authentication, and authorization for the Commerce Ecosystem. The only service that issues JWT tokens.

---

## Overview

| Property | Value |
|---|---|
| Port | 8081 |
| Spring Application Name | user-service |
| Database | commerce_db_auth (MySQL) |
| Config Source | config-server → configs/user-service.yml |

---

## What It Does

- User registration and login
- JWT access token issuance
- Refresh token management (token rotation)
- Forgot password with OTP via email
- Password reset
- Automatic cleanup of expired OTPs

---

## Package Structure

```
io.commerce.userservice/
├── config/
│   ├── JpaConfig.java           → Enables JPA auditing (@CreatedDate, @LastModifiedDate)
│   └── SecurityConfig.java      → Spring Security config (stateless, BCrypt)
├── controller/
│   └── AuthController.java      → All auth endpoints
├── dto/
│   ├── request/
│   │   ├── RegisterRequest.java
│   │   ├── LoginRequest.java
│   │   ├── RefreshTokenRequest.java
│   │   ├── ForgotPasswordRequest.java
│   │   ├── VerifyOtpRequest.java
│   │   └── ResetPasswordRequest.java
│   └── response/
│       ├── AuthResponse.java
│       └── UserResponse.java
├── entity/
│   ├── User.java
│   ├── Role.java
│   ├── RefreshToken.java
│   └── OtpToken.java
├── exception/
│   ├── ConflictException.java
│   ├── UnauthorizedException.java
│   ├── BadRequestException.java
│   ├── ResourceNotFoundException.java
│   └── GlobalExceptionHandler.java
├── repository/
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   ├── RefreshTokenRepository.java
│   └── OtpTokenRepository.java
├── security/
│   └── JwtService.java          → JWT generation and validation
└── service/
    ├── AuthService.java          → Core auth logic
    ├── RefreshTokenService.java  → Token rotation logic
    ├── OtpService.java           → OTP generation and email sending
    ├── PasswordResetService.java → Password reset logic
    └── OtpCleanupService.java    → Scheduled OTP cleanup
```

---

## Database Schema

Database: `commerce_db_auth`

```
users
├── id            VARCHAR(36) PK
├── email         VARCHAR(255) UNIQUE
├── password_hash VARCHAR(255)
├── first_name    VARCHAR(100)
├── last_name     VARCHAR(100)
├── active        BOOLEAN DEFAULT TRUE
├── created_at    DATETIME
└── updated_at    DATETIME

roles
├── id   VARCHAR(36) PK
└── name VARCHAR(50) UNIQUE   → ROLE_CUSTOMER, ROLE_ADMIN

user_roles (join table)
├── user_id VARCHAR(36) FK → users.id
└── role_id VARCHAR(36) FK → roles.id

refresh_tokens
├── id         VARCHAR(36) PK
├── token      VARCHAR(512) UNIQUE
├── user_id    VARCHAR(36) FK → users.id (CASCADE DELETE)
├── expires_at DATETIME
└── created_at DATETIME

otp_tokens
├── id         VARCHAR(36) PK
├── email      VARCHAR(255)
├── otp        VARCHAR(6)
├── expires_at DATETIME
├── used       BOOLEAN DEFAULT FALSE
└── created_at DATETIME
```

### Flyway Migrations
```
V1__init_user_schema.sql     → Creates users, roles, user_roles + seeds ROLE_CUSTOMER, ROLE_ADMIN
V2__add_refresh_tokens.sql   → Creates refresh_tokens table
V3__add_otp_tokens.sql       → Creates otp_tokens table
```

---

## API Endpoints

All endpoints prefixed with `/api/v1/auth`

| Method | Endpoint | Auth Required | Description |
|---|---|---|---|
| POST | /register | No | Register new user |
| POST | /login | No | Login, returns JWT + refresh token |
| POST | /refresh | No | Get new access token using refresh token |
| POST | /forgot-password | No | Send OTP to email |
| POST | /verify-otp | No | Verify OTP, get reset token |
| POST | /reset-password | No | Reset password using reset token |

---

## Request / Response Examples

### Register
```json
POST /api/v1/auth/register
{
  "email": "user@example.com",
  "password": "password123",
  "firstName": "John",
  "lastName": "Doe"
}

Response 201:
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "roles": ["ROLE_CUSTOMER"]
  }
}
```

### Login
```json
POST /api/v1/auth/login
{
  "email": "user@example.com",
  "password": "password123"
}

Response 200:
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "uuid-string",
    "tokenType": "Bearer",
    "expiresIn": 3600000
  }
}
```

### Refresh Token
```json
POST /api/v1/auth/refresh
{
  "refreshToken": "uuid-string"
}

Response 200:
{
  "success": true,
  "data": {
    "accessToken": "new-eyJhbGci...",
    "refreshToken": "new-uuid-string",
    "tokenType": "Bearer",
    "expiresIn": 3600000
  }
}
```

### Forgot Password Flow
```json
// Step 1 - Send OTP
POST /api/v1/auth/forgot-password
{ "email": "user@example.com" }
Response: { "success": true, "data": "OTP sent to your email" }

// Step 2 - Verify OTP
POST /api/v1/auth/verify-otp
{ "email": "user@example.com", "otp": "123456" }
Response: { "success": true, "data": { "resetToken": "uuid" } }

// Step 3 - Reset Password
POST /api/v1/auth/reset-password
{ "resetToken": "uuid", "newPassword": "newpassword123" }
Response: { "success": true, "data": "Password reset successfully" }
```

---

## JWT Token Structure

```json
Header: { "alg": "HS256", "typ": "JWT" }

Payload:
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "roles": ["ROLE_CUSTOMER"],
  "iat": 1234567890,
  "exp": 1234571490
}
```

Token expires in **1 hour** (3600000ms). Use refresh token to get a new one without re-logging in.

---

## Refresh Token Flow

```
Login → accessToken (1 hour) + refreshToken (7 days)
                                      ↓
                    accessToken expires after 1 hour
                                      ↓
              POST /refresh with refreshToken
                                      ↓
              Old refreshToken DELETED (rotation)
                                      ↓
              New accessToken + new refreshToken returned
```

Token rotation means each refresh token can only be used once. If someone steals a refresh token and uses it after you've already used it, both tokens are invalidated.

---

## OTP Flow

```
POST /forgot-password { email }
      ↓
Check email exists in DB
      ↓
Generate 6-digit OTP
Save to otp_tokens with 10-minute expiry
      ↓
Send OTP to email via Gmail SMTP
      ↓
POST /verify-otp { email, otp }
      ↓
Validate OTP (not used, not expired)
Mark OTP as used
Save reset token to refresh_tokens with 15-minute expiry
      ↓
POST /reset-password { resetToken, newPassword }
      ↓
Validate reset token (not expired)
Update password hash (BCrypt strength 12)
Delete reset token
Delete ALL refresh tokens for user (force re-login)
```

---

## Scheduled Jobs

### OTP Cleanup
Runs every **5 minutes** automatically.
- Finds all OTP tokens where `expires_at < now`
- Deletes them from DB
- Logs count of deleted tokens

To change frequency, update `@Scheduled(fixedRate = 300000)` in `OtpCleanupService.java`.

---

## Configuration (from config-server)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/commerce_db_auth
    username: root
    password: 1234
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        type:
          preferred_uuid_jdbc_type: VARCHAR
  flyway:
    enabled: true
    locations: classpath:db/migration
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-gmail@gmail.com
    password: app-password

jwt:
  secret: your-secret-key
  expiration: 3600000
```

---

## Security

- Passwords hashed with **BCrypt strength 12** — never stored as plaintext
- JWT secret stored in config-server only — never in committed code
- All JWT validation done at **API Gateway** — user-service only issues tokens
- Refresh token rotation — each token usable only once
- Password reset invalidates all existing refresh tokens

---

## Debugging

### "Invalid credentials" on login
- Check email exists in DB
- Check password is correct
- Check user `active = true` in DB

### "Email already registered" on register
- User with that email already exists
- Check `users` table in `commerce_db_auth`

### JWT token issues
- Check JWT secret matches between user-service config and api-gateway config
- Both must use the same secret from config-server

### OTP email not received
- Check Gmail app password is correct in config
- Check spam folder
- Check `otp_tokens` table — OTP should be there with `used = false`

### Refresh token "Invalid refresh token"
- Token was already used (rotation)
- Token expired (7 days)
- Check `refresh_tokens` table

### Flyway migration failed
```sql
USE commerce_db_auth;
SELECT * FROM flyway_schema_history;
DELETE FROM flyway_schema_history WHERE success = 0;
```
Then fix the migration SQL and restart.

---

## Health Check

```
GET http://localhost:8081/actuator/health
Response: { "status": "UP" }
```