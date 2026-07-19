---
name: ticketing-tests
description: |-
  Skill 3 — Unit, integration, concurrency, security testleri; JaCoCo ~%70.
  Primary agent: Codex (test yazimi). Fable security test senaryolari; Grok eksik edge-case listesi.
  Multi-agent dagitim icin once ticketing-orchestrator kullan.
  TRIGGER when:
  - Test yazilmasi, test coverage arttirilmasi istendiginde
  - Unit test, integration test, concurrency test, security test istendiginde
  - JaCoCo coverage raporu istendiginde
  DO NOT TRIGGER when:
  - Uygulama kodu implement edilecekse (ticketing-base-infra veya ticketing-service-impl kullan)
  - README/dokumantasyon istendiginde (ticketing-readme kullan)
  - Sadece agent/gorev dagitimi soruluyorsa (ticketing-orchestrator kullan)
---

# Test Implementation (Skill 3)

Unit, integration, concurrency ve security testlerini implement eder. Her proje icin ayri test stratejisi.

> **Referans:** Mimari detaylar icin [ticket-plan.md](../../../ticket-plan.md) dosyasini oku.
> **Orchestration:** [ticketing-orchestrator](../ticketing-orchestrator/SKILL.md)
> **Guncel path'ler:** Client `/api/ticket/...`; downstream `/events`, `/auth`; headers `X-User-Id` + `X-Session-Id`.

## Agent Assignment

| Rol | Agent | Model | Bu skill'deki is |
|---|---|---|---|
| Primary test author | **Codex** | `gpt-5.2-codex` | Unit/integration/concurrency test kodu, MockMvc, JaCoCo plugin |
| Security test designer | **Fable** | `claude-fable-5-thinking-high` | Role bypass, JWT revoked session, gateway AuthZ negative cases (senaryo listesi; kodu Codex yazar) |
| Coverage brainstorm | **Grok** | `cursor-grok-4.5-high-fast` | Eksik edge-case / test isimleri listesi |

**Akis:** Grok veya Fable senaryo listesi (opsiyonel) → Codex testleri yazar → Fable security test bosluklarini kontrol eder.

---

## Test Stratejisi Ozeti

| Proje | Test Turu | Siniflar |
|---|---|---|
| ticketing-service | Unit | EventServiceTest, ReservationServiceTest, IdempotencyServiceTest |
| ticketing-service | Integration | EventControllerTest |
| ticketing-service | Concurrency | ReservationConcurrencyTest |
| ticketing-service | Security | SecurityIntegrationTest |
| ticketing-auth | Integration | AuthControllerTest, AuthServiceTest |
| ticketing-apigateway | Integration | RouteAuthorizationTest |

---

## 1. ticketing-service Unit Testler

Test konumu: `ticketing-service/src/test/java/com/turkcell/mayacore/ticketing/service/`

Test application.yml (`src/test/resources/application.yml`):

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
  jpa:
    hibernate.ddl-auto: create-drop
  data:
    redis:
      host: localhost
      port: 6379

security:
  jwt:
    secret: dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3Rz
    access-ttl-minutes: 5
    refresh-ttl-days: 1
