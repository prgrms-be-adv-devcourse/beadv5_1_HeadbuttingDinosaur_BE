# 🤖 DevTicket PR Review Agent

> 이 문서는 Codex가 PR 리뷰를 수행할 때 참조하는 프로젝트 규칙 및 리뷰 기준 명세입니다.
> 모든 리뷰는 아래 규칙을 기반으로 수행하며, 위반 사항은 구체적 근거와 함께 코멘트합니다.
> 모든 리뷰는 한국어로 작성합니다.

---

## 1. 프로젝트 개요

- **프로젝트명:** DevTicket — 개발자 이벤트 티켓 커머스 플랫폼
- **아키텍처:** Saga 기반 마이크로서비스 아키텍처 (MSA)
- **DB 전략:** Shared Database Pattern (단일 PostgreSQL, 논리적 스키마 격리)
- **언어/프레임워크:** Java 17+, Spring Boot, Spring Cloud Gateway, Python 3.11+, FastAPI
- **비동기 메시징:** Apache Kafka
- **코드 스타일:** Google Java Style 기반 + 프로젝트 커스텀 규칙

### 서비스 목록

| 서비스 | 루트 패키지 | 역할 |
|---|---|---|
| Gateway | `com.devticket.gateway` | JWT 검증, 라우팅, Rate Limiting |
| Member | `com.devticket.member` | 회원/인증/프로필 관리 |
| Event | `com.devticket.event` | 이벤트 CRUD, 재고(잔여석) 관리 |
| Commerce | `com.devticket.commerce` | 장바구니, 주문, 티켓 발급 |
| Payment | `com.devticket.payment` | PG(토스페이먼츠)/예치금 결제, 환불 |
| Settlement | `com.devticket.settlement` | 정산 내역 생성, 수수료 계산 |
| Log | `com.devticket.log` | 행동 로그 수집 (비동기) |
| Admin | `com.devticket.admin` | 운영 관리 전반 |
| AI | `app.*` (FastAPI) | 임베딩 생성, 개인화 추천, 인기 이벤트 fallback |

---

## 2. 아키텍처 및 계층 규칙

### 2.1 패키지 구조 (Layered DDD)

```
com.devticket.{서비스}
├── presentation        // 외부 요청 수신 계층
│   ├── controller      // REST API Controller
│   ├── consumer        // Kafka Listener
│   └── dto             // Request / Response DTO
├── application         // 비즈니스 유스케이스 흐름 제어
│   └── service         // @Transactional 비즈니스 로직
├── domain              // 도메인 계층
│   ├── model           // JPA Entity + 도메인 로직 (통합)
│   ├── repository      // Spring Data JPA Repository 인터페이스
│   └── exception       // 도메인 비즈니스 예외
└── infrastructure      // 기술 구현체
    ├── messaging       // Kafka Producer
    ├── client          // OpenFeign Client (타 서비스 호출)
    ├── external        // 외부 연동 (S3, PG사 등)
    └── config          // Spring 설정 (Security, Kafka, DB 등)
```

> **설계 결정:** 본 프로젝트는 도메인 모델과 JPA 엔티티를 통합하여 `domain.model`에 `@Entity`를 직접 선언한다.
> 별도의 `infrastructure.persistence` 패키지 및 변환 메서드(`from()`/`toDomain()`)는 사용하지 않는다.
>
> **파이널 확장 결정:** 추천 서버(AI)는 Python/FastAPI로 독립 배포한다. Java 서비스 규칙을 그대로 강제하지 않고,
> FastAPI 계층 분리(`router → service → repository/client`)와 비즈니스 규칙 캡슐화를 동일 수준으로 검증한다.

### 2.2 계층 간 의존 규칙 — ⚠️ 최우선 검증 대상

```
presentation → application → domain ← infrastructure
```

**위반 시 반드시 리뷰 코멘트를 남겨야 하는 규칙:**

