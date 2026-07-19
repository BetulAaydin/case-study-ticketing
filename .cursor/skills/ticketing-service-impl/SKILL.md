---
name: ticketing-service-impl
description: |-
  Skill 2 — Ticketing Service: Event CRUD + publish, Reservation, idempotency, oversell, audit.
  Primary agent: Codex (idempotency, concurrency, ownership). Fable race/AuthZ review; Grok edge-case brainstorm.
  Multi-agent dagitim icin once ticketing-orchestrator kullan.
  TRIGGER when:
  - Event veya reservation is mantigi implement edilecekse
  - Ticketing service iskeleti, domain, service, controller katmanlari olusturulacaksa
  - Idempotency, oversell korunmasi, audit logging implement edilecekse
  DO NOT TRIGGER when:
  - CommonLibrary, Auth veya Gateway implement edilecekse (ticketing-base-infra / skill 1 kullan)
  - Test yazilacaksa (ticketing-tests kullan)
  - README/dokumantasyon istendiginde (ticketing-readme kullan)
  - Sadece agent/gorev dagitimi soruluyorsa (ticketing-orchestrator kullan)
---

# Ticketing Service Implementation (Skill 2)

Event ve reservation is mantigi servisini 4 fazda implement eder. JWT decode/encode **yapmaz**; Gateway'in
gonderdigi header'lara guvenir. Coarse-grained AuthZ Gateway'dedir; bu servis yalnizca fine-grained ownership
ve is kurallarini uygular.

> **On kosul:** Skill 1 (`ticketing-base-infra`) tamamlanmis olmali. `ticketing-common-library` local Maven
> repo'da (`mvn install`) olmali.
> **Orchestration:** [ticketing-orchestrator](../ticketing-orchestrator/SKILL.md)

## Agent Assignment

| Rol | Agent | Model | Bu skill'deki is |
|---|---|---|---|
| Primary implementer | **Codex** | `gpt-5.2-codex` | IdempotencyService, ReservationService retry/oversell, EventService ownership, GatewayHeaderAuthFilter, SessionRoleResolver |
| Security / race reviewer | **Fable** | `claude-fable-5-thinking-high` | Oversell race, idempotency PROCESSING gap, ADMIN via Redis session, header trust model |
| Edge-case brainstorm | **Grok** | `cursor-grok-4.5-high-fast` | Cancel/confirm edge-cases, capacity sinirlari; implementasyonu Codex'e birak |

**Akis:** Codex Faz 1–4 → Fable race/idempotency/ownership review → Grok eksik edge-case listesi (opsiyonel) → Codex fix.

> **Referans:** Case study [CL_Case_1_v2.pdf](../../../CL_Case_1_v2.pdf). Gateway path prefix `/api/ticket` +
> `RewritePath` strip eder; downstream controller path'leri prefix'sizdir.

---

## Mimari Kararlar (sabit)

| Konu | Karar |
|---|---|
| Client path | Gateway: `/api/ticket/events/**`, `/api/ticket/reservations/**` |
| Downstream path | Controller: `/events`, `/reservations` (prefix yok) |
| Identity headers | Yalnizca `X-User-Id`, `X-Session-Id` (email/roles **gelmez**) |
| ADMIN ownership | `X-Session-Id` ile Redis `TICKET:user-session:{sid}` okunur; `roles` icinde `ADMIN` varsa bypass |
| Idempotency | Redis Hash (JPA `IdempotencyKey` entity **yok**); key `TICKET:idempotency:{key}:{endpoint}`, TTL 24h |
| Capacity | `reservedSeats` + `@Version` optimistic locking |
| Redis | Auth/gateway ile ayni cluster |
| H2 console | Boot 4: `spring-boot-h2console` + ayri `SecurityFilterChain` |

### Endpoint eslemesi (client → service)

| Client (Gateway :8080) | Downstream (:8081) |
|---|---|
| `POST /api/ticket/events` | `POST /events` |
| `PUT /api/ticket/events/{id}` | `PUT /events/{id}` |
| `POST /api/ticket/events/{id}/publish` | `POST /events/{id}/publish` |
| `GET /api/ticket/events` | `GET /events` |
| `GET /api/ticket/events/public` | `GET /events/public` |
| `POST /api/ticket/events/{id}/reservations` | `POST /events/{id}/reservations` |
| `POST /api/ticket/reservations/{id}/confirm` | `POST /reservations/{id}/confirm` |
| `POST /api/ticket/reservations/{id}/cancel` | `POST /reservations/{id}/cancel` |

### CommonLibrary oncesi ek (skill 1 / RedisKeys)