```

### 1.1 EventServiceTest

```java
@ExtendWith(MockitoExtension.class)
```

Mock: `EventRepository`, `AuditService`
Inject: `EventService`

**Test case'ler:**
- `create_shouldCreateDraftEvent`: Verilen request ile event olusturur, `published=false`, `reservedSeats=0` dogrula.
- `update_shouldUpdateOwnedEvent`: Owner guncelleme yapabilir.
- `update_shouldThrow_whenNotOwner`: Baskasinin event'ini guncelleyemez (`BusinessException`).
- `update_shouldThrow_whenPublished`: Published event guncellenemez.
- `publish_shouldSetPublishedTrue`: Draft event basariyla publish edilir.
- `publish_shouldThrow_whenNotOwner`: Owner olmayan publish edemez.
- `list_withOwnerId_shouldFilterByOwner`: ownerId parametresi ile filtreleme calismali.
- `publicSearch_shouldReturnPublishedEvents`: Sadece published eventler donmeli.

### 1.2 ReservationServiceTest

```java
@ExtendWith(MockitoExtension.class)
```

Mock: `EventRepository`, `ReservationRepository`, `AuditService`
Inject: `ReservationService`

**Test case'ler:**
- `create_shouldCreatePendingReservation`: Kapasitesi yeterli event'e reservation olusturur.
- `create_shouldThrow_whenNotPublished`: Published olmayan event'e reservation olusturulamaz.
- `create_shouldThrow_whenCapacityExceeded`: Kapasite yetersizse `BusinessException`.
- `create_shouldIncrementReservedSeats`: reservedSeats alaninin dogru arttigini dogrula.
- `confirm_shouldSetStatusConfirmed`: PENDING -> CONFIRMED.
- `confirm_shouldThrow_whenNotOwner`: Baskasinin reservation'ini onaylayamaz.
- `confirm_shouldThrow_whenNotPending`: Sadece PENDING olan onaylanabilir.
- `cancel_shouldSetStatusCancelled`: PENDING/CONFIRMED -> CANCELLED.
- `cancel_shouldDecrementReservedSeats`: reservedSeats azalmali.
- `cancel_shouldThrow_whenAlreadyCancelled`: Zaten iptal olan tekrar iptal edilemez.

### 1.3 IdempotencyServiceTest

```java
@SpringBootTest
```

Redis gerektirir. Test'te embedded Redis veya `@MockBean StringRedisTemplate` kullanilabilir.

**Test case'ler:**
- `check_firstRequest_shouldReturnNull`: Ilk istek, PROCESSING olarak kaydedilir, null doner.
- `check_completedRequest_shouldReturnCachedResponse`: Ayni key + ayni hash ile tekrar istek, cached response doner.
- `check_differentHash_shouldThrow`: Ayni key + farkli hash, BusinessException.
- `check_processingRequest_shouldThrow`: PROCESSING durumundaki key, BusinessException.
- `complete_shouldSetCompletedWithResponse`: COMPLETED status + response body kaydedilir.
- `fail_shouldSetFailedStatus`: FAILED status set edilir.
- `check_failedRequest_shouldAllowRetry`: FAILED key icin retry izni, null doner.

---

## 2. ticketing-service Integration Testler

### 2.1 EventControllerTest

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
```

MockMvc kullanir. GatewayHeaderAuthFilter'in calisabilmesi icin request'lere `X-User-Id` ve `X-User-Roles` header'lari eklenir.

**Test case'ler:**
- `createEvent_shouldReturn201`: ORGANIZER header'lari ile event olusturma.
- `updateEvent_shouldReturn200`: Owner event guncelleyebilir.
- `publishEvent_shouldReturn200`: Draft event publish edilir.
- `listEvents_shouldReturn200`: Event listesi basariyla doner.
- `publicSearch_shouldReturn200_withoutAuth`: Auth header'i olmadan public search calismali.
- `createEvent_shouldReturn403_withoutUserHeader`: Header olmadan erisim reddedilir.

Her test `ApiResponse` formatini dogrular: `$.success`, `$.data`, `$.errorCode`.

### 2.2 SecurityIntegrationTest

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
```

**Test case'ler:**
- `request_withoutHeaders_shouldReturn401or403`: X-User-Id header'i olmadan korunmus endpoint'e erisim reddedilir.
- `request_withValidHeaders_shouldReturn200`: Dogru header'larla erisim saglanir.
- `publicEndpoint_shouldBeAccessibleWithoutHeaders`: `/api/events/public` header'siz erisilebilir.

---

## 3. Concurrency Testler

### 3.1 ReservationConcurrencyTest

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
```

Amac: Birden fazla thread ayni anda reservation olusturmaya calistiginda **oversell olmamali**.

**Senaryo:**
1. Capacity=5 olan bir published event olustur.
2. 10 thread baslat, her biri 1 koltuk reserve etmeye calissin.
3. `CountDownLatch` ile tum thread'leri ayni anda baslat.
4. Sonuc: En fazla 5 reservation basarili olmali, diger 5 `BusinessException` veya `OptimisticLockingFailureException` almali.
5. `event.getReservedSeats() <= event.getCapacity()` dogrulanmali.