| 규칙 | 위반 사례 |
|---|---|
| Controller는 Service만 호출 | Controller에서 Repository 직접 접근 |
| Service는 domain의 Entity, Repository 사용 | Service에서 infrastructure 패키지 직접 의존 |
| infrastructure → domain 방향 구현 | domain 패키지가 infrastructure에 의존 |
| domain.model에 비즈니스 로직 포함 | 상태 변경 로직이 Service에만 존재하고 Entity에 없음 |
| Service에서 다른 Service 직접 주입 금지 | `@Autowired OtherService` — Kafka 또는 Internal API 사용해야 함 |

---

## 3. 코드 컨벤션 검증 기준

### 3.1 네이밍 규칙

**클래스 네이밍:**

| 계층 | 접미사 | 올바른 예시 | 잘못된 예시 |
|---|---|---|---|
| Controller | `Controller` | `MemberController` | `MemberCtrl` |
| Service | `Service` | `MemberService` | `MemberSvc` |
| Repository | `Repository` | `MemberRepository` | `MemberRepo` |
| Entity (domain.model) | 접미사 없음 | `Member`, `Event`, `Order` | `MemberEntity`, `MemberDomain` |
| Request DTO | `Request` | `SignupRequest` | `SignupReq`, `SignupDTO` |
| Response DTO | `Response` | `MemberInfoResponse` | `MemberInfoRes` |
| Kafka Producer | `Producer` 또는 `EventPublisher` | `PaymentEventProducer` | |
| Kafka Consumer | `Consumer` 또는 `EventListener` | `PaymentCompletedConsumer` | |
| Exception | `Exception` | `MemberNotFoundException` | `MemberNotFound` |
| Config | `Config` | `SecurityConfig` | `SecurityConfiguration` |
| Feign Client | `Client` | `MemberInternalClient` | |

**메서드 네이밍:**

| 동작 | 접두사 | 예시 |
|---|---|---|
| 조회 (단건) | `get` 또는 `find` | `getMember()`, `findByEmail()` |
| 조회 (목록) | `get` + 복수형 또는 `findAll` | `getEvents()`, `findAllByStatus()` |
| 생성 | `create` | `createOrder()` |
| 수정 | `update` | `updateProfile()` |
| 삭제 | `delete` 또는 `remove` | `deleteCartItem()` |
| 검증 | `validate` | `validatePurchase()` |
| 변환 | `toDto` 또는 `from` | `toResponse()`, `from(entity)` |
| 존재 확인 | `exists` | `existsByEmail()` |
| 상태 변경 | 동사 + 상태 | `suspend()`, `approve()`, `cancel()` |

**변수 네이밍:**

- camelCase 사용
- boolean은 `is` 접두사: `isActive`, `isPurchasable`
- 컬렉션은 복수형: `events`, `orderItems`
- **약어 금지:** `quantity` (O) / `qty` (X), `description` (O) / `desc` (X)
- 상수는 `UPPER_SNAKE_CASE`

### 3.2 포맷팅 규칙

- **들여쓰기:** 스페이스 4칸 (탭 금지)
- **연속 줄바꿈 들여쓰기:** 스페이스 8칸
- **최대 줄 길이:** 120자
- **중괄호:** K&R 스타일 (같은 줄에서 시작), 한 줄이라도 중괄호 필수
- **와일드카드 import 금지:** `import java.util.*;` → 위반

**import 정렬 순서:**
1. `java.*`
2. `javax.*` / `jakarta.*`
3. 외부 라이브러리 (`org.springframework.*` 등)
4. 프로젝트 내부 (`com.devticket.*`)
5. static import는 맨 아래

### 3.3 Lombok 사용 규칙

| 어노테이션 | 허용 여부 | 사용 위치 |
|---|---|---|
| `@Getter` | ✅ 허용 | Entity, DTO |
| `@RequiredArgsConstructor` | ✅ 허용 | Service, Controller |
| `@NoArgsConstructor(access = PROTECTED)` | ✅ 허용 | Entity (JPA 필수) |
| `@Builder` | ✅ 허용 | Entity (private 생성자와 함께) |
| `@Setter` | ❌ **금지** | — |
| `@Data` | ❌ **금지** | — |
| `@AllArgsConstructor` | ❌ **금지** | — |
| `@ToString` | ⚠️ 주의 | 연관 관계 필드 제외 필수 |

