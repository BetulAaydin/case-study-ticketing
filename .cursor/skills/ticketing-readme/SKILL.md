---
name: ticketing-readme
description: |-
  Skill 4 — README, ADR, setup, auth flow, seed users, OpenAPI linkleri.
  Primary agent: Grok (dokumantasyon / aciklama). Codex teknik dogruluk; Fable security ADR.
  Multi-agent dagitim icin once ticketing-orchestrator kullan.
  TRIGGER when:
  - README, dokumantasyon, setup talimatlari istendiginde
  - ADR (Architecture Decision Record) yazilmasi istendiginde
  - Seed user bilgileri, auth flow aciklamasi istendiginde
  DO NOT TRIGGER when:
  - Uygulama kodu implement edilecekse (ticketing-base-infra veya ticketing-service-impl kullan)
  - Test yazilacaksa (ticketing-tests kullan)
  - Sadece agent/gorev dagitimi soruluyorsa (ticketing-orchestrator kullan)
---

# README & Documentation (Skill 4)

Her proje icin README.md ve ana proje README.md olusturur. PDF case study'deki README expectations'a uygun.

> **Referans:** Mimari detaylar icin [ticket-plan.md](../../../ticket-plan.md) dosyasini oku.
> **Orchestration:** [ticketing-orchestrator](../ticketing-orchestrator/SKILL.md)
> **Guncel mimari:** Gateway `/api/ticket` + RewritePath; thin JWT + Redis session; downstream yalnizca `X-User-Id` + `X-Session-Id`; Redis cluster + `TICKET:` prefix.

## Agent Assignment

| Rol | Agent | Model | Bu skill'deki is |
|---|---|---|---|
| Primary author | **Grok** | `cursor-grok-4.5-high-fast` | Ana + alt README, ADR metni, curl ornekleri, mimari aciklama, alternatif karar gerekceleri |
| Technical fact-check | **Codex** | `gpt-5.2-codex` | Port/path/header/endpoint dogrulugu; yanlis dokumantasyonu duzeltir |
| Security ADR | **Fable** | `claude-fable-5-thinking-high` | Threat/security kararlarinin ADR maddeleri (session revoke, rate limit, header trust) |

**Akis:** Grok draft → Codex teknik kontrol → Fable security ADR ekleri → Grok birlestirir.

---

## 1. Ana README (case-study-ticketing/README.md)

Ana dizinde bir README.md olustur. Asagidaki sectionlari icerir:

### Icerik Yapisi

```markdown
# Secure Ticketing & Reservation API

## Overview
Kisa aciklama: 4 microservice, Spring Boot 4, Java 21, JWT auth, Redis rate limiting.

## Architecture
Mimari diyagram (mermaid veya metin). 4 proje ve sorumluluk ozeti tablosu.

## Prerequisites
- Java 21+
- Maven 3.9+
- Redis 7+ (lokal kurulum: `brew install redis`)

## Quick Start
1. CommonLibrary derle
2. Redis baslat
3. Auth servisi baslat
4. Ticketing servisi baslat
5. Gateway baslat
6. Test et

## Auth Flow
Adim adim auth akisi aciklamasi + curl ornekleri.

## Sample JWT
Ornek decoded JWT token.

## Seed Users
Tablo: email, rol, sifre.

## API Endpoints
Tum endpoint'lerin ozet tablosu.

## Architectural Decisions (ADR)
Neden bu kararlar alindi.

## OpenAPI
Swagger UI linki.

## Running Tests
Test calistirma talimatlari + coverage.
```

### Quick Start Section Detayi

```bash
# 1. CommonLibrary
cd ticketing-common-library
mvn clean install

# 2. Redis
redis-server

# 3. Auth Service (yeni terminal)
cd ticketing-auth
mvn spring-boot:run
# Port 8082, seed users otomatik olusturulur

# 4. Ticketing Service (yeni terminal)
cd ticketing-service
mvn spring-boot:run
# Port 8081

# 5. API Gateway (yeni terminal)
cd ticketing-apigateway
mvn spring-boot:run
# Port 8080

# 6. Test
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"organizer@ticketing.com","password":"ChangeMe123!"}'
```

### Auth Flow Section

Adim adim aciklama:

