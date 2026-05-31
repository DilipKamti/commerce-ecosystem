# Config Server

Centralized configuration server for the Commerce Ecosystem. All microservices pull their configuration from this server on startup.

---

## Overview

| Property | Value |
|---|---|
| Port | 8888 |
| Spring Application Name | config-server |
| Profile | native |
| Dependencies | Spring Cloud Config Server, Actuator |

---

## What It Does

- Serves configuration files to all downstream services on startup
- Uses **native profile** — reads config files from classpath (no external Git repo needed)
- Every service calls this server before starting to get its full configuration (DB credentials, ports, JWT secrets, Flyway settings etc.)

---

## How It Works

```
Service starts
      ↓
Reads local application.yml
      ↓
Calls config-server: GET http://localhost:8888/{service-name}/default
      ↓
Config server returns full configuration as JSON
      ↓
Service merges config and continues startup
```

---

## Configuration Files Location

```
src/main/resources/
├── application.yml              → Config server's own config
└── configs/
    ├── discovery-server.yml     → Config for discovery-server
    ├── user-service.yml         → Config for user-service
    ├── product-service.yml      → Config for product-service
    ├── order-service.yml        → Config for order-service
    └── api-gateway.yml          → Config for api-gateway
```

---

## application.yml Explained

```yaml
server:
  port: 8888                          # Config server runs on this port

spring:
  application:
    name: config-server

  profiles:
    active: native                    # Use local classpath files (not Git)

  cloud:
    config:
      server:
        native:
          search-locations: classpath:/configs   # Where to find service configs

management:
  endpoints:
    web:
      exposure:
        include: health,info,env      # Expose these actuator endpoints
```

---

## How Services Connect to Config Server

Each service has this in its local `application.yml`:

```yaml
spring:
  config:
    import: "configserver:http://localhost:8888"  # Pull config from here
  cloud:
    config:
      fail-fast: true                             # Fail if config server unreachable
```

---

## What Each Service Config Contains

### user-service.yml
- Server port (8081)
- MySQL datasource URL, username, password
- JPA/Hibernate settings
- Flyway migration settings
- JWT secret and expiration
- Mail (SMTP) settings for OTP emails
- Zipkin tracing settings

### product-service.yml
- Server port (8082)
- MySQL datasource URL, username, password
- JPA/Hibernate settings
- Flyway migration settings
- Zipkin tracing settings

### order-service.yml
- Server port (8083)
- MySQL datasource URL, username, password
- JPA/Hibernate settings
- Flyway migration settings
- Resilience4j circuit breaker, retry, timeout settings
- Zipkin tracing settings

### api-gateway.yml
- Server port (8080)
- Route definitions (which path goes to which service)
- Redis connection settings
- JWT secret
- Zipkin tracing settings

---

## Verify Config Server is Working

```bash
# Check config server health
GET http://localhost:8888/actuator/health

# Get user-service config
GET http://localhost:8888/user-service/default

# Get product-service config
GET http://localhost:8888/product-service/default

# Get order-service config
GET http://localhost:8888/order-service/default

# Get api-gateway config
GET http://localhost:8888/api-gateway/default
```

Each response returns a JSON object containing all config properties for that service.

---

## Debugging

### Service fails to start with "Could not resolve placeholder"
Config server is not serving that property. Check the relevant yml file in `configs/`.

### Service fails with "Connection refused" on startup
Config server is not running. Start config-server first before any other service.

### Config change not reflected after restart
Config server serves files from classpath. After changing a config file, restart config-server first, then restart the affected service.

### Check what config a service is receiving
```
GET http://localhost:8888/{service-name}/default
```
This shows exactly what the service will receive.

---

## Important Rules

- **Never put secrets** (passwords, JWT secret) directly in config files committed to Git
- **Always restart config-server first** when config files change
- **Never modify** a service's port or DB settings anywhere other than here
- Config server must be the **first service** to start — everything else depends on it