# Ticketing Common Library

Shared JAR library used by all other services in the ticketing platform.

## Contents

| Package | Class | Description |
|---|---|---|
| `dto` | `ApiResponse<T>` | Standard API response wrapper with `success`, `data`, `errorCode`, `errorMessage`, `timestamp` |
| `exception` | `BusinessException` | HTTP 4xx errors (e.g. validation, not found) |
| `exception` | `SystemException` | HTTP 5xx errors (e.g. infrastructure failures) |
| `exception` | `GlobalExceptionHandler` | `@ControllerAdvice` for validation and generic exceptions |
| `security` | `JwtService` | HS256 JWT token generation and validation (JJWT) |
| `security` | `JwtProperties` | `@ConfigurationProperties(prefix="security.jwt")` |
| `util` | `GatewayHeaders` | Constants: `X-User-Id`, `X-User-Email`, `X-User-Roles`, etc. |
| `util` | `HashUtil` | SHA-256 hashing utility |
| `util` | `DateUtil` | ISO-8601 date/time conversion utilities |

## Build

```bash
mvn clean install
```

This installs the JAR to your local Maven repository so other modules can depend on it.

## Configuration

When using this library, configure JWT properties in your `application.yml`:

```yaml
security:
  jwt:
    secret: <base64-encoded-secret>
    access-ttl-minutes: 30
    refresh-ttl-days: 7
```

Auto-configuration is registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
