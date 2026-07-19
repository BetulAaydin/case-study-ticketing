# Ticketing API Gateway

Servlet-based Spring Cloud Gateway (WebMVC) handling JWT validation, coarse-grained authorization, and request routing. No WebFlux dependency.

## Port

`8080`

## Routing

| Route ID | Target | Path |
|---|---|---|
| auth-service | `http://localhost:8082` | `/api/auth/**` |
| ticketing-public | `http://localhost:8081` | `GET /api/events/public/**` |
| ticketing-service | `http://localhost:8081` | `/api/events/**`, `/api/reservations/**` |

## Authorization Rules

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

## Security (Spring Security + OAuth2 Resource Server)

Follows MayaCore API Gateway pattern with `SecurityFilterChain`:

```
Request → Spring Security Filter Chain
  → NimbusJwtDecoder (validates HS256 signature + expiry)
  → JwtUserSessionConverter (extracts sid → Redis lookup → authorities)
  → AuthorizationFilter (role-based access via authorizeHttpRequests)
  → UserHeaderForwardingFilter (adds X-User-* headers, strips Authorization)
  → Gateway proxy → downstream service
```

| Component | Responsibility |
|---|---|
| `SecurityConfig` | `SecurityFilterChain` bean: CSRF, stateless, `authorizeHttpRequests`, `oauth2ResourceServer`, `JwtDecoder` |
| `JwtUserSessionConverter` | `Converter<Jwt, AbstractAuthenticationToken>`: reads `sid` → Redis → builds authorities |
| `UserHeaderForwardingFilter` | `addFilterAfter(AuthorizationFilter)`: forwards `X-User-Id`, `X-User-Email`, `X-User-Roles` to downstream |

## Prerequisites

- Redis running on `localhost:6379` (shared for session lookup + rate limiting)
- `ticketing-common-library` installed in local Maven repo

## Run

```bash
mvn spring-boot:run
```