1. **Register:** `POST /api/auth/register` -- yeni kullanici kaydi, varsayilan rol CUSTOMER.
2. **Login:** `POST /api/auth/login` -- email + password ile JWT access token + refresh token alinir.
3. **Authenticated Request:** Access token `Authorization: Bearer <token>` header'i ile gonderilir. Gateway validate eder, role kontrol eder, downstream'e user header'lari ekler.
4. **Refresh:** `POST /api/auth/refresh` -- refresh token ile yeni access token alinir (rotation).
5. **Logout:** `POST /api/auth/logout` -- refresh token iptal edilir.

Her adim icin `curl` ornegi ekle.

### Sample JWT Section

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "organizer@ticketing.com",
    "uid": 2,
    "roles": ["ORGANIZER"],
    "iat": 1721300000,
    "exp": 1721301800
  }
}
```

Access token 30 dakika gecerli, HS256 ile imzali.

### Seed Users Section

| Email | Role | Password |
|---|---|---|
| admin@ticketing.com | ADMIN | ChangeMe123! |
| organizer@ticketing.com | ORGANIZER | ChangeMe123! |
| customer@ticketing.com | CUSTOMER | ChangeMe123! |

Uygulama ilk baslatildiginda otomatik olusturulur (`SeedDataConfig`).

### Architectural Decisions (ADR) Section

Asagidaki kararlar ve gerekceleri aciklanir:

**ADR-1: 4 Ayri Proje**
- CommonLibrary: Kod tekrarini onler, standart API response/exception/JWT.
- Auth servisi: Single Responsibility, kullanici yonetimi izole.
- API Gateway: Cross-cutting concerns (auth, authZ, rate limit) merkezi.
- Ticketing Service: Is mantigi izole, auth yapmaz.

**ADR-2: JWT CommonLibrary'de**
- Auth uretir, Gateway validate eder, ayni JwtService sinifi.
- JJWT dependency tek yerde, transitive.

**ADR-3: Coarse-grained AuthZ Gateway'de, Fine-grained Ticketing'de**
- Gateway: Hangi role hangi endpoint'e erisebilir (route bazli).
- Ticketing: Ownership kontrolu (bu event benim mi?).

**ADR-4: Redis Idempotency**
- Rate limiting icin zaten sistemde.
- Native TTL, atomic ops, restart-safe.
- JPA entity yerine Redis Hash.

**ADR-5: Optimistic Locking ile Oversell Korunmasi**
- `@Version` annotation, `OptimisticLockingFailureException` + retry.
- Pessimistic lock'a gore performansli, race condition'lari onler.

**ADR-6: Plain HTTP Headers (Gateway -> Downstream)**
- JJWT dependency Ticketing Service'te gerekmiyor.
- Trusted network varsayimi.
- `GatewayHeaders` constant sinifi ile standart header isimleri.

### OpenAPI Section

```
Swagger UI: http://localhost:8081/swagger-ui.html
OpenAPI JSON: http://localhost:8081/v3/api-docs
```

Tum istekler Gateway (port 8080) uzerinden yapilir, ama Swagger UI Ticketing Service'e dogrudan erismek icin port 8081 kullanir.

### Running Tests Section

```bash
# Tum testler
cd ticketing-common-library && mvn test
cd ticketing-auth && mvn test
cd ticketing-service && mvn test
cd ticketing-apigateway && mvn test

# Coverage raporu
cd ticketing-service && mvn test
# Rapor: target/site/jacoco/index.html
```

---

## 2. Alt Proje README'leri

Her alt proje icin kisa README.md olustur:

### ticketing-common-library/README.md
- Amac: Shared JAR
- Icerik: ApiResponse, Exception, JwtService, util siniflar
- Build: `mvn clean install`

### ticketing-auth/README.md
- Amac: User auth servisi
- Port: 8082
- Endpointler: register, login, logout, refresh
- Seed users
- Calistirma: `mvn spring-boot:run`

### ticketing-apigateway/README.md
- Amac: API Gateway
- Port: 8080
- Routing kurallari tablosu
- Rate limit kurallari
- On kosul: Redis
- Calistirma: `mvn spring-boot:run`

### ticketing-service/README.md
- Amac: Event + Reservation servisi
- Port: 8081
- Endpointler tablosu
- Swagger UI linki
- On kosul: CommonLibrary installed, Redis
- Calistirma: `mvn spring-boot:run`

---

## Kritik Kurallar

- Tum curl orneklerinde `http://localhost:8080` (Gateway) kullan (Swagger haric).
- Seed user sifreleri README'de acikca belirt.
- ADR section'da her karar icin "neden" aciklamasi yaz.
- README'de proje yapisini goster (tree).
- Markdown formatinda, temiz ve okunabilir.
