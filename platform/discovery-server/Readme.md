# Discovery Server (Eureka)

Service registry for the Commerce Ecosystem. All microservices register here on startup so they can find and communicate with each other without hardcoded URLs.

---

## Overview

| Property | Value |
|---|---|
| Port | 8761 |
| Spring Application Name | discovery-server |
| Technology | Spring Cloud Netflix Eureka |
| Dependencies | Eureka Server, Config Client, Actuator |

---

## What It Does

- Acts as a **phone book** for all microservices
- Every service registers itself here with its name, host, and port
- When one service needs to call another (e.g. order-service calling product-service), it asks Eureka for the address instead of using a hardcoded URL
- Monitors service health via heartbeats — removes services that stop responding

---

## How It Works

```
Service starts
      ↓
Registers with Eureka: "I am user-service, running at 127.0.0.1:8081"
      ↓
Eureka stores this registration
      ↓
Every 30 seconds: service sends heartbeat to Eureka
      ↓
If heartbeat stops: Eureka removes the service after 90 seconds

When api-gateway needs to call user-service:
      ↓
Asks Eureka: "Where is user-service?"
      ↓
Eureka returns: "127.0.0.1:8081"
      ↓
Gateway routes request there
```

---

## Configuration

Config is pulled from config-server. Local `application.yml` only contains enough to connect to config-server:

```yaml
# Local application.yml
server:
  port: 8761

spring:
  application:
    name: discovery-server
  config:
    import: "configserver:http://localhost:8888"

eureka:
  instance:
    hostname: localhost
    prefer-ip-address: false
    instance-id: ${spring.application.name}:${server.port}
  client:
    register-with-eureka: false    # Don't register itself
    fetch-registry: false          # Don't fetch registry from itself
```

---

## Eureka Dashboard

Open in browser after starting:
```
http://localhost:8761
```

You should see all registered services listed with their status.

### What a Healthy Dashboard Looks Like

```
Application       Status
API-GATEWAY       UP (1) - api-gateway:8080
USER-SERVICE      UP (1) - user-service:8081
PRODUCT-SERVICE   UP (1) - product-service:8082
ORDER-SERVICE     UP (1) - order-service:8083
```

---

## How Services Register

Each service has this in its `application.yml`:

```yaml
eureka:
  instance:
    hostname: localhost
    prefer-ip-address: false
    instance-id: ${spring.application.name}:${server.port}
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

---

## Debugging

### Service not appearing in Eureka dashboard
1. Check service started successfully (no startup errors)
2. Check service's `application.yml` has correct Eureka URL
3. Wait 30 seconds — registration can take up to 30 seconds
4. Check discovery-server logs for registration messages:
```
INFO - Registered instance USER-SERVICE/user-service:8081 with status UP
```

### Service showing hostname instead of localhost (e.g. Dilip.mshome.net)
Set these in each service's local `application.yml`:
```yaml
eureka:
  instance:
    hostname: localhost
    prefer-ip-address: false
```

### api-gateway returns "No servers available for service: user-service"
- Check user-service is registered in Eureka dashboard
- Check user-service is healthy (green)
- Restart user-service if needed

### Services deregistering randomly
Eureka removes services that miss 3 consecutive heartbeats (90 seconds). Check if the service is crashing silently.

---

## Important Rules

- Discovery server must start **after config-server** but **before all other services**
- Never hardcode service URLs in code — always use service names with Eureka load balancing (`lb://service-name`)
- All services must have `spring-cloud-starter-netflix-eureka-client` dependency