→ `@Setter`, `@Data`, `@AllArgsConstructor` 발견 시 반드시 지적

---

## 4. 계층별 코드 작성 규칙

### 4.1 Controller (presentation)

- Controller에는 비즈니스 로직 금지 (Service에 위임만)
- `@Valid`로 요청 DTO 검증
- 적절한 HTTP 상태 코드 반환 (201 Created, 204 No Content 등)
- Swagger 어노테이션 필수: `@Tag`, `@Operation`, `@ApiResponse`

**리뷰 체크리스트:**
- [ ] Controller에 if/else 분기나 비즈니스 로직이 있는가?
- [ ] `@Valid` 어노테이션이 Request DTO에 적용되었는가?
- [ ] HTTP 상태 코드가 적절한가? (생성 → 201, 삭제 → 204 등)
- [ ] Swagger 어노테이션이 누락되지 않았는가?

### 4.2 Service (application)

- 클래스 레벨에 `@Transactional(readOnly = true)` 기본 적용
- 쓰기 작업 메서드에만 `@Transactional` 오버라이드
- 비즈니스 검증 로직은 private 메서드로 분리
- **다른 Service 직접 주입 금지** → Kafka 또는 Internal API(Feign) 사용

**리뷰 체크리스트:**
- [ ] `@Transactional(readOnly = true)`가 클래스 레벨에 적용되었는가?
- [ ] 쓰기 메서드에만 `@Transactional`이 오버라이드되었는가?
- [ ] 다른 서비스의 Service 클래스를 직접 주입하고 있지 않은가?
- [ ] 검증 로직이 private 메서드로 분리되었는가?

### 4.3 Entity (domain.model) — 도메인 모델 + JPA 통합

도메인 모델과 JPA 엔티티를 하나로 통합한다. `domain.model` 패키지에 `@Entity`를 직접 선언하되, 비즈니스 로직도 함께 포함한다.

**필수 규칙:**
- `@Entity`, `@Table`, `@Column`, `@Enumerated` 등 JPA 어노테이션 사용
- `@Enumerated(EnumType.STRING)` 필수 (`ORDINAL` 금지)
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 필수
- `@Setter` 금지 — 상태 변경은 반드시 도메인 메서드를 통해서만
- 생성은 정적 팩토리 메서드 사용 (`create()`, `of()`)
- 도메인 비즈니스 규칙 검증은 엔티티 내부에서 처리
- `from()`/`toDomain()` 변환 메서드 불필요 (별도 persistence 엔티티 없음)

**리뷰 체크리스트:**
- [ ] `@Setter`를 통한 상태 변경이 있는가?
- [ ] 정적 팩토리 메서드를 사용하고 있는가?
- [ ] `@Enumerated(EnumType.ORDINAL)` 사용 여부
- [ ] `@NoArgsConstructor(access = PROTECTED)` 적용되었는가?
- [ ] 비즈니스 로직(상태 전이, 검증)이 엔티티에 포함되어 있는가?
- [ ] `infrastructure.persistence` 패키지에 별도 엔티티를 만들고 있지 않은가?

### 4.4 DTO (presentation)

- Java Record 사용 권장
- Request DTO에 Bean Validation 어노테이션 필수
- Response DTO에 `from()` 정적 팩토리 메서드로 Entity → DTO 변환
- DTO에 비즈니스 로직 금지

### 4.5 Exception (domain)

- 모든 비즈니스 예외는 `BusinessException` 상속
- 예외 처리 정책 문서의 에러 코드와 연결 필수
- 예외 메시지에 디버깅에 필요한 정보 포함

---

## 5. 비즈니스 도메인 검증 규칙

### 5.1 에러 코드 체계

