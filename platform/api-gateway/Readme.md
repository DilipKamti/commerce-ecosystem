# API Gateway

Single entry point for all client traffic in the Commerce Ecosystem. Handles JWT validation, rate limiting, and routing to downstream services.

---

## Overview

| Property | Value |
|---|---|
| Port | 8080 |
| Spring Application Name | api-gateway |
| Database | None — stateless |
| Config Source | config-server → configs/api-gateway.yml |
| Technology | Spring Cloud Gateway (Reactive/WebFlux) |

---

## What It Does

- **Single entry point** — all client requests go through port 8080
- **JWT validation** — validates every non-public request before forwarding
- **Header injection** — injects `X-User-Id` and `X-User-Roles` for downstream services
- **Rate limiting** — Redis-backed per-user/per-IP request limiting
- **Routing** — forwards requests to correct service via Eureka load balancing
- **Centralized 404** — returns consistent error for unknown routes

---

## Important: Reactive Architecture

API Gateway uses **Spring WebFlux** (reactive, non-blocking), NOT Spring MVC. This means:
- No `@Controller` — use `GlobalFilter` instead
- No `HttpServletRequest` — use `ServerWebExchange`
- All operations return `Mono<>` or `Flux<>`
- Security uses `@EnableWebFluxSecurity` not `@EnableWebSecurity`

---

## Package Structure

```
io.commerce.apigateway/
├── config/
│   ├── SecurityConfig.java      → WebFlux security (permits all — JWT filter handles auth)
│   ├── RedisConfig.java         → Redis template configuration (if needed)
│   └── GatewayErrorConfig.java  → Custom error response format
├── filter/
│   ├── JwtAuthFilter.java       → JWT validation + header injection (order = -1)
│   └── RateLimitFilter.java     → Redis-backed rate limiting (order = 0)
└── security/
    └── JwtService.java          → JWT parsing and validation
```

---

## Request Flow

```
Client Request
      ↓
JwtAuthFilter (order = -1) — runs FIRST
  ├── Is public path? → Skip JWT check, forward request
  ├── No Authorization header? → Return 401
  ├── Invalid JWT? → Return 401
  └── Valid JWT → Extract userId + roles → Inject headers → Continue
      ↓
RateLimitFilter (order = 0) — runs SECOND
  ├── Get X-User-Id header (set by JwtAuthFilter)
  ├── Increment Redis counter for user
  ├── Count > 100/minute? → Return 429
  └── Count OK → Add rate limit headers → Continue
      ↓
Spring Cloud Gateway Router
  ├── /api/v1/auth/**       → user-service (lb://user-service)
  ├── /api/v1/users/**      → user-service (lb://user-service)
  ├── /api/v1/products/**   → product-service (lb://product-service)
  ├── /api/v1/categories/** → product-service (lb://product-service)
  └── /api/v1/orders/**     → order-service (lb://order-service)
      ↓
Downstream Service
```

---

## JWT Auth Filter

### Public Paths (No JWT Required)
```java
private static final List<String> PUBLIC_PATHS = List.of(
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/forgot-password",
    "/api/v1/auth/verify-otp",
    "/api/v1/auth/reset-password",
    "/actuator/health"
);
```

### What It Does With Valid JWT
Extracts claims and injects headers:
```
X-User-Id    → UUID of authenticated user
X-User-Roles → ["ROLE_CUSTOMER"] or ["ROLE_ADMIN"]
```

Downstream services read these headers — they never validate JWT themselves.

### What It Returns on Failure
```
401 Unauthorized → Missing JWT, invalid JWT, expired JWT
```

---

## Rate Limiting

Uses Redis to track request counts per user per minute.

```
Key format:   rate_limit:user:{userId}     (for authenticated users)
              rate_limit:ip:{ipAddress}    (for public endpoints)

Limit:        100 requests per minute
Window:       1 minute (sliding, resets after 60 seconds)
```

### Response Headers Added to Every Request
```
X-RateLimit-Limit:     100
X-RateLimit-Remaining: 99   (decrements with each request)
```

### When Rate Limit Exceeded
```
429 Too Many Requests
X-RateLimit-Limit:     100
X-RateLimit-Remaining: 0
```

---

## Route Configuration (from api-gateway.yml in config-server)

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service-auth
          uri: lb://user-service         # lb:// = load balanced via Eureka
          predicates:
            - Path=/api/v1/auth/**

        - id: user-service-users
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**

        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/v1/products/**, /api/v1/categories/**

        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/v1/orders/**
```

`lb://service-name` means Gateway asks Eureka for the service address instead of using a hardcoded URL. This enables load balancing when multiple instances are running.

---

## Configuration (from config-server)

```yaml
server:
  port: 8080

spring:
  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: your-super-secret-key     # Must match user-service JWT secret

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

---

## Redis Dependency

API Gateway requires Redis for rate limiting. Ensure Redis is running:

```bash
# Check Redis is running
docker ps | grep commerce-redis

# Start Redis if needed
docker run -d --name commerce-redis -p 6379:6379 redis:7-alpine
```

If Redis is down, the `RateLimitFilter` will throw an exception and all requests will fail. Ensure Redis starts before the Gateway.

---

## Security Config Note

`SecurityConfig.java` sets all routes to `permitAll()`:

```java
.authorizeExchange(exchanges -> exchanges
    .anyExchange().permitAll()  // JWT filter handles everything
);
```

This is intentional — Spring Security's built-in auth is disabled because `JwtAuthFilter` handles all authentication. Having both would cause conflicts.

---

## Testing via Gateway

All requests to downstream services should go through port 8080:

```bash
# Register (no JWT needed)
POST http://localhost:8080/api/v1/auth/register

# Login (no JWT needed) — get token
POST http://localhost:8080/api/v1/auth/login

# Protected endpoint — needs JWT
GET http://localhost:8080/api/v1/products
Authorization: Bearer eyJhbGci...

# Create order — needs JWT
POST http://localhost:8080/api/v1/orders
Authorization: Bearer eyJhbGci...
```

---

## Debugging

### 401 Unauthorized on every request
- JWT token expired — login again to get new token
- Wrong JWT secret — check jwt.secret matches between user-service and api-gateway in config-server
- Missing "Bearer " prefix in Authorization header

### 404 Route not found
- Check route is configured in `api-gateway.yml`
- Check path matches exactly — `/api/v1/products` not `/api/products`

### 503 No servers available for service: {name}
- Target service is not registered in Eureka
- Check Eureka dashboard: `http://localhost:8761`
- Restart the missing service

### 429 Too Many Requests
- Rate limit exceeded (100 requests/minute)
- Wait 1 minute for counter to reset
- Redis must be running for rate limiting to work

### Gateway starts but requests fail immediately
- Check Redis is running (required for rate limiting)
- Check config-server is running (Gateway pulls config from it)
- Check Eureka is running (Gateway needs it for routing)

### JWT secret mismatch
Both user-service and api-gateway must use the same `jwt.secret` value from config-server. If they differ, Gateway will reject all tokens as invalid.

---

## Health Check

```
GET http://localhost:8080/actuator/health
Response: { "status": "UP" }
```