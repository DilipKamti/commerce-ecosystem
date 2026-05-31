# Order Service

Manages the order lifecycle for the Commerce Ecosystem. Validates products via Feign call to product-service, extracts user context from injected headers, and persists order state.

---

## Overview

| Property | Value |
|---|---|
| Port | 8083 |
| Spring Application Name | order-service |
| Database | commerce_db_order (MySQL) |
| Config Source | config-server → configs/order-service.yml |
| External Calls | product-service (via Feign + Resilience4j) |

---

## What It Does

- Create orders with multiple items
- Validate each product exists via Feign call to product-service
- Calculate total order amount
- List a user's orders (paginated)
- Cancel orders (only if PENDING status)
- Circuit breaker protection — graceful fallback if product-service is down

---

## Package Structure

```
io.commerce.orderservice/
├── client/
│   ├── ProductClient.java          → Feign client interface for product-service
│   └── ProductClientFallback.java  → Fallback when product-service is down
├── config/
│   └── JpaConfig.java              → Enables JPA auditing
├── controller/
│   └── OrderController.java        → Order endpoints
├── dto/
│   ├── request/
│   │   ├── CreateOrderRequest.java
│   │   └── OrderItemRequest.java
│   └── response/
│       ├── OrderResponse.java
│       ├── OrderItemResponse.java
│       └── ProductApiResponse.java  → Wrapper for product-service response envelope
│       └── ProductResponse.java     → Matches product-service ProductResponse DTO
├── entity/
│   ├── Order.java
│   ├── OrderItem.java
│   └── OrderStatus.java            → PENDING, CONFIRMED, CANCELLED, PAID
├── exception/
│   ├── ResourceNotFoundException.java
│   ├── BadRequestException.java
│   ├── ServiceUnavailableException.java
│   └── GlobalExceptionHandler.java
├── repository/
│   └── OrderRepository.java
└── service/
    └── OrderService.java
```

---

## Database Schema

Database: `commerce_db_order`

```
orders
├── id           VARCHAR(36) PK
├── user_id      VARCHAR(36)         → from JWT via X-User-Id header
├── status       VARCHAR(20)         → PENDING, CONFIRMED, CANCELLED, PAID
├── total_amount DECIMAL(10,2)
├── active       BOOLEAN DEFAULT TRUE
├── created_at   DATETIME
└── updated_at   DATETIME

order_items
├── id           VARCHAR(36) PK
├── order_id     VARCHAR(36) FK → orders.id
├── product_id   VARCHAR(36)         → snapshot at time of order
├── product_name VARCHAR(255)        → snapshot at time of order
├── unit_price   DECIMAL(10,2)       → snapshot at time of order
└── quantity     INT
```

### Why Snapshot Product Data?
`product_name` and `unit_price` are copied from product-service at the time of order creation. This ensures historical orders always show the correct price and name even if the product is later updated or deleted.

### Flyway Migrations
```
V1__init_order_schema.sql   → Creates orders, order_items tables
```

---

## API Endpoints

All endpoints require `X-User-Id` header (injected by API Gateway after JWT validation).

| Method | Endpoint | Description |
|---|---|---|
| POST | /api/v1/orders | Create new order |
| GET | /api/v1/orders | List my orders (paginated) |
| GET | /api/v1/orders/{id} | Get order by ID |
| PUT | /api/v1/orders/{id}/cancel | Cancel order |

---

## Request / Response Examples

### Create Order
```json
POST /api/v1/orders
X-User-Id: user-uuid-here

{
  "items": [
    { "productId": "product-uuid", "quantity": 2 },
    { "productId": "another-uuid", "quantity": 1 }
  ]
}

Response 201:
{
  "success": true,
  "data": {
    "id": "order-uuid",
    "userId": "user-uuid",
    "status": "PENDING",
    "totalAmount": 2999.97,
    "items": [
      {
        "id": "item-uuid",
        "productId": "product-uuid",
        "productName": "iPhone 15",
        "unitPrice": 999.99,
        "quantity": 2,
        "subtotal": 1999.98
      }
    ],
    "createdAt": "2026-05-30T10:00:00"
  }
}
```

### Get My Orders
```
GET /api/v1/orders?page=0&size=10
X-User-Id: user-uuid-here
```

### Cancel Order
```
PUT /api/v1/orders/{id}/cancel
X-User-Id: user-uuid-here

Response: { "success": true, "data": { "status": "CANCELLED", ... } }
```

