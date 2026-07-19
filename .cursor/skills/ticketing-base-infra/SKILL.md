---
name: ticketing-base-infra
description: |-
  Skill 1 — Base infrastructure: ticketing-common-library, ticketing-auth, ticketing-apigateway.
  Primary agent: Codex (Security, JWT, rate limit). Fable security review; Grok mimari ikinci gorus.
  Multi-agent dagitim icin once ticketing-orchestrator kullan.
  TRIGGER when:
  - CommonLibrary, Auth veya Gateway projesi olusturulmasi istendiginde
  - JWT, authentication, authorization altyapisi kurulmasi gerektiginde
  - Base infra, iskelet, scaffold istendiginde
  DO NOT TRIGGER when:
  - Ticketing Service (event/reservation) implementasyonu istendiginde (ticketing-service-impl kullan)
  - Test yazilmasi istendiginde (ticketing-tests kullan)
  - README/dokumantasyon istendiginde (ticketing-readme kullan)
  - Sadece agent/gorev dagitimi soruluyorsa (ticketing-orchestrator kullan)
---

# Base Infrastructure Implementation (Skill 1)

CommonLibrary + Auth + API Gateway implementasyonunu 3 fazda gerceklestirir. Her faz sonunda `mvn compile` ile dogrulama yapilir.

> **Referans:** Detayli mimari kararlar icin [ticket-plan.md](../../../ticket-plan.md) dosyasini oku.
> **Orchestration:** [ticketing-orchestrator](../ticketing-orchestrator/SKILL.md)

## Agent Assignment

| Rol | Agent | Model | Bu skill'deki is |
|---|---|---|---|
| Primary implementer | **Codex** | `gpt-5.2-codex` | Spring Security config, JWT service/decoder/converter, gateway filters, Redis session, rate limit |
| Security reviewer | **Fable** | `claude-fable-5-thinking-high` | JWT saldiri senaryolari, Redis session/revocation elestirisi, AuthZ aciklari, header strip spoofing |
| Second opinion | **Grok** | `cursor-grok-4.5-high-fast` | Thin JWT vs alternatifler, path/prefix trade-off; README degil (skill 4) |

**Akis:** Codex implement → Fable review → kritik bulgu varsa Codex fix. Buyuk mimari sapmada once Grok.

---

## Faz 1: ticketing-common-library

Shared JAR kutuphanesi. Diger uc proje bunu dependency olarak kullanir.

### 1.1 pom.xml

```xml
<groupId>com.turkcell.mayacore</groupId>
<artifactId>ticketing-common-library</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>jar</packaging>
```

