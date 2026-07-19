# Secure Ticketing & Reservation API

Event ticketing and reservation platform built with **Java 21**, **Spring Boot 4**, **JWT authentication**, and **Redis** rate limiting. The system consists of 4 microservices following a BFF (Backend for Frontend) architecture.

## Architecture

```
                    +-------------------+
                    |   API Gateway     |
                    |   (port 8080)     |
                    | JWT validation    |
                    | AuthZ (coarse)    |
                    | Rate limiting     |
                    +--------+----------+
                             |
              +--------------+--------------+
              |                             |
    +---------v---------+       +-----------v-----------+
    |   Auth Service    |       |  Ticketing Service    |
    |   (port 8082)     |       |  (port 8081)          |
    | Register/Login    |       | Event CRUD            |
    | JWT generation    |       | Reservations          |
    | Refresh tokens    |       | Idempotency           |
    +---------+---------+       +-----------+-----------+
              |                             |
              +------------+----------------+
                           |
                 +---------v---------+
                 | Common Library    |
                 | (shared JAR)     |
                 | ApiResponse, JWT |
                 | Exceptions, Util |
                 +------------------+
```

| Service | Port | Responsibility |
|---|---|---|
| **ticketing-common-library** | - | Shared JAR: ApiResponse, exceptions, JwtService, utilities |
| **ticketing-auth** | 8082 | User registration, login, logout, refresh token rotation |
| **ticketing-apigateway** | 8080 | JWT validation, coarse-grained authorization, rate limiting, routing |
| **ticketing-service** | 8081 | Event CRUD, reservation lifecycle, idempotency, audit logging |

## Prerequisites

- Java 21+
- Maven 3.9+
- Redis 7+ (`brew install redis` on macOS)

## Quick Start

```bash
# 1. Build Common Library
cd ticketing-common-library
mvn clean install

# 2. Start Redis
redis-server

# 3. Start Auth Service (new terminal)
cd ticketing-auth
mvn spring-boot:run
# Port 8082, seed users created automatically

# 4. Start Ticketing Service (new terminal)
cd ticketing-service
mvn spring-boot:run
# Port 8081

# 5. Start API Gateway (new terminal)
cd ticketing-apigateway
mvn spring-boot:run
# Port 8080

# 6. Test - Login
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"organizer@ticketing.com","password":"ChangeMe123!"}' | jq
```

## Auth Flow

### 1. Register

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "newuser@example.com",
    "password": "MySecure8!",
    "role": "CUSTOMER"
  }'
```

Response:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
    "tokenType": "Bearer",
    "expiresInMinutes": 30
  }
}
```

### 2. Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"organizer@ticketing.com","password":"ChangeMe123!"}'
```

### 3. Authenticated Request

Use the access token in the `Authorization` header. The Gateway validates the JWT, checks role-based authorization, and forwards user identity headers (`X-User-Id`, `X-User-Email`, `X-User-Roles`) to downstream services.

```bash
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{"name":"Concert","totalSeats":100}'
```

### 4. Refresh Token

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"550e8400-e29b-41d4-a716-446655440000"}'
```

Refresh token rotation: old token is invalidated, new access + refresh tokens are issued.

### 5. Logout

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"550e8400-e29b-41d4-a716-446655440000"}'
```

## Security Architecture

JWT is a thin pointer to a Redis session. User context (roles, email) lives in Redis, not in the JWT.

```
Client                Auth (8082)              Redis                  Gateway (8080)
  |                      |                       |                        |
  |-- POST /login ------>|                       |                        |
  |                      |-- store session ----->|                        |
  |                      |   {userId,email,roles}|                        |
  |<-- JWT (sid=uuid) ---|   TTL=30min           |                        |
  |                      |                       |                        |
  |-- GET /api/events ---|---------------------->|                        |
  |   Authorization:     |                       |<-- lookup sid ---------|
  |   Bearer <jwt>       |                       |-- {userId,email,roles}>|
  |                      |                       |   => authorize + forward
```

| Concern | Service | How |
|---|---|---|
| **Token generation** | Auth Service | `JwtService.generateToken(sessionId, email)` — JWT contains only `sid` + `sub` |
| **Session storage** | Auth Service | `UserSessionService` stores `{userId, email, roles}` in Redis Hash with TTL |
| **Token validation** | API Gateway | `JwtAuthGatewayFilter` validates JWT signature, then looks up `sid` in Redis |
| **Session revocation** | Auth Service | On logout/refresh, deletes Redis key → JWT becomes immediately invalid |
| **Password hashing** | Auth Service | BCrypt via `PasswordEncoder` bean |
| **Coarse-grained AuthZ** | API Gateway | Route + method + roles (from Redis) matching |

**Redis key format**: `user-session:{uuid}` → Hash with fields `userId`, `email`, `roles`

**Token TTL**: Access token 30 minutes (Redis TTL matches), refresh token 7 days (H2 database)

## Sample JWT

JWT payload is minimal — only session pointer + email:

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "organizer@ticketing.com",
    "sid": "550e8400-e29b-41d4-a716-446655440000",
    "iat": 1721300000,
    "exp": 1721301800
  }
}
```