```java
@Test
void concurrentReservations_shouldNotOversell() throws InterruptedException {
    // 1. Published event olustur (capacity=5)
    Event event = createPublishedEvent(5);

    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        long userId = 100 + i;
        executor.submit(() -> {
            try {
                latch.await();
                reservationService.create(userId, event.getId(), 1);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        });
    }

    latch.countDown(); // tum thread'leri ayni anda baslat
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);

    assertThat(successCount.get()).isLessThanOrEqualTo(5);
    Event updated = eventRepository.findById(event.getId()).orElseThrow();
    assertThat(updated.getReservedSeats()).isLessThanOrEqualTo(updated.getCapacity());
}
```

---

## 4. ticketing-auth Testler

### 4.1 AuthControllerTest

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
```

**Test case'ler:**
- `register_shouldReturn201_withValidInput`: Basarili kayit, AuthResponse donmeli.
- `register_shouldReturn400_withInvalidEmail`: Gecersiz email formati.
- `register_shouldReturn409_withDuplicateEmail`: Ayni email ile tekrar kayit.
- `login_shouldReturn200_withValidCredentials`: Basarili login, accessToken + refreshToken donmeli.
- `login_shouldReturn401_withWrongPassword`: Yanlis sifre.
- `refresh_shouldReturn200_withValidToken`: Gecerli refresh token ile yeni access token.
- `refresh_shouldReturn401_withExpiredToken`: Suresi dolmus refresh token.
- `logout_shouldReturn200_andInvalidateToken`: Logout sonrasi ayni refresh token kullanilamaz.

### 4.2 AuthServiceTest

```java
@ExtendWith(MockitoExtension.class)
```

Mock: `UserRepository`, `RefreshTokenRepository`, `JwtService`, `PasswordEncoder`

**Test case'ler:**
- `register_shouldHashPassword_andGenerateTokens`
- `login_shouldUpdateLastLoginAt`
- `refresh_shouldRotateRefreshToken`
- `logout_shouldDeleteRefreshToken`

---

## 5. ticketing-apigateway Testler

### 5.1 RouteAuthorizationTest

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
```

Gateway filter mantigi test edilir. `WebTestClient` kullanilir.

**Test case'ler:**
- `authEndpoint_shouldBeAccessibleWithoutToken`: `/api/auth/login` JWT olmadan erisilebilir.
- `eventsEndpoint_shouldReturn401_withoutToken`: `/api/events` JWT olmadan 401.
- `eventsEndpoint_shouldReturn403_forCustomer`: CUSTOMER rollu token ile `POST /api/events` 403.
- `eventsEndpoint_shouldReturn200_forOrganizer`: ORGANIZER rollu token ile `POST /api/events` basarili.
- `reservationEndpoint_shouldReturn403_forOrganizer`: ORGANIZER ile reservation olusturulamaz.
- `publicEndpoint_shouldBeAccessibleWithoutToken`: `GET /api/events/public` JWT olmadan erisilebilir.

---

## 6. JaCoCo Coverage

Her proje `pom.xml`'ine JaCoCo plugin ekle:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

Rapor: `mvn test` sonrasi `target/site/jacoco/index.html`

Hedef: Service siniflarinda ~%70 line coverage. Controller ve config siniflarinda zorunlu degil.

---

## Dogrulama

Her proje icin:
```bash
mvn clean test
```

Tum testler gecmeli. JaCoCo raporu uretilmeli.

---

## Kritik Kurallar

- Test'lerde `@MockitoBean` (Spring Boot 4) veya `@Mock` + `@ExtendWith(MockitoExtension.class)` kullan.
- `lenient()` kullanma, strict stubbing tercih et.
- Given-When-Then yapisinda yaz.
- AssertJ (`assertThat`) tercih et.
- Concurrency test'inde `CountDownLatch` ile thread'leri senkronize et.
- Integration test'lerde H2 in-memory DB kullan (`create-drop`).
- Gateway test'lerinde `WebTestClient` kullan (reactive).