에러 코드는 `{서비스}_{번호}` 형식을 따른다. PR에서 새 에러 코드를 추가하는 경우, 기존 코드와 번호가 겹치지 않는지 확인한다.

| 접두사 | 서비스 |
|---|---|
| COMMON | 공통 (유효성 검증, 인증/인가) |
| MEMBER | 회원 |
| EVENT | 이벤트 |
| CART | 장바구니 |
| ORDER | 주문 |
| PAYMENT | 결제 |
| WALLET | 예치금 |
| REFUND | 환불 |
| SETTLEMENT | 정산 |
| ADMIN | 어드민 |

### 5.2 HTTP 상태 코드 사용 기준

| 상태 코드 | 의미 | 사용 상황 |
|---|---|---|
| 200 | OK | 정상 처리 |
| 201 | Created | 리소스 생성 성공 (회원가입, 이벤트 생성, 주문 생성) |
| 204 | No Content | 삭제 성공 (장바구니 삭제, 로그아웃) |
| 400 | Bad Request | 유효성 검증 실패, 잘못된 파라미터 |
| 401 | Unauthorized | 인증 실패 (토큰 없음, 만료, 위조) |
| 403 | Forbidden | 권한 없음 |
| 404 | Not Found | 리소스 없음 |
| 409 | Conflict | 충돌 (이메일 중복, 재고 부족, 중복 주문) |
| 500 | Internal Server Error | 서버 내부 오류 |
| 502 | Bad Gateway | 외부 서비스 연동 실패 (PG사, Google OAuth) |
| 503 | Service Unavailable | 서비스 일시 불가 |

### 5.3 서비스 정책 상수값 — 하드코딩 체크

PR에서 아래 값이 매직 넘버로 하드코딩되어 있으면 지적한다. 상수로 관리되어야 한다.

| 상수 | 값 | 설명 |
|---|---|---|
| `WALLET_MIN_CHARGE` | 1,000 | 최소 충전 금액 |
| `WALLET_MAX_CHARGE` | 50,000 | 최대 충전 금액 |
| `WALLET_DAILY_LIMIT` | 1,000,000 | 일일 충전 한도 |
| `EVENT_MIN_PRICE` | 0 | 최소 이벤트 가격 |
| `EVENT_MAX_PRICE` | 9,999,999 | 최대 이벤트 가격 |
| `EVENT_MIN_CAPACITY` | 5 | 최소 이벤트 인원 |
| `EVENT_MAX_CAPACITY` | 9,999 | 최대 이벤트 인원 |
| `EVENT_MAX_IMAGES` | 2 | 이미지 최대 업로드 수 |
| `REFUND_FULL_DEADLINE_DAYS` | 7 | 100% 환불 마감 (행사 N일 전) |
| `REFUND_HALF_DEADLINE_DAYS` | 3 | 50% 환불 마감 (행사 N일 전) |
| `SETTLEMENT_FEE_RATE` | 5 | 플랫폼 수수료율 (%) |
| `SETTLEMENT_MIN_AMOUNT` | 10,000 | 최소 정산 금액 |
| `NICKNAME_MIN_LENGTH` | 2 | 닉네임 최소 길이 |
| `NICKNAME_MAX_LENGTH` | 12 | 닉네임 최대 길이 |
| `PASSWORD_MIN_LENGTH` | 8 | 비밀번호 최소 길이 |
| `PASSWORD_MAX_LENGTH` | 20 | 비밀번호 최대 길이 |
| `ACCESS_TOKEN_TTL_MINUTES` | 30 | Access Token 유효 시간 |
| `REFRESH_TOKEN_TTL_DAYS` | 7 | Refresh Token 유효 시간 |

---

## 6. Kafka / 비동기 이벤트 검증

### 6.1 토픽 목록 및 Producer/Consumer 매핑