- Parent: `spring-boot-starter-parent` 4.0.x
- Java: 21
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-validation`, JJWT (api + impl + jackson) 0.12.6
- Spring Boot Maven Plugin **dahil edilmez** (plain JAR, executable degil)

### 1.2 Siniflar

Package: `com.turkcell.mayacore.commonlibrary`

| Sinif | Package | Detay |
|---|---|---|
| `ApiResponse<T>` | `dto` | `success`, `data`, `errorCode`, `errorMessage`, `timestamp` alanlari. `static success(T data)` ve `static error(String code, String msg)` factory metotlari. |
| `BusinessException` | `exception` | `errorCode` + `message` alanlari. HTTP 4xx hatalari icin. `RuntimeException` extend eder. |
| `SystemException` | `exception` | `errorCode` + `message` alanlari. HTTP 5xx hatalari icin. `RuntimeException` extend eder. |
| `GlobalExceptionHandler` | `exception` | `@ControllerAdvice`. `MethodArgumentNotValidException`, `ConstraintViolationException`, genel `Exception` handler. **BusinessException ve SystemException icin handler YAZMAZ** (RULE-2). Tum response'lar `ApiResponse` formatinda. |
| `JwtService` | `security` | `generateToken(String sessionId, String email)`: HS256 access token uretir (JWT sadece `sid` + `sub` icerir, roller Redis'te). `validateToken(String token)`: boolean. `extractClaims(String token)`: Claims. `extractSessionId(String token)`: String. JJWT kullanir. |
| `RedisKeys` | `util` | Final class. Tum key'ler `TICKET:` app prefix'i ile baslar. `USER_SESSION_PREFIX`, `RATE_LIMIT_PREFIX`, `IDEMPOTENCY_PREFIX` (`TICKET:idempotency:`). `userSessionKey`, `rateLimitKey`, `idempotencyKey(key, endpoint)` — sonuncusu skill 2 (ticketing-service) icin. |
| `JwtProperties` | `security` | `@ConfigurationProperties(prefix="security.jwt")`. Alanlar: `secret` (String), `algorithm` (String, default "HmacSHA256"), `accessTtlMinutes` (long, default 30), `refreshTtlDays` (long, default 7). |
| `HashUtil` | `util` | `static String sha256(String input)`: SHA-256 hex hash. `MessageDigest` kullanir. |
| `GatewayHeaders` | `util` | Final class, private constructor. Constant'lar: `USER_ID = "X-User-Id"`, `SESSION_ID = "X-Session-Id"`, `FORWARDED_FOR = "X-Forwarded-For"`, `IDEMPOTENCY_KEY = "Idempotency-Key"`. Downstream'e **yalnizca** userId + sessionId gider; email/roller gitmez. |
| `DateUtil` | `util` | `static String toIso8601(LocalDateTime dt)`, `static LocalDateTime fromIso8601(String s)`, `static LocalDateTime nowUtc()`. `DateTimeFormatter.ISO_DATE_TIME` kullanir. |

### 1.3 Auto-configuration

`@EnableConfigurationProperties(JwtProperties.class)` annotation'i `JwtService` veya ayri bir config class uzerinde tanimlanmali. `spring.factories` veya `@AutoConfiguration` ile diger projelerde otomatik aktif olmali.

### 1.4 Dogrulama

```bash
cd ticketing-common-library && mvn clean install
```

Build basarili olmali, local Maven repo'ya yuklenecek.

---

## Faz 2: ticketing-auth

User authentication servisi. Port 8082.

### 2.1 pom.xml

- Parent: `spring-boot-starter-parent` 4.0.x
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-data-redis`, `h2` (runtime), `spring-boot-h2console` (Spring Boot 4'te H2 console auto-config ayri modulde), `ticketing-common-library` (1.0.0-SNAPSHOT), `spring-boot-starter-actuator`, `springdoc-openapi-starter-webmvc-ui`, `spring-boot-starter-test` + `spring-security-test` (test)

### 2.2 Domain Entities

**User:**
```
@Entity @Table(name = "users")
- id: Long (@Id @GeneratedValue)
- email: String (@Column unique, nullable=false)
- passwordHash: String (nullable=false)
- roles: Set<Role> (@ElementCollection @Enumerated(STRING), fetch=EAGER)
- createdAt: LocalDateTime
- lastLoginAt: LocalDateTime
```

**Role (enum):** `ADMIN`, `ORGANIZER`, `CUSTOMER`

**RefreshToken:**
```
@Entity
- id: Long (@Id @GeneratedValue)
- userId: Long (nullable=false)
- token: String (unique, nullable=false) -- UUID
- sessionId: String (nullable=false) -- Redis session key, logout/refresh'te silinir
- expiresAt: LocalDateTime
```

### 2.3 Repository

- `UserRepository`: `Optional<User> findByEmail(String email)`, `boolean existsByEmail(String email)`
- `RefreshTokenRepository`: `Optional<RefreshToken> findByToken(String token)`, `List<RefreshToken> findAllByUserId(Long userId)`, `void deleteByUserId(Long userId)`, `void deleteByExpiresAtBefore(LocalDateTime now)`

### 2.4 DTO (Java Records)

- `AuthRegisterRequest`: `@NotBlank @Email email`, `@NotBlank @Size(min=8) password`, `Role role` (optional, default CUSTOMER)
- `AuthLoginRequest`: `@NotBlank @Email email`, `@NotBlank password`
- `AuthRefreshRequest`: `@NotBlank refreshToken`
- `AuthLogoutRequest`: `@NotBlank refreshToken`
- `AuthResponse`: `accessToken`, `refreshToken`, `tokenType` ("Bearer"), `expiresInMinutes`

### 2.5 UserSessionService

Redis-backed kullanici oturum yonetimi. `StringRedisTemplate` kullanir.

- `createSession(Long userId, String email, List<String> roles)`: UUID olusturur, `saveSession`'a delege eder. Session ID doner.
- `saveSession(String sessionId, Long userId, String email, List<String> roles)`: Redis Hash'e `{userId, email, roles}` yazar, TTL = `accessTtlMinutes`. Refresh akisinda ayni sessionId ile cagirilir -- session TTL ile silinmis olsa bile hash yeniden olusturulur (sadece expire uzatmak yeterli DEGIL).
- `deleteSession(String sessionId)`: Redis key'i siler (token revocation).

Redis key formati: `TICKET:user-session:{sessionId}` (CommonLibrary `RedisKeys` sinifi)

### 2.6 AuthService

- `register(AuthRegisterRequest)`: Email unique kontrolu, BCrypt hash, User kaydet, **Redis'te session olustur**, JWT uret (sessionId ile), RefreshToken olustur (sessionId ile), AuthResponse don.
- `login(AuthLoginRequest)`: Email ile user bul, password verify, lastLoginAt guncelle, **eski session'lari Redis'ten sil**, yeni session olustur, JWT uret, RefreshToken olustur, AuthResponse don.
- `refresh(AuthRefreshRequest)`: Token bul, expiry kontrolu, **ayni sessionId korunur** -- `saveSession` ile session verisi yeniden yazilir ve TTL sifirlanir, ayni `sid` ile yeni JWT uretilir, refresh token rotate edilir (eski silinir, yeni UUID kaydedilir).
- `logout(AuthLogoutRequest)`: **Redis session sil** + refresh token sil.

BCrypt: `PasswordEncoder` bean olarak `SecurityConfig`'de tanimla.

### 2.6 AuthController

```
@RestController @RequestMapping("/auth")
```

Gateway `/api/ticket` prefix'ini `RewritePath` ile stripler; downstream controller path'leri prefix icermez.

- `POST /register` -> `ApiResponse<AuthResponse>`
- `POST /login` -> `ApiResponse<AuthResponse>`
- `POST /refresh` -> `ApiResponse<AuthResponse>`
- `POST /logout` -> `ApiResponse<Void>`

Tum metotlar `ApiResponse<T>` doner (RULE-1).

### 2.7 SecurityConfig

Auth servisi yalnizca token **uretir**, JWT validation **yapmaz** (bu sorumluluk API Gateway'dedir).

- `@EnableWebSecurity`
- CSRF disabled, stateless session
- Permit all: `/auth/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`
- Diger: `denyAll()` (bu servis sadece auth endpoint'leri sunar, diger istekleri reddeder)
- `PasswordEncoder` bean: `BCryptPasswordEncoder` (password hashing icin)
- H2 console icin **ayri** bir `SecurityFilterChain` (`@Order(1)`) tanimlanir: `PathPatternRequestMatcher.pathPattern("/h2-console/**")` securityMatcher, CSRF disable, `frameOptions(sameOrigin)`, permitAll. (Spring Security 7'de `AntPathRequestMatcher` kaldirildi.)

```java
@Bean
@Order(1)
SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
    return http
        .securityMatcher(PathPatternRequestMatcher.pathPattern("/h2-console/**"))
        .csrf(csrf -> csrf.disable())
        .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .build();
}

@Bean
@Order(2)
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/**").permitAll()
            .requestMatchers("/actuator/**").permitAll()
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
            .anyRequest().denyAll()
        )
        .build();
}
```

**NOT:** `oauth2ResourceServer` veya `JwtDecoder` bu serviste **kullanilmaz**. JWT validation Gateway'de yapilir.

### 2.8 SeedDataConfig

`@Component` implementing `CommandLineRunner`. Startup'ta 3 user yoksa olustur:

| Email | Role | Password |
|---|---|---|
| admin@ticketing.com | ADMIN | ChangeMe123! |
| organizer@ticketing.com | ORGANIZER | ChangeMe123! |
| customer@ticketing.com | CUSTOMER | ChangeMe123! |

### 2.9 application.yml

```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:h2:mem:authdb;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
  data:
    redis:
      cluster:
        nodes:
          - ${REDIS_NODE_1:127.0.0.1:6379}
          - ${REDIS_NODE_2:127.0.0.1:6379}
          - ${REDIS_NODE_3:127.0.0.1:6379}
      password: ${REDIS_PASSWORD:}
  jpa:
    hibernate.ddl-auto: update
    open-in-view: false
  h2:
    console:
      enabled: true
      path: /h2-console
      settings:
        web-allow-others: true

security:
  jwt:
    secret: ${JWT_SECRET:dGlja2V0aW5nLWNhc2Utc3R1ZHktc2VjcmV0LWtleS0yMDI2}
    algorithm: HmacSHA256
    access-ttl-minutes: 30
    refresh-ttl-days: 7

management:
  endpoints.web.exposure.include: health,info
```

### 2.10 Dogrulama

```bash
cd ticketing-auth && mvn compile
```

---

## Faz 3: ticketing-apigateway

API Gateway. Port 8080. JWT validation, coarse-grained authorization, rate limiting, routing.

### 3.1 pom.xml

- Parent: `spring-boot-starter-parent` 4.0.x
- Dependencies: `spring-cloud-starter-gateway-server-webmvc` (servlet-based), `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-data-redis`, `ticketing-common-library` (1.0.0-SNAPSHOT), `spring-boot-starter-actuator`, `spring-boot-starter-test` (test)
- Spring Cloud BOM: `spring-cloud-dependencies` (Spring Boot 4 uyumlu versiyon)
- **NOT:** Gateway servlet-based'dir (MVC), WebFlux dependency'si yoktur.

### 3.2 SecurityConfig

`SecurityFilterChain` bean ile JWT validation ve authorization. MayaCore API Gateway patterni:

```java
http.csrf(csrf -> csrf.disable());
http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/ticket/auth/**", "/actuator/**").permitAll()
    .requestMatchers(HttpMethod.GET, "/api/ticket/events/public/**").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/ticket/events/**").hasAnyAuthority("ORGANIZER", "ADMIN")
    // ... diger kurallar
    .anyRequest().authenticated()
);
http.oauth2ResourceServer(oauth ->
    oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtUserSessionConverter))
);
http.addFilterAfter(userHeaderForwardingFilter, AuthorizationFilter.class);
http.addFilterAfter(rateLimitFilter, UserHeaderForwardingFilter.class);
```

- `JwtDecoder` bean: `NimbusJwtDecoder.withSecretKey()` + `MacAlgorithm.HS256` ile symmetric key dogrulama. `SecretKeySpec` algoritmasi `JwtProperties.getAlgorithm()`'den okunur.
- Authorization kurallari Spring Security `authorizeHttpRequests` ile tanimlanir (manuel AuthRule match yerine). Matcher'lar client-facing path'leri (`/api/ticket/...`) kullanir cunku security, `RewritePath`'ten **once** calisir.

### 3.3 JwtUserSessionConverter

`Converter<Jwt, AbstractAuthenticationToken>` implement eder. `StringRedisTemplate` inject eder.

1. JWT'den `sid` claim'ini cikarir.
2. `RedisKeys.userSessionKey(sessionId)` ile Redis Hash okunur. Session bulunamazsa `InvalidBearerTokenException` firlatir (401).
3. Redis'ten `userId`, `email`, `roles` alinir.
4. `roles` -> `SimpleGrantedAuthority` listesine donusturulur.
5. `JwtAuthenticationToken` olusturulur, `details` map'ine `userId` ve `sessionId` eklenir (header forwarding filtresi bunlari okur).

### 3.4 UserHeaderForwardingFilter

`OncePerRequestFilter` extend eder. `SecurityConfig`'de `addFilterAfter(AuthorizationFilter.class)` ile zincirlenir.

1. `SecurityContextHolder`'dan `JwtAuthenticationToken` alinir.
2. Token details'inden `userId`, `sessionId` okunur.
3. `HttpServletRequestWrapper` ile **yalnizca** `GatewayHeaders.USER_ID` ve `GatewayHeaders.SESSION_ID` header'lari eklenir (email/roller downstream'e GITMEZ).
4. `Authorization` header'i **tum isteklerde** (public dahil) strip edilir -- downstream JWT gormez.
5. Rate limit filtresi icin `request.setAttribute("userId", userId)` set edilir.

### 3.5 RateLimitFilter + RateLimitProperties

Redis-backed fixed-window rate limiting. `OncePerRequestFilter`, `UserHeaderForwardingFilter`'dan **sonra** zincirlenir (userId attribute'una ihtiyac duyar).

`RateLimitProperties` (`@ConfigurationProperties(prefix="security.rate-limit")`):

| Alan | Default | Aciklama |
|---|---|---|
| `enabled` | true | Rate limit acik/kapali |
| `windowSeconds` | 60 | Fixed window suresi |
| `loginMaxRequests` | 5 | `POST /api/ticket/auth/login` icin IP bazli limit (brute-force korumasi) |
| `authenticatedMaxRequests` | 100 | Authenticated istekler icin `user:{userId}` bazli limit |
| `anonymousMaxRequests` | 50 | Diger anonim istekler icin IP bazli limit |

Bucket secimi:
1. Path `POST /api/ticket/auth/login` -> identifier `login:ip:{ip}`, limit = `loginMaxRequests`
2. `request.getAttribute("userId")` dolu -> identifier `user:{userId}`, limit = `authenticatedMaxRequests`
3. Diger -> identifier `anon:ip:{ip}`, limit = `anonymousMaxRequests`

Redis key: `RedisKeys.rateLimitKey(identifier, windowId)` -> `TICKET:rate-limit:{identifier}:{windowId}`. `INCR` + ilk istekte `EXPIRE windowSeconds`.
Limit asilirsa `429 Too Many Requests` + JSON error body. Response'lara `X-RateLimit-Limit` ve `X-RateLimit-Remaining` header'lari eklenir.

### 3.6 Coarse-grained Authorization Kurallari

`SecurityConfig.authorizeHttpRequests` icinde tanimlanir (client-facing `/api/ticket` prefix'li path'ler):

| Route Pattern | Method | Izin Verilen Roller |
|---|---|---|
| `/api/ticket/auth/**` | ANY | PERMIT_ALL (JWT kontrolu yok) |
| `/api/ticket/events/public/**` | GET | PERMIT_ALL (JWT kontrolu yok) |
| `/api/ticket/events/**` | POST | ORGANIZER, ADMIN |
| `/api/ticket/events/**` | PUT | ORGANIZER, ADMIN |
| `/api/ticket/events` | GET | Authenticated (any role) |
| `/api/ticket/events/*/reservations` | POST | CUSTOMER |
| `/api/ticket/reservations/*/confirm` | POST | CUSTOMER |
| `/api/ticket/reservations/*/cancel` | POST | CUSTOMER |
| `/actuator/**` | ANY | PERMIT_ALL |

### 3.7 Route Tanimlari (application.yml)

Property prefix: `spring.cloud.gateway.server.webmvc.routes` (Gateway Server WebMVC). Tum route'larda `RewritePath` ile `/api/ticket` prefix'i striplenir; downstream servisler prefix'siz path alir (or. `/auth/login`, `/events/public`).

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: auth-service
              uri: http://localhost:8082
              predicates:
                - Path=/api/ticket/auth/**
              filters:
                - RewritePath=/api/ticket(?<segment>/?.*), ${segment}
            - id: ticketing-public
              uri: http://localhost:8081
              predicates:
                - Path=/api/ticket/events/public/**
                - Method=GET
              filters:
                - RewritePath=/api/ticket(?<segment>/?.*), ${segment}
            - id: ticketing-service
              uri: http://localhost:8081
              predicates:
                - Path=/api/ticket/events/**,/api/ticket/reservations/**
              filters:
                - RewritePath=/api/ticket(?<segment>/?.*), ${segment}
```

### 3.8 application.yml (diger ayarlar)

```yaml
server:
  port: 8080

spring:
  data:
    redis:
      cluster:
        nodes:
          - ${REDIS_NODE_1:127.0.0.1:6379}
          - ${REDIS_NODE_2:127.0.0.1:6379}
          - ${REDIS_NODE_3:127.0.0.1:6379}
      password: ${REDIS_PASSWORD:}

security:
  jwt:
    secret: ${JWT_SECRET:dGlja2V0aW5nLWNhc2Utc3R1ZHktc2VjcmV0LWtleS0yMDI2}
    algorithm: HmacSHA256
  rate-limit:
    enabled: true
    window-seconds: 60
    login-max-requests: 5
    authenticated-max-requests: 100
    anonymous-max-requests: 50

management:
  endpoints.web.exposure.include: health,info
```

### 3.9 Dosya Yapisi

```
ticketing-apigateway/
├── pom.xml
├── src/main/java/com/turkcell/mayacore/apigateway/
│   ├── ApiGatewayApplication.java
│   ├── config/
│   │   ├── RateLimitProperties.java
│   │   └── SecurityConfig.java
│   ├── converter/
│   │   └── JwtUserSessionConverter.java
│   └── filter/
│       ├── RateLimitFilter.java
│       └── UserHeaderForwardingFilter.java
├── src/main/resources/
│   └── application.yml
```

### 3.10 Dogrulama

```bash
cd ticketing-apigateway && mvn compile
```

Tum uc proje derlenebilmeli. Redis calisirken gateway + auth baslatilip `POST /api/ticket/auth/login` (gateway uzerinden) ile JWT alinabilmeli.

---

## Kritik Kurallar

- Tum controller metotlari `ApiResponse<T>` doner (RULE-1).
- `BusinessException` / `SystemException` icin ozel `@ExceptionHandler` yazilmaz (RULE-2).
- JWT secret her iki projede ayni olmali (env variable veya ayni default).
- CommonLibrary'de `spring-boot-maven-plugin` kullanilmaz (fat JAR degil, plain JAR).
- Gateway servlet-based'dir (WebMVC). WebFlux dependency'si **kullanilmaz**.
- Client-facing tum path'ler `/api/ticket` prefix'i tasir; gateway `RewritePath` ile stripler, downstream controller'lar prefix'siz map edilir.
- Downstream'e yalnizca `X-User-Id` + `X-Session-Id` gider; `Authorization` header'i her istekte striplenir.
- Tum Redis key'leri `TICKET:` prefix'i ile baslar (`RedisKeys` sinifi uzerinden olusturulur).
