# ticketing-service

Event CRUD, publish, public discovery, reservation (PENDING → CONFIRMED / CANCELLED),
Redis idempotency, audit log, optimistic locking.

## Port

`8081`

## Run

```bash
# Prerequisite: ticketing-common-library installed, Redis up
mvn spring-boot:run
```

## OpenAPI

- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI JSON: http://localhost:8081/v3/api-docs

Client traffic should go through Gateway (`:8080` → `/api/**`).