Implementasyona baslamadan once `RedisKeys`'e ekle (yoksa):

```java
public static final String IDEMPOTENCY_PREFIX = APP_PREFIX + "idempotency:";

public static String idempotencyKey(String key, String endpoint) {
    return IDEMPOTENCY_PREFIX + key + ":" + endpoint;
}
```

---

## Faz 1: Proje Iskeleti

### 1.1 pom.xml

- Parent: `spring-boot-starter-parent` 4.0.x (or. 4.0.7)
- Java: 21
- Dependencies:
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-jpa`
  - `spring-boot-starter-validation`
  - `spring-boot-starter-security`
  - `spring-boot-starter-data-redis`
  - `spring-boot-starter-actuator`
  - `springdoc-openapi-starter-webmvc-ui`
  - `h2` (runtime)
  - `spring-boot-h2console` (Boot 4 H2 console auto-config)
  - `ticketing-common-library` 1.0.0-SNAPSHOT
  - `spring-boot-starter-test` + `spring-security-test` (test)

### 1.2 application.yml

```yaml
server:
  port: 8081

spring:
  application:
    name: ticketing-service
  datasource:
    url: jdbc:h2:mem:ticketingdb;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  h2:
    console:
      enabled: true
      path: /h2-console
      settings:
        web-allow-others: true
  data:
    redis:
      cluster:
        nodes:
          - ${REDIS_NODE_1:127.0.0.1:6379}
          - ${REDIS_NODE_2:127.0.0.1:6379}
          - ${REDIS_NODE_3:127.0.0.1:6379}
      password: ${REDIS_PASSWORD:}

springdoc:
  api-docs.path: /v3/api-docs
  swagger-ui.path: /swagger-ui.html

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### 1.3 SecurityConfig

Iki `SecurityFilterChain` (auth servisi ile ayni pattern):