| 토픽명 | Producer | Consumer(s) |
|---|---|---|
| `payment.completed` | Payment | Commerce, Event, Log |
| `refund.completed` | Payment | Commerce, Event, Payment(Wallet) |
| `event.force-cancelled` | Event | Payment |
| `event.sale-stopped` | Event | Payment |
| `member.suspended` | Member | Commerce, Member(Token) |
| `action.log` | 각 Service | Log |
| `order.created` | Commerce | Event |
| `stock.deducted` | Event | Payment |
| `stock.failed` | Event | Commerce |
| `payment.failed` | Payment | Event, Commerce |

**리뷰 체크리스트:**
- [ ] 새로운 Kafka 토픽을 사용하는 경우, 토픽명이 위 목록과 일치하는가?
- [ ] Producer/Consumer 매핑이 설계와 일치하는가?
- [ ] Consumer 실패 시 재시도 로직(최대 3회) 및 DLQ 처리가 구현되었는가?
- [ ] `action.log` 저장 실패가 핵심 기능에 영향을 주지 않는가?

### 6.2 Saga 보상 트랜잭션 검증

티켓 예매 흐름 (핵심 비즈니스 플로우):

```
주문 생성(Commerce) → 재고 선점(Event) → 결제 처리(Payment) → 최종 확정(Commerce)
```

**보상 트랜잭션 대원칙:** "내 도메인에서 DB 상태를 변경했는데, 다음 순서의 도메인에서 에러가 나면 반드시 보상 트랜잭션(Rollback)을 수행해야 한다."

| 실패 지점 | 보상 처리 |
|---|---|
| 재고 부족 (`stock.failed`) | 주문 상태 → CANCELED |
| 결제 실패 (`payment.failed`) | 재고 원복(+N), 주문 상태 → CANCELED |
| 환불 완료 (`refund.completed`) | 티켓 상태 → REFUNDED, 재고 복구 |

**리뷰 체크리스트:**
- [ ] 결제 관련 코드에서 실패 시 보상 이벤트를 발행하는가?
- [ ] 재고 차감에 비관적 락(Pessimistic Lock)을 사용하는가?

### 6.3 AI 추천 / ElasticSearch 검증 (파이널 필수)

- [ ] `action.log` 기반 추천에서 신규 유저 fallback(인기 이벤트)이 구현되어 있는가?
- [ ] 이벤트 임베딩 모델이 `text-embedding-3-small` 기준(1536차원)과 일치하는가?
- [ ] ES 인덱스 매핑에 `dense_vector`와 검색용 텍스트 필드(BM25 대상)가 모두 정의되어 있는가?
- [ ] AI 서비스 장애 시 기존 주문/결제 핵심 플로우에 영향이 없도록 실패 격리(fail-soft) 처리되어 있는가?
- [ ] 추천/검색 인덱스 동기화 실패 시 재시도 또는 운영 추적 가능한 로그/알람이 존재하는가?

---

## 7. 보안 검증

### 7.1 필수 보안 규칙

- [ ] **민감 정보 로그 출력 금지:** 비밀번호, 토큰, API 키, 결제 정보가 로그에 출력되지 않는가?
- [ ] **SQL 인젝션 방지:** 네이티브 쿼리에서 문자열 연결 대신 파라미터 바인딩(`@Param`)을 사용하는가?
- [ ] **AWS 키 유출 방지:** `.env`, `application.yml` 등에 시크릿 키가 하드코딩되어 있지 않은가?
- [ ] **`System.out.println()` 금지:** SLF4J(`@Slf4j`)를 사용하는가?
- [ ] **인증 관련 에러 메시지:** "비밀번호가 틀렸습니다" 같은 구체적 사유 대신 "이메일 또는 비밀번호가 일치하지 않습니다"로 통일되어 있는가?

### 7.2 인증/인가 구조

- JWT 기반 인증 (Access Token 30분, Refresh Token 7일)
- API Gateway에서 JWT 검증 후 `X-User-Id`, `X-User-Role` 등을 Header로 전달
- 각 서비스는 Gateway가 전달한 Header 기반으로 인가 처리

**리뷰 시 확인:**
- [ ] 권한이 필요한 API에 적절한 권한 검증이 있는가? (USER / SELLER / ADMIN)
- [ ] Internal API(`/internal/**`)에 외부 접근이 차단되는가?

