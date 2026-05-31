# Commerce Ecosystem

Enterprise-grade microservices commerce platform built with Spring Boot 3.5.1 and Spring Cloud 2025.0.0.

---

## Platform Overview

A simplified Amazon/Flipkart-style commerce platform supporting the end-to-end customer journey from registration to order placement.

```
Customer Registers → Browses Products → Places Order → [Payment - Phase 2] → [Fulfillment - Phase 2]
```

---

## Repository Structure

```
commerce-ecosystem/
│
├── platform/
│   ├── config-server/        → Port 8888 — Centralized configuration
│   ├── discovery-server/     → Port 8761 — Eureka service registry
│   └── api-gateway/          → Port 8080 — Single entry point
│
├── services/
│   ├── user-service/         → Port 8081 — Auth, JWT, users
│   ├── product-service/      → Port 8082 — Product catalog
│   └── order-service/        → Port 8083 — Order management
│
├── infrastructure/
│   ├── promtail/             → Log collector config
│   └── grafana/              → Dashboard provisioning
│
├── logs/                     → Shared log output folder
├── docs/                     → Architecture documents
└── docker-compose.yml        → Infrastructure containers
```

---

## Technology Stack

| Concern | Technology | Version |
|---|---|---|
| Language | Java | 25 |
| Framework | Spring Boot | 3.5.1 |
| Service Discovery | Eureka | Spring Cloud 2025.0.0 |
| Config Management | Spring Cloud Config | Spring Cloud 2025.0.0 |
| API Gateway | Spring Cloud Gateway | Spring Cloud 2025.0.0 |
| Security | Spring Security + JWT (jjwt) | 0.12.6 |
| ORM | Spring Data JPA + Hibernate | 3.x |
| Database | MySQL | 8.x |
| Migrations | Flyway | Latest |
| HTTP Client | OpenFeign + Resilience4j | Spring Cloud 2025.0.0 |
| Caching | Redis | 7.x |
| Tracing | Zipkin | Latest |
| Log Aggregation | Grafana + Loki + Promtail | 2.9.0 |
| Build | Maven | 3.9.x |

---

## Infrastructure Services (Docker)

| Container | Image | Port | Purpose |
|---|---|---|---|
| commerce-redis | redis:7-alpine | 6379 | Rate limiting, caching |
| commerce-zipkin | openzipkin/zipkin | 9411 | Distributed tracing |
| commerce-loki | grafana/loki:2.9.0 | 3100 | Log storage |
| commerce-promtail | grafana/promtail:2.9.0 | — | Log collector |
| commerce-grafana | grafana/grafana:latest | 3000 | Log visualization |

---

## Service Startup Order

Always start services in this order:

```
1. Infrastructure (Docker) → Redis, Zipkin, Loki, Promtail, Grafana
2. config-server            → Port 8888
3. discovery-server         → Port 8761
4. user-service             → Port 8081
5. product-service          → Port 8082
6. order-service            → Port 8083
7. api-gateway              → Port 8080 (last — depends on all others)
```

---

## Architecture Decisions

| Decision | Choice | Reason |
|---|---|---|
| Database per service | Separate MySQL databases | No cross-service data coupling |
| JWT validation | API Gateway only | Single auth enforcement point |
| Config management | Config Server (native profile) | Centralized, environment-specific |
| DB migrations | Flyway | Version-controlled schema changes |
| Deletes | Soft deletes only | Never lose data |
| Primary keys | UUID | No ID enumeration attacks |
| Logging | Logback + Loki | Centralized, searchable logs |
| Tracing | Zipkin | Cross-service request tracing |

---

## Quick Start

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Start platform services (in order)
cd platform/config-server && mvn spring-boot:run
cd platform/discovery-server && mvn spring-boot:run

# 3. Start business services
cd services/user-service && mvn spring-boot:run
cd services/product-service && mvn spring-boot:run
cd services/order-service && mvn spring-boot:run

# 4. Start gateway last
cd platform/api-gateway && mvn spring-boot:run
```

---

## Observability URLs

| Tool | URL | Credentials |
|---|---|---|
| Eureka Dashboard | http://localhost:8761 | — |
| Zipkin UI | http://localhost:9411 | — |
| Grafana | http://localhost:3000 | admin / admin |
| Config Server | http://localhost:8888/{service}/default | — |

---

## Phase Roadmap

| Phase | Services | Status |
|---|---|---|
| Phase 1 | user, product, order, gateway, config, discovery | ✅ Complete |
| Phase 2 | payment, inventory, shipping, notification | 🔜 Pending |
| Phase 3 | analytics, reporting | 🔜 Pending |