The corresponding Redis Hash (`user-session:550e8400-...`):
```
userId: "2"
email:  "organizer@ticketing.com"
roles:  "ORGANIZER"
```

## Seed Users

Created automatically on first startup by `SeedDataConfig`:

| Email | Role | Password |
|---|---|---|
| admin@ticketing.com | ADMIN | ChangeMe123! |
| organizer@ticketing.com | ORGANIZER | ChangeMe123! |
| customer@ticketing.com | CUSTOMER | ChangeMe123! |

## API Endpoints

### Auth Service (`/api/auth`)

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/api/auth/register` | Register new user | Public |
| POST | `/api/auth/login` | Login, get JWT | Public |
| POST | `/api/auth/refresh` | Refresh access token | Public |
| POST | `/api/auth/logout` | Invalidate refresh token | Public |

### Gateway Authorization Rules

| Route Pattern | Method | Allowed Roles |
|---|---|---|
| `/api/auth/**` | ANY | Public (no JWT) |
| `/api/events/public/**` | GET | Public (no JWT) |
| `/api/events/**` | POST | ORGANIZER, ADMIN |
| `/api/events/**` | PUT | ORGANIZER, ADMIN |
| `/api/events` | GET | Any authenticated |
| `/api/events/*/reservations` | POST | CUSTOMER |
| `/api/reservations/*/confirm` | POST | CUSTOMER |
| `/api/reservations/*/cancel` | POST | CUSTOMER |

## Architectural Decisions (ADR)

**ADR-1: 4 Separate Projects**
- CommonLibrary prevents code duplication (shared ApiResponse, exceptions, JWT).
- Auth service isolates user management (Single Responsibility).
- API Gateway centralizes cross-cutting concerns (auth, authZ, rate limiting).
- Ticketing Service contains only business logic, no auth handling.

**ADR-2: JWT in CommonLibrary**
- Auth generates tokens, Gateway validates them using the same `JwtService` class.
- JJWT dependency is managed in one place, shared transitively.

**ADR-3: Coarse-grained AuthZ at Gateway, Fine-grained in Ticketing**
- Gateway enforces which roles can access which endpoints (route-based).
- Ticketing Service enforces ownership checks (is this my event?).

**ADR-4: Redis-backed JWT Sessions**
- JWT is a thin token containing only a session ID (`sid`) and email (`sub`).
- User roles and identity are stored in Redis (`user-session:{sid}` Hash), not in the JWT.
- Gateway reads from Redis on every request to get userId, email, roles.
- Enables instant token revocation: deleting the Redis key invalidates the JWT immediately.
- On login, old sessions are invalidated. On refresh, old session is deleted and a new one created.
- BCrypt `PasswordEncoder` bean for secure password hashing in Auth service.

**ADR-5: Redis Idempotency**
- Redis is already in the system for rate limiting.
- Native TTL, atomic operations, restart-safe.
- Redis Hash preferred over JPA entity.

**ADR-6: Optimistic Locking for Oversell Protection**
- `@Version` annotation with `OptimisticLockingFailureException` + retry.
- Better performance than pessimistic locks, prevents race conditions.

**ADR-7: Plain HTTP Headers (Gateway -> Downstream)**
- JJWT dependency not needed in Ticketing Service.
- Trusted network assumption within the cluster.
- `GatewayHeaders` constants class standardizes header names.

## OpenAPI

```
Swagger UI:    http://localhost:8082/swagger-ui.html  (Auth Service)
OpenAPI JSON:  http://localhost:8082/v3/api-docs      (Auth Service)

Swagger UI:    http://localhost:8081/swagger-ui.html  (Ticketing Service)
OpenAPI JSON:  http://localhost:8081/v3/api-docs      (Ticketing Service)
```

All API requests should go through the Gateway at port 8080. Swagger UI accesses services directly.

## Running Tests

```bash
# All tests
cd ticketing-common-library && mvn test
cd ticketing-auth && mvn test
cd ticketing-service && mvn test
cd ticketing-apigateway && mvn test

# Coverage report (Ticketing Service)
cd ticketing-service && mvn test
# Report: target/site/jacoco/index.html
```