---

## 8. 테스트 규칙 검증

### 8.1 테스트 클래스 네이밍

```
{대상클래스}Test               → 단위 테스트
{대상클래스}IntegrationTest    → 통합 테스트
```

### 8.2 테스트 메서드 네이밍

- 한글 메서드명 허용 (테스트에 한해)
- 예시: `이메일_중복시_회원가입_실패()`, `행사_7일전_환불시_100퍼센트_환불()`

### 8.3 테스트 구조

- Given-When-Then 패턴 필수
- 주석으로 `// given`, `// when`, `// then` (또는 `// when & then`) 구분

### 8.4 테스트 어노테이션

| 테스트 유형 | 어노테이션 |
|---|---|
| 단위 테스트 (Service) | `@ExtendWith(MockitoExtension.class)` |
| 통합 테스트 | `@SpringBootTest` |
| Repository 테스트 | `@DataJpaTest` |
| Controller 테스트 | `@WebMvcTest` |

**리뷰 체크리스트:**
- [ ] 새 기능에 대한 테스트가 존재하는가?
- [ ] 테스트 메서드명이 테스트 의도를 명확히 드러내는가?
- [ ] Given-When-Then 구조를 따르는가?

---

## 9. Git / PR 컨벤션 검증

### 9.1 브랜치 네이밍

```
{type}/{서비스명}-{기능 또는 이슈}
```

type: `feat`, `fix`, `hotfix`

예시: `feat/member-signup`, `fix/payment-refund-calculation`, `hotfix/payment-duplicate-charge`

### 9.2 커밋 메시지

```
{type}({서비스}): {설명}
```

| type | 용도 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 |
| `test` | 테스트 |
| `docs` | 문서 |
| `chore` | 빌드/설정 |
| `style` | 포맷팅 |
| `perf` | 성능 개선 |

서비스명: `gateway`, `member`, `event`, `commerce`, `payment`, `settlement`, `log`, `admin`, `ai`

### 9.3 PR 제목

```
[{서비스}] {type}: {설명}
```

예시: `[Member] feat: 회원가입 Step 1 API 구현`

**리뷰 체크리스트:**
- [ ] PR 제목이 컨벤션을 따르는가?
- [ ] PR 본문에 작업 내용, 변경 사항, 테스트 여부가 기술되었는가?
- [ ] 관련 이슈가 연결되어 있는가? (`close #이슈번호`)

---

## 10. 기타 코드 품질 규칙

### 10.1 null 처리

- `Optional` 사용 (null 직접 반환 금지)
- 조회 결과 없을 때 예외 던지기: `.orElseThrow(() -> new NotFoundException(...))`

### 10.2 로그

- `@Slf4j` + SLF4J 사용 (`System.out.println()` 금지)
- 적절한 로그 레벨: DEBUG, INFO, WARN, ERROR
- 민감 정보 로그 출력 금지

### 10.3 주석

- 클래스/메서드 설명은 JavaDoc
- 코드 내 주석은 "왜(Why)"를 설명 ("무엇(What)"은 코드로 표현)

### 10.4 SQL

- 네이티브 쿼리에서 문자열 연결 금지 (SQL 인젝션 방지)
- 반드시 파라미터 바인딩 사용 (`@Param`)
- `@Enumerated(EnumType.STRING)` 필수 (`ORDINAL` 금지)

### 10.5 Enum

- Enum 값은 `UPPER_SNAKE_CASE`
- Enum 정의서 문서의 값과 반드시 일치
- 새 Enum 값 추가 시 정의서 문서와 일관성 확인

---

## 11. Enum / 상태값 정의 (레퍼런스)

PR에서 새로운 Enum이나 상태값을 사용하는 경우, 아래 정의와 일치하는지 반드시 검증한다.

### UserRole
`USER`, `SELLER`, `ADMIN`

### UserStatus
`ACTIVE`, `SUSPENDED`, `WITHDRAWN`

### ProviderType
`LOCAL`, `GOOGLE`