1. `@Order(1)` H2 console: `PathPatternRequestMatcher.pathPattern("/h2-console/**")`, CSRF disable, `frameOptions(sameOrigin)`, permitAll.
2. `@Order(2)` uygulama:
   - CSRF disable, STATELESS
   - Permit all: `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `GET /events/public/**`
   - Diger: `authenticated()` (GatewayHeaderAuthFilter SecurityContext set etmis olmali)
   - `GatewayHeaderAuthFilter` zincire eklenir (`UsernamePasswordAuthenticationFilter` oncesi)

### 1.4 GatewayHeaderAuthFilter

`OncePerRequestFilter` extend eder. CommonLibrary `GatewayHeaders` kullanir.

1. `X-User-Id` yoksa filter'i atla (public path'ler SecurityConfig ile acik; protected path'ler 401).
2. `X-Session-Id` varsa details map'ine koy (`sessionId`).
3. `UsernamePasswordAuthenticationToken` olustur: principal = userId string, authorities = bos liste (rol kontrolu Gateway'de; ADMIN fine-grained icin `SessionRoleResolver` kullanilir).
4. `SecurityContextHolder` set et.

**NOT:** `X-User-Roles` / `X-User-Email` **okunmaz** — Gateway bunlari gondermez.

### 1.5 SessionRoleResolver

```java
@Component
public class SessionRoleResolver {
    // StringRedisTemplate + RedisKeys.userSessionKey(sessionId)
    // Redis Hash "roles" alanini virgul ile parse eder
    boolean isAdmin(String sessionId);
    List<String> resolveRoles(String sessionId); // session yok/bos -> empty list
}
```

Event ownership'te ADMIN bypass icin kullanilir.

### 1.6 TicketingApplication

```java
@SpringBootApplication
public class TicketingApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketingApplication.class, args);
    }
}
```

### 1.7 Dogrulama

```bash
cd ticketing-service && mvn compile
```

---

## Faz 2: Domain + Repository

### 2.1 Entity'ler

**Event:**
```
@Entity @Table(name = "events")
- id: Long (@Id @GeneratedValue)
- ownerId: Long (nullable=false)
- title: String (nullable=false)
- venue: String (nullable=false)
- startsAt: LocalDateTime (nullable=false)
- endsAt: LocalDateTime (nullable=false)
- capacity: int (nullable=false)
- reservedSeats: int (default 0)
- published: boolean (default false)
- version: Long (@Version)
```

**Reservation:**
```
@Entity @Table(name = "reservations")
- id: Long (@Id @GeneratedValue)
- eventId: Long (nullable=false)
- userId: Long (nullable=false)
- status: ReservationStatus (@Enumerated STRING, default PENDING)
- seats: int (nullable=false)
- createdAt: LocalDateTime
```

**AuditLog:**
```
@Entity @Table(name = "audit_logs")
- id: Long (@Id @GeneratedValue)
- actorId: Long
- action: String (nullable=false)
- resourceType: String
- resourceId: Long
- ip: String
- userAgent: String
- createdAt: LocalDateTime
```

### 2.2 Enum

**ReservationStatus:** `PENDING`, `CONFIRMED`, `CANCELLED`

(Role enum bu serviste zorunlu degil; Redis'ten string `"ADMIN"` karsilastirilir.)

### 2.3 Repository'ler

**EventRepository:**
```java
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByOwnerId(Long ownerId);

    @Query("SELECT e FROM Event e WHERE e.published = true " +
           "AND (:from IS NULL OR e.startsAt >= :from) " +
           "AND (:to IS NULL OR e.endsAt <= :to) " +
           "AND (:query IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Event> searchPublished(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("query") String query);
}
```

**ReservationRepository:** `findByEventId`, `findByUserId`

**AuditLogRepository:** `JpaRepository<AuditLog, Long>`

### 2.4 Dogrulama

```bash
cd ticketing-service && mvn compile
```

---

## Faz 3: Service Katmani

### 3.1 EventService

`@Service @Transactional`

- `create(Long ownerId, EventCreateRequest req)`: `published=false`, `reservedSeats=0`. Audit log. `EventResponse`.
- `update(Long userId, String sessionId, Long eventId, EventUpdateRequest req)`:
  - Ownership: `ownerId == userId` **veya** `sessionRoleResolver.isAdmin(sessionId)`.
  - `published == true` ise guncelleme engelle (`BusinessException`).
- `publish(Long userId, String sessionId, Long eventId)`: ayni ownership, `published=true`.
- `list(Long ownerId)`: `ownerId` varsa filtrele, yoksa tumu.
- `publicSearch(from, to, query)`: `searchPublished`.

Ownership fail: `BusinessException("EVENT_FORBIDDEN", ...)`.

### 3.2 ReservationService

`@Service @Transactional`

- `create(Long userId, Long eventId, int seats)`:
  1. Event bul; `published` degilse `BusinessException`.
  2. `capacity - reservedSeats >= seats` degilse `BusinessException("Not enough capacity")`.
  3. `reservedSeats += seats` (optimistic lock).
  4. Reservation `PENDING` kaydet; audit.
  5. `OptimisticLockingFailureException` → max **3** retry (Event'i her seferinde yeniden oku).

- `confirm(Long userId, Long reservationId)`: ownership (`reservation.userId`), status `PENDING` → `CONFIRMED`.
- `cancel(Long userId, Long reservationId)`: ownership; zaten `CANCELLED` degilse status `CANCELLED`, `reservedSeats -= seats` (Event `@Version` ile).

### 3.3 IdempotencyService

Redis-backed. `StringRedisTemplate` + `RedisKeys.idempotencyKey(key, endpoint)`.

```java
@Service
public class IdempotencyService {
    private static final long TTL_SECONDS = 86400;

    /** null = devam et (PROCESSING yazildi); CachedResponse = cache don; BusinessException = conflict/hash mismatch */
    public CachedResponse checkIdempotency(String key, String endpoint, String requestHash);

    public void complete(String key, String endpoint, String responseBody, int statusCode);

    public void fail(String key, String endpoint);
}
```

**CachedResponse** record: `String responseBody`, `int statusCode`.

**checkIdempotency akisi:**
1. Key: `RedisKeys.idempotencyKey(key, endpoint)` → `TICKET:idempotency:{key}:{endpoint}`
2. `HGETALL`
3. Yoksa: `HSETNX` `requestHash` + `status=PROCESSING`, `EXPIRE` 86400 → `null`
4. `COMPLETED` + ayni hash → `CachedResponse`; farkli hash → `BusinessException` (422)
5. `PROCESSING` → `BusinessException` (409 concurrent)
6. `FAILED` → `status=PROCESSING` set, `null` (retry)

Request hash: CommonLibrary `HashUtil.sha256(requestBodyJson)`.

### 3.4 AuditService

```java
public void log(Long actorId, String action, String resourceType, Long resourceId,
                String ip, String userAgent);
```

IP: `GatewayHeaders.FORWARDED_FOR` veya `request.getRemoteAddr()`. User-Agent: `User-Agent` header.

### 3.5 Dogrulama

```bash
cd ticketing-service && mvn compile
```

---

## Faz 4: Controller + DTO

### 4.1 DTO'lar (Java Records)

```java
public record EventCreateRequest(
    @NotBlank String title,
    @NotBlank String venue,
    @NotNull LocalDateTime startsAt,
    @NotNull LocalDateTime endsAt,
    @Min(1) int capacity
) {}