---

## Order Status Lifecycle

```
    Create Order
         ↓
      PENDING          ← Can be cancelled here
         ↓
    CONFIRMED          ← Phase 2: after payment initiated
         ↓
       PAID            ← Phase 2: after payment completed
```

Rules:
- Only **PENDING** orders can be cancelled
- Cancelling a non-PENDING order returns `400 Bad Request`

---

## How It Communicates with Product-Service

order-service uses **OpenFeign** with **Resilience4j** to call product-service:

```java
@FeignClient(name = "product-service", fallback = ProductClientFallback.class)
public interface ProductClient {
    @GetMapping("/api/v1/products/{id}")
    ProductApiResponse getProductById(@PathVariable UUID id);
}
```

### Why ProductApiResponse Wrapper?

product-service returns a response envelope:
```json
{ "success": true, "data": { "id": "...", "name": "...", "price": 999.99 } }
```

So order-service uses `ProductApiResponse` to unwrap the `data` field:
```java
public class ProductApiResponse {
    private boolean success;
    private ProductResponse data;   // actual product is nested here
}
```

---

## Resilience4j Configuration

Configured in `order-service.yml` via config-server:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      product-service:
        slidingWindowSize: 10               # Watch last 10 calls
        failureRateThreshold: 50            # Open circuit if 50%+ fail
        waitDurationInOpenState: 5s         # Wait 5s before trying again
        permittedNumberOfCallsInHalfOpenState: 3
  retry:
    instances:
      product-service:
        maxAttempts: 3                      # Retry up to 3 times
        waitDuration: 500ms                 # Wait 500ms between retries
  timelimiter:
    instances:
      product-service:
        timeoutDuration: 3s                 # Timeout after 3 seconds
```

### Circuit Breaker States

```
CLOSED (normal)
  → Calls go through to product-service
  → If 50%+ of last 10 calls fail → OPEN

OPEN (product-service is down)
  → Calls immediately return fallback (null)
  → After 5 seconds → HALF-OPEN

HALF-OPEN (testing recovery)
  → 3 test calls allowed through
  → If they succeed → CLOSED
  → If they fail → OPEN again
```

### Fallback Behavior

When product-service is down:
```java
// ProductClientFallback.java
public ProductApiResponse getProductById(UUID id) {
    return null;  // Returns null
}

// OrderService.java handles null:
if (apiResponse == null || apiResponse.getData() == null) {
    throw new ServiceUnavailableException(
        "Product not found or service unavailable: " + id);
}
```
Client receives `503 Service Unavailable`.

---

## User Identity — How It Works

order-service does NOT validate JWT. Instead it reads the `X-User-Id` header:

```java
@PostMapping
public ResponseEntity<?> createOrder(
    @RequestHeader("X-User-Id") UUID userId,  // Injected by Gateway
    @RequestBody CreateOrderRequest request) {
```

The API Gateway validates the JWT and injects `X-User-Id` before forwarding the request. order-service trusts this header completely.

For local testing (bypassing Gateway), pass the header manually in Postman:
```
X-User-Id: any-valid-uuid
```

---

## Configuration (from config-server)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/commerce_db_order
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

resilience4j:
  circuitbreaker:
    instances:
      product-service:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 5s
```

---

## Debugging

### "Product not found or service unavailable"
- Check product-service is running
- Check product ID is valid: `GET http://localhost:8082/api/v1/products/{id}`
- Check Eureka — is product-service registered?
- Check circuit breaker state — if open, wait 5 seconds

### "Only PENDING orders can be cancelled"
- Order status is not PENDING
- Check current status: `GET /api/v1/orders/{id}`

### "Order not found"
- Order doesn't exist OR belongs to a different user
- order-service returns 404 for both cases (security — don't reveal other users' orders)

### Product price wrong in order
- Price is snapshotted at order creation time
- If product price changed after order was placed, historical orders keep the original price

### X-User-Id header missing
- When testing directly (not via gateway): manually add `X-User-Id` header in Postman
- When testing via gateway: ensure JWT token is valid and not expired

### Feign call failing with timeout
- product-service is slow or down
- Check product-service logs
- Increase timeout in config: `timelimiter.instances.product-service.timeoutDuration: 5s`

---

## Health Check

```
GET http://localhost:8083/actuator/health
Response: { "status": "UP" }
```