### Position
`BACKEND`, `FRONTEND`, `FULLSTACK`, `DEVOPS`, `AI_ML`, `MOBILE`, `OTHER`

### SellerApplicationStatus
`PENDING`, `APPROVED`, `REJECTED`

### EventStatus
`DRAFT`, `ON_SALE`, `SOLD_OUT`, `SALE_ENDED`, `CANCELLED`, `FORCE_CANCELLED`

### EventCategory
`MEETUP`, `CONFERENCE`

### OrderStatus
`CREATED`, `PAYMENT_PENDING`, `PAID`, `CANCELLED`, `REFUND_PENDING`, `REFUNDED`, `FAILED`

### TicketStatus
`ISSUED`, `CANCELLED`, `REFUNDED`

### PaymentMethod
`WALLET`, `PG`

### PaymentStatus
`READY`, `SUCCESS`, `FAILED`, `CANCELLED`, `REFUNDED`

### WalletTransactionType
`CHARGE`, `USE`, `REFUND`, `WITHDRAW`

### RefundStatus
`REQUESTED`, `APPROVED`, `REJECTED`, `COMPLETED`, `FAILED`

### SettlementStatus
`PENDING`, `COMPLETED`, `FAILED`

### ActionType
`VIEW`, `DETAIL_VIEW`, `CART_ADD`, `CART_REMOVE`, `PURCHASE`, `DWELL_TIME`

---

## 12. 리뷰 코멘트 작성 가이드

### 심각도 분류

리뷰 코멘트에는 아래 접두사를 사용하여 심각도를 표시한다:

| 접두사 | 의미 | 예시 |
|---|---|---|
| `🚨 [CRITICAL]` | 반드시 수정 필요 (아키텍처 위반, 보안 취약점, 데이터 정합성 위험) | 별도 persistence 엔티티 생성, SQL 인젝션 가능성 |
| `⚠️ [WARNING]` | 수정 권장 (컨벤션 위반, 잠재적 버그) | `@Setter` 사용, `@Transactional` 누락, 매직 넘버 |
| `💡 [SUGGESTION]` | 개선 제안 (코드 품질, 가독성) | 메서드 분리, 네이밍 개선, 테스트 보강 |
| `❓ [QUESTION]` | 의도 확인 필요 | 의도적인 설계 선택인지 확인 |

### 코멘트 포맷

```
{접두사} {위반 내용 한 줄 요약}

**근거:** {프로젝트 컨벤션 또는 설계 문서의 관련 규칙}
**현재 코드:** {문제가 되는 코드 스니펫 또는 설명}
**수정 제안:** {올바른 코드 또는 방향}
```

예시:

```
🚨 [CRITICAL] 별도 persistence 엔티티 생성 — 프로젝트 구조 위반

**근거:** 아키텍처 결정 — "도메인 모델과 JPA 엔티티를 domain.model에 통합, infrastructure.persistence 패키지 사용 안 함"
**현재 코드:** `infrastructure.persistence.MemberEntity`가 별도로 존재하고 `from()`/`toDomain()` 변환을 사용하고 있습니다.
**수정 제안:** `domain.model.Member`에 `@Entity`를 직접 선언하고, 비즈니스 로직과 JPA 매핑을 하나의 클래스에서 처리하세요.
```

---

## 13. 리뷰 우선순위

PR 리뷰 시 아래 순서대로 검증한다:

1. **아키텍처 위반** — 계층 의존 규칙, 패키지 구조
2. **보안 취약점** — 민감 정보 노출, SQL 인젝션, 키 유출
3. **비즈니스 로직 정합성** — 상태 전이, 에러 코드, 정책 상수값
4. **Kafka / Saga 규칙** — 토픽 매핑, 보상 트랜잭션, DLQ 처리
5. **코드 컨벤션** — 네이밍, Lombok, 포맷팅
6. **테스트** — 존재 여부, 구조, 커버리지
7. **Git 컨벤션** — PR 제목, 커밋 메시지, 브랜치명
