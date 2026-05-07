# Commerce Ecosystem

Enterprise-grade microservices commerce platform.

## Phase 1 Services
| Service | Port |
|---|---|
| api-gateway | 8080 |
| user-service | 8081 |
| product-service | 8082 |
| order-service | 8083 |
| config-server | 8888 |
| discovery-server | 8761 |

## Quick Start
1. Start infra: `docker-compose up -d`
2. Start config-server first
3. Start discovery-server
4. Start remaining services in any order