public record EventUpdateRequest(
    String title,
    String venue,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    @Min(1) Integer capacity
) {}

public record EventResponse(
    Long id, Long ownerId, String title, String venue,
    LocalDateTime startsAt, LocalDateTime endsAt,
    int capacity, int reservedSeats, boolean published
) {}

public record ReservationCreateRequest(@Min(1) int seats) {}

public record ReservationResponse(
    Long id, Long eventId, Long userId,
    ReservationStatus status, int seats, LocalDateTime createdAt
) {}
```

### 4.2 EventController

```java
@RestController
@RequestMapping("/events")
@Tag(name = "Events")
```

- `POST /` → `ApiResponse<EventResponse>` — `@RequestHeader(GatewayHeaders.USER_ID) Long userId`, `@RequestHeader(value=GatewayHeaders.SESSION_ID, required=false) String sessionId`
- `PUT /{id}` → ayni header'lar + body
- `POST /{id}/publish` → ayni
- `GET /` → `@RequestParam(required=false) Long ownerId` + userId header
- `GET /public` → `@RequestParam(required=false)` from, to, q — **header zorunlu degil**

Tum metotlar `ApiResponse<T>` (RULE-1).

### 4.3 ReservationController

```java
@RestController
@Tag(name = "Reservations")
```

- `POST /events/{eventId}/reservations` → `ApiResponse<ReservationResponse>`
  - `@RequestHeader(GatewayHeaders.IDEMPOTENCY_KEY) String idempotencyKey` zorunlu (yoksa 400)
  - `HashUtil.sha256(body)` ile `checkIdempotency`; cache varsa direkt don
  - `create` → `complete`; hata → `fail`
- `POST /reservations/{id}/confirm`
- `POST /reservations/{id}/cancel`

### 4.4 OpenApiConfig

OpenAPI title: `Ticketing Service API`, version `1.0.0`.

### 4.5 Dosya yapisi

```
ticketing-service/
├── pom.xml
├── src/main/java/com/turkcell/mayacore/ticketing/
│   ├── TicketingApplication.java
│   ├── config/
│   │   └── OpenApiConfig.java
│   ├── controller/
│   │   ├── EventController.java
│   │   └── ReservationController.java
│   ├── domain/
│   │   ├── Event.java
│   │   ├── Reservation.java
│   │   ├── AuditLog.java
│   │   └── ReservationStatus.java
│   ├── dto/
│   │   ├── EventCreateRequest.java
│   │   ├── EventUpdateRequest.java
│   │   ├── EventResponse.java
│   │   ├── ReservationCreateRequest.java
│   │   └── ReservationResponse.java
│   ├── repository/
│   │   ├── EventRepository.java
│   │   ├── ReservationRepository.java
│   │   └── AuditLogRepository.java
│   ├── security/
│   │   ├── SecurityConfig.java
│   │   ├── GatewayHeaderAuthFilter.java
│   │   └── SessionRoleResolver.java
│   └── service/
│       ├── EventService.java
│       ├── ReservationService.java
│       ├── IdempotencyService.java
│       └── AuditService.java
└── src/main/resources/
    └── application.yml
```

### 4.6 Dogrulama

```bash
cd ticketing-common-library && mvn clean install
cd ticketing-service && mvn compile
```

Manuel smoke (gateway uzerinden): login → create event → publish → reservation (`Idempotency-Key`) → confirm/cancel.

---

## Kritik Kurallar

- Tum controller metotlari `ApiResponse<T>` doner (RULE-1).
- `BusinessException` / `SystemException` icin ozel `@ExceptionHandler` yazilmaz (RULE-2); `GlobalExceptionHandler` CommonLibrary'den gelir.
- Oversell: `@Version` + capacity check + max 3 retry.
- Idempotency yalnizca reservation create; Redis TTL 24h; CommonLibrary `HashUtil` + `RedisKeys.idempotencyKey`.
- Bu servis JWT **kullanmaz**. Identity: `X-User-Id` / `X-Session-Id`.
- Downstream path'ler `/api` veya `/api/ticket` **icermez**.
- Tum Redis key'leri `TICKET:` prefix (CommonLibrary `RedisKeys`).
- Testler bu skill kapsaminda **degil** (`ticketing-tests`).
- README bu skill kapsaminda **degil** (`ticketing-readme`).
