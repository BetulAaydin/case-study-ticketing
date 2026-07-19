# Ticketing Auth Service

User authentication microservice. Handles registration, login, token refresh, and logout.

## Port

`8082`

## Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Register a new user (default role: CUSTOMER) |
| POST | `/api/auth/login` | Login with email + password, returns JWT |
| POST | `/api/auth/refresh` | Exchange refresh token for new access + refresh tokens |
| POST | `/api/auth/logout` | Invalidate refresh token |

All responses wrapped in `ApiResponse<T>`.

## Security

This service **generates** JWT tokens and stores user sessions in **Redis**. JWT validation is handled by the API Gateway.

- Auth endpoints (`/api/auth/**`) are `permitAll()`
- All other requests are `denyAll()` (this service only issues tokens)
- BCrypt password hashing via `PasswordEncoder` bean
- Stateless sessions, CSRF disabled

### Redis Session Flow

On login/register:
1. User credentials verified (BCrypt)
2. `UserSessionService` stores `{userId, email, roles}` in Redis Hash (`user-session:{uuid}`, TTL = 30min)
3. JWT generated with `sid` claim pointing to Redis session
4. Refresh token stored in H2 with `sessionId` reference

On logout: Redis session + refresh token deleted (instant revocation)
On refresh: Old Redis session deleted, new session created, new JWT issued

## Prerequisites

- `ticketing-common-library` installed in local Maven repo
- Redis running on `localhost:6379`

## Seed Users

Created on first startup:

| Email | Role | Password |
|---|---|---|
| admin@ticketing.com | ADMIN | ChangeMe123! |
| organizer@ticketing.com | ORGANIZER | ChangeMe123! |
| customer@ticketing.com | CUSTOMER | ChangeMe123! |

## Run

```bash
mvn spring-boot:run
```

## Swagger UI

```
http://localhost:8082/swagger-ui.html
```
