# Product Service

Manages the product catalog including categories, pricing, and product metadata for the Commerce Ecosystem. A read-heavy service with no authentication of its own.

---

## Overview

| Property | Value |
|---|---|
| Port | 8082 |
| Spring Application Name | product-service |
| Database | commerce_db_product (MySQL) |
| Config Source | config-server → configs/product-service.yml |

---

## What It Does

- CRUD operations for products
- Category management (with parent-child hierarchy)
- Paginated and searchable product listing
- Soft delete for products (sets `active = false`)
- Exposes product data to order-service via Feign

---

## Package Structure

```
io.commerce.productservice/
├── config/
│   └── JpaConfig.java           → Enables JPA auditing
├── controller/
│   ├── ProductController.java   → Product CRUD endpoints
│   └── CategoryController.java  → Category endpoints
├── dto/
│   ├── request/
│   │   ├── CreateProductRequest.java
│   │   ├── UpdateProductRequest.java
│   │   └── CreateCategoryRequest.java
│   └── response/
│       ├── ProductResponse.java
│       └── CategoryResponse.java
├── entity/
│   ├── Product.java
│   └── Category.java
├── exception/
│   ├── ResourceNotFoundException.java
│   ├── ConflictException.java
│   └── GlobalExceptionHandler.java
├── repository/
│   ├── ProductRepository.java
│   └── CategoryRepository.java
└── service/
    ├── ProductService.java
    └── CategoryService.java
```

---

## Database Schema

Database: `commerce_db_product`

```
categories
├── id                 VARCHAR(36) PK
├── name               VARCHAR(100) UNIQUE
├── parent_category_id VARCHAR(36) NULL   → self-reference for hierarchy
├── active             BOOLEAN DEFAULT TRUE
├── created_at         DATETIME
└── updated_at         DATETIME

products
├── id          VARCHAR(36) PK
├── name        VARCHAR(255)
├── description TEXT
├── price       DECIMAL(10,2)
├── sku         VARCHAR(100) UNIQUE
├── category_id VARCHAR(36) FK → categories.id
├── active      BOOLEAN DEFAULT TRUE
├── created_at  DATETIME
└── updated_at  DATETIME
```

### Flyway Migrations
```
V1__init_product_schema.sql  → Creates categories, products + seeds Electronics, Clothing, Books
```

### Seeded Categories
On fresh startup these categories are automatically created:
- Electronics
- Clothing
- Books

---

## API Endpoints

### Products — `/api/v1/products`

| Method | Endpoint | Auth Required | Description |
|---|---|---|---|
| GET | /api/v1/products | Yes (via Gateway) | List all products (paginated, searchable) |
| GET | /api/v1/products/{id} | Yes (via Gateway) | Get product by ID |
| POST | /api/v1/products | Yes — ADMIN only | Create product |
| PUT | /api/v1/products/{id} | Yes — ADMIN only | Update product |
| DELETE | /api/v1/products/{id} | Yes — ADMIN only | Soft delete product |

### Categories — `/api/v1/categories`

| Method | Endpoint | Auth Required | Description |
|---|---|---|---|
| GET | /api/v1/categories | Yes (via Gateway) | List all active categories |
| POST | /api/v1/categories | Yes — ADMIN only | Create category |

---

## Request / Response Examples

### Get All Products (Paginated)
```
GET /api/v1/products?page=0&size=10&sort=createdAt,desc

Response 200:
{
  "success": true,
  "data": [...],
  "meta": {
    "page": 0,
    "size": 10,
    "totalElements": 45,
    "totalPages": 5
  }
}
```

### Search Products
```
GET /api/v1/products?search=iPhone&page=0&size=10
```

### Create Product
```json
POST /api/v1/products
{
  "name": "iPhone 15",
  "description": "Latest Apple smartphone",
  "price": 999.99,
  "sku": "APPL-IPH15-001",
  "categoryId": "uuid-of-electronics-category"
}

Response 201:
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "iPhone 15",
    "price": 999.99,
    "sku": "APPL-IPH15-001",
    "category": { "id": "uuid", "name": "Electronics" },
    "createdAt": "2026-05-30T10:00:00"
  }
}
```

### Update Product
```json
PUT /api/v1/products/{id}
{
  "name": "iPhone 15 Pro",
  "price": 1099.99
}
```

### Soft Delete Product
```
DELETE /api/v1/products/{id}

Response 200:
{ "success": true, "message": "Product deleted" }
```
Note: Product is NOT removed from DB. `active` is set to `false`. Deleted products won't appear in GET requests.

---

## Pagination & Sorting

All list endpoints support:

| Query Param | Default | Example |
|---|---|---|
| page | 0 | ?page=2 |
| size | 20 | ?size=10 |
| sort | createdAt,desc | ?sort=price,asc |

---

## How order-service Uses This Service

order-service calls product-service via Feign client to validate products when creating orders:

```java
// In order-service
@FeignClient(name = "product-service")
public interface ProductClient {
    @GetMapping("/api/v1/products/{id}")
    ProductApiResponse getProductById(@PathVariable UUID id);
}
```

The call goes through Eureka load balancing — order-service doesn't hardcode the URL.

If product-service is down, Resilience4j circuit breaker in order-service returns a fallback response instead of failing.

---

## Configuration (from config-server)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/commerce_db_product
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
```

---

## Security Note

Product-service has **no security of its own**. It trusts that:
- All requests coming through API Gateway have already been JWT-validated
- The `X-User-Roles` header injected by the Gateway contains the user's roles
- Role-based access control (ADMIN only for write operations) is enforced at the Gateway layer

When calling product-service directly (bypassing Gateway) during development, there is no auth check. This is intentional for local testing.

---

## Debugging

### "Product not found: {id}"
- Check product exists in DB: `SELECT * FROM products WHERE id = 'uuid'`
- Check product is active: `SELECT * FROM products WHERE id = 'uuid' AND active = true`

### "SKU already exists"
- SKU must be unique across all products
- Check: `SELECT * FROM products WHERE sku = 'your-sku'`

### "Category not found"
- Check category exists and is active
- Use `GET /api/v1/categories` to see available categories

### Search not returning expected results
- Search is case-insensitive but requires partial match
- `search=iphone` will match "iPhone 15", "iPhone 14 Pro" etc.
- Check the JPQL query in `ProductRepository.searchProducts`

### Flyway migration failed
```sql
USE commerce_db_product;
SELECT * FROM flyway_schema_history;
DELETE FROM flyway_schema_history WHERE success = 0;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS categories;
```
Then restart product-service.

---

## Health Check

```
GET http://localhost:8082/actuator/health
Response: { "status": "UP" }
```