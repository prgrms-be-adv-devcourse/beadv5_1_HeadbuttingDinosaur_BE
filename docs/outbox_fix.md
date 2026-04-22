# Outbox 정합 작업 

> **결정일**: 2026-04-22
> **기준**: `kafka-design.md §4 Outbox 패턴` + 2026-04-22 모듈별 통합표 결정값
> **목적**: Commerce / Event / Payment 3모듈의 Outbox 구현을 단일 정책으로 수렴시키기 위한 모듈별 수정 작업 체크리스트 + **Frontend N1 상태머신 연동 작업**

---

## 1. 결정 근거

- 설계문서: `kafka-design.md §4` (지수 6회 / ShedLock 5m·5s / Producer 타임아웃 정합 / 쿼리 `<` 연산자)
- 통합표 (2026-04-22): 모듈별 현재 코드 vs 결정 사항 전수 비교
- Commerce를 **기준안(reference implementation)** 으로 채택 — 현 코드가 설계값과 가장 근접

---

## 2. 통합 결정값 요약 (3모듈 공통)

| 영역 | 결정값 |
|---|---|
| Outbox `@Table(schema=...)` | 각 모듈 schema명 (`commerce` / `event` / `payment`) |
| `messageId` 타입 | `String` (UUID.toString()) |
| `OutboxService.save()` 시그니처 | `(aggregateId, partitionKey, eventType, topic, event)` — Commerce 기준 순서 |
| `OutboxService.save()` `@Transactional` | `propagation = MANDATORY` (외부 트랜잭션 강제) |
| `OutboxService.processOne(Outbox)` | **별도 빈 메서드** (Scheduler 루프에서 호출) |
| Producer 메서드 시그니처 | `publish(OutboxEventMessage msg): void throws OutboxPublishException` |
| `.get()` 타임아웃 (앱) | **2s** |
| `delivery.timeout.ms` | **1500** |
| `request.timeout.ms` | **1000** |
| `max.block.ms` | **500** |
| `acks` / `enable.idempotence` | `all` / `true` |
| `@Scheduled(fixedDelay)` | `3_000` (3초) |
| `@SchedulerLock(lockAtMostFor)` | `"5m"` |
| `@SchedulerLock(lockAtLeastFor)` | `"5s"` |
| Scheduler 트랜잭션 경계 | **루프만** — 건별 트랜잭션은 `processOne()` 별도 빈에서. 장기 `@Transactional` 금지, self-invocation 금지 |
| Repository 메서드명 | `findPendingToPublish` |
| Repository 쿼리 연산자 | `nextRetryAt < :now` (경계 배제 — `== now`는 다음 틱에서 픽업) |
| Repository LIMIT | `50` |
| 재시도 최대 횟수 | **6회 지수** (`2^(retryCount-1)초` = 즉시/1/2/4/8/16s, 누적 31초) |
| 실패 메서드명 | `markFailed()` (상수 내부화 — 파라미터로 주입 금지) |
| `retryCount >= 6` 도달 시 | `FAILED` 전환 + 재시도 중단 |
| `X-Message-Id` 헤더 세팅 | 필수 (Outbox `messageId` 그대로 Kafka 헤더로 전달) |

---

## 3. 모듈별 작업 체크리스트

### 3-A. Commerce (기준안)

> 대부분 변경 없음. 정책 변경에 따른 문서 동기화만 확인.

- [ ] **검증만**: 현 구현이 `2.` 통합 결정값과 일치하는지 회귀 점검
  - `Outbox @Table(schema="commerce")` ✅
  - `messageId = String(UUID.toString())` ✅
  - `save(aggregateId, partitionKey, eventType, topic, event) + @Transactional(MANDATORY)` ✅
  - `publish(msg): void throws OutboxPublishException` ✅
  - `.get(2s)` ✅
  - `fixedDelay=3s + lockAtMostFor=5m + lockAtLeastFor=5s` ✅ (lockAtMostFor는 본 결정 반영 시점 확인 필요)
  - `findPendingToPublish` + `< :now` ✅
  - 지수 6회 + `markFailed()` (상수) ✅
  - `processOne()` 별도 빈 ✅
- [ ] **신규 적용 필요**: ProducerConfig 3종 타임아웃
  - `delivery.timeout.ms = 1500` (미설정 상태 → 명시)
  - `request.timeout.ms = 1000` (미설정 상태 → 명시)
  - `max.block.ms = 500` (미설정 상태 → 명시)

---

### 3-B. Event

#### 필수 수정

- [ ] **Producer 메서드 시그니처 통일**
  - 현재: `publish(msg): boolean`
  - 변경: `publish(OutboxEventMessage msg): void throws OutboxPublishException`
  - 근거: boolean 반환은 **호출부에서 실패를 놓치기 쉬움** + Commerce와 예외 전파 방식 불일치
- [ ] **`.get()` 타임아웃 축소**
  - 현재: `5s`
  - 변경: `2s`
- [ ] **ProducerConfig 타임아웃 3종 추가**
  - `delivery.timeout.ms = 1500`
  - `request.timeout.ms = 1000`
  - `max.block.ms = 500`
- [ ] **`@SchedulerLock(lockAtMostFor)` 확장**
  - 현재: `"30s"`
  - 변경: `"5m"`
  - 근거: `kafka-design.md §4 lockAtMostFor="5m"` — 최악 처리 시간(50건×건당 2s) 대비 안전계수 3배
- [ ] **`@SchedulerLock(lockAtLeastFor)` 확인/세팅**
  - 현재: `"5s"` 또는 미설정 (확인 필요)
  - 변경: `"5s"` 확정 세팅
- [ ] **Scheduler self-invocation 버그 수정**
  - 현재: `processOutbox()` 같은 클래스 내부에 `@Transactional` 선언 → **self-invocation으로 AOP 미적용 (무효)**
  - 변경: Commerce 패턴 채택 — Scheduler는 **루프만**, 건별 처리는 `OutboxService.processOne(Outbox)` 별도 빈에 위임
  - 근거: Scheduler가 같은 클래스 private 메서드 호출 시 Spring AOP 프록시 우회 → 트랜잭션 경계 무효
- [ ] **Repository 메서드명 통일**
  - 현재: `findPendingOutboxes`
  - 변경: `findPendingToPublish`
- [ ] **쿼리 연산자 통일**
  - 현재: `<= :now`
  - 변경: `< :now`
- [ ] **실패 메서드 시그니처 단순화**
  - 현재: `markFailed(maxRetries)` — 파라미터 주입
  - 변경: `markFailed()` — 상수(`MAX_RETRY = 6`) 엔티티 내부화

---

### 3-C. Payment

#### 필수 수정

- [ ] **Outbox `@Table(schema="payment")` 지정**
  - 현재: schema 미지정
  - 변경: `@Table(name = "outbox", schema = "payment")`
- [ ] **`messageId` 타입 변경**
  - 현재: `UUID`
  - 변경: `String` (`UUID.toString()` 저장 — 3모듈 통일)
  - 영향 범위: 엔티티 필드 / DB 컬럼 타입(VARCHAR(36)) / 호출부 / Kafka 헤더 세팅
- [ ] **`OutboxService.save()` 시그니처 재정렬**
  - 현재: `(aggregateId, eventType, topic, partitionKey, event)` (파라미터 순서 상이)
  - 변경: `(aggregateId, partitionKey, eventType, topic, event)` — Commerce 기준
  - 근거: 3모듈 동일 계약으로 통일해야 호출부 복사·참조·교차 검토 용이
- [ ] **`OutboxService.save()` 전파 속성 지정**
  - 현재: 전파 미지정 (기본 `REQUIRED`)
  - 변경: `@Transactional(propagation = Propagation.MANDATORY)`
  - 근거: Outbox 단일 트랜잭션 경계 강제 — 외부 `@Transactional` 없이 호출되면 **비즈니스 DB 커밋과 Outbox 커밋 분리** 위험 차단
- [ ] **Producer 메서드 시그니처 통일**
  - 현재: `send(topic, key, msg): void throws ...`
  - 변경: `publish(OutboxEventMessage msg): void throws OutboxPublishException`
- [ ] **`.get()` 타임아웃 세팅**
  - 현재: 타임아웃 없음 (무한 대기)
  - 변경: `.get(2, TimeUnit.SECONDS)` — ProducerConfig 타임아웃 3종과 정합
- [ ] **ProducerConfig 타임아웃 3종 추가**
  - `delivery.timeout.ms = 1500`
  - `request.timeout.ms = 1000`
  - `max.block.ms = 500`
- [ ] **`@SchedulerLock(lockAtMostFor)` 확장**
  - 현재: `"30s"`
  - 변경: `"5m"`
- [ ] **Scheduler 장기 트랜잭션 제거 + Commerce 패턴 채택**
  - 현재: Scheduler 메서드 **전체**에 `@Transactional` — 50건 × 2s = 100초간 트랜잭션 유지 → **DB 커넥션 점유·락 홀딩·장애 전파** 위험
  - 변경: Scheduler는 **루프만** (트랜잭션 없음), 건별 처리는 `OutboxService.processOne(Outbox)` **별도 빈**에 위임하여 건별 트랜잭션 경계
- [ ] **`OutboxService.processOne(Outbox)` 별도 빈 신규**
  - 현재: 없음 (Scheduler 내부에서 직접 처리)
  - 변경: Commerce와 동일하게 `OutboxService.processOne()` 신규 — `@Transactional` + Kafka 발행 + `markSent()` / `markFailed()` 단일 경계
- [ ] **재시도 최대 횟수: 5 → 6**
  - 현재: 선형 5회 (`retryCount * 60s` = 60/120/180/240s, 누적 약 10분)
  - 변경: 지수 6회 (`2^(retryCount-1)s` = 즉시/1/2/4/8/16s, 누적 31초)
- [ ] **백오프 전략: 선형 → 지수**
  - 현재: `nextRetryAt = now + retryCount * 60s`
  - 변경: `nextRetryAt = now + 2^(retryCount-1) * 1s`
- [ ] **Repository 메서드명 통일**
  - 현재: `findPendingForRetry`
  - 변경: `findPendingToPublish`
- [ ] **쿼리 연산자 통일**
  - 현재: `<= :now`
  - 변경: `< :now`
- [ ] **실패 메서드명 통일**
  - 현재: `increaseRetryCount()`
  - 변경: `markFailed()` (상수 내부화)

---

### 3-D. Frontend (N1 상태머신 연동 — React)

> 백엔드 Outbox 정합 결정의 **클라이언트 체감 계약** 반영.
> 근거: `front-server-idempotency-guide.md §4` 타임아웃 처리 기준 + N1 상태머신 패턴 (2026-04-22 결정 — URL 유지 + 페이지 내부 상태머신).

#### 필수 수정

- [ ] **`setTimeout(2000)` 단순 지연 제거**
  - 현재: `src/pages/PaymentSuccess.tsx:39` — Toss confirm 성공 후 `setTimeout(2000)` → `/payment/complete` 이동
  - 변경: confirm 성공 **즉시** `/payment/complete` 이동 (지연 제거)
  - 근거: Saga(Kafka 비동기) 완료 보장 없는 2초 지연은 사용자 기만 — 최종 상태는 `/payment/complete` 내부 폴링으로 확정해야 정확

- [ ] **`/payment/complete` 페이지 상태머신 재작성**
  - 현재: 단순 완료 화면 (실제 구조 실사 필요)
  - 변경: 내부 상태 `LOADING | PAID | FAILED | CANCELLED | TIMEOUT` 분기 UI
  - **URL은 유지** — N1 결정 (URL = 결제 플로우 결과 섹션의 정체성, 상태 = 내용물로 분리)
  - 화면 문구 예시:
    - `LOADING`: "결제 처리 중..." + 스피너
    - `PAID`: 성공 화면 + 티켓 표시
    - `FAILED`: 실패 화면 + 사유 + 재시도 CTA
    - `CANCELLED`: 취소 화면 (만료/직접 취소 안내)
    - `TIMEOUT`: "잠시 후 주문내역에서 확인" 안내 + 주문내역 링크

- [ ] **`useOrderStatus` 훅 신규** (React Query / SWR 미사용 — `setInterval` 기반 직접 구현)
  - 신규 파일: `src/hooks/useOrderStatus.ts`
  - 동작:
    - `setInterval(fn, 2000)` 로 2초 간격 `GET /api/orders/{orderId}` 폴링
    - 종단 상태(`PAID | FAILED | CANCELLED`) 도달 시 **자동 중지** (`clearInterval`)
    - 시도 횟수 카운터 30회 초과 시 `TIMEOUT` 반환
    - 언마운트 시 `useEffect` cleanup 에서 `clearInterval` 정리 (메모리 누수·고아 요청 방지)
    - 동일 요청 중복 방지: in-flight 플래그로 이전 응답 대기 중이면 다음 틱 스킵
  - 반환 타입: `{ status: OrderStatus | 'LOADING' | 'TIMEOUT', order: Order | null, error: Error | null }`
  - 주의: 이 프로젝트는 `@tanstack/react-query` / SWR 등 데이터 페칭 라이브러리를 사용하지 않으므로 직접 구현 필수

- [ ] **`api/orders.ts` 추가 (또는 확장)**
  - `getOrderStatus(orderId: string): Promise<OrderStatusResponse>` 래퍼
  - `GET /api/orders/{orderId}` 호출, `X-User-Id` 헤더는 기존 `api/client.ts` 인터셉터 재사용

- [ ] **폴링 가이드 상수 분리**
  - 신규 파일: `src/constants/polling.ts`
  - 상수:
    ```ts
    export const POLL_INTERVAL_MS = 2000;      // 폴링 간격
    export const POLL_MAX_ATTEMPTS = 30;       // 최대 시도 횟수
    export const POLL_TIMEOUT_MS = 60_000;     // 총 타임아웃 (2s × 30 = 60s)
    ```
  - 근거: `front-server-idempotency-guide.md §4` 권장 폴링 전략. 백엔드 산식(Saga SLA + Outbox 창 + 여유) 변경 시 본 상수만 갱신하면 전파

#### 선행 확인 (프론트 작업 착수 전)

- [ ] **백엔드 `GET /api/orders/{orderId}` 구현 여부 실사**
  - 경로: `api-overview.md` + 실제 `OrderController` 구현
  - 미구현 시: Commerce 모듈에서 **선 작업 필요** (본 문서 scope 밖, 별도 이슈)
- [ ] **응답 스키마에 `orderStatus` 필드 포함 여부**
  - 필수 값: `PAYMENT_PENDING`, `PAID`, `FAILED`, `CANCELLED`, `CREATED` (종단·진행중 구분 가능해야 함)
  - `service-status.md` 와 일치 여부 확인
- [ ] **Gateway Rate limit 미차단 확인**
  - Gateway Bucket4j `5초 내 1회` 정책이 `GET /orders` 를 차단하지 않는지 검증
  - 조회 API는 Rate limit **제외 경로**여야 함 (쓰기 API만 차단 대상)

---

## 4. 공통 작업

### 4-A. ProducerConfig 타임아웃 3종 (3모듈 공통)

모듈별 `KafkaProducerConfig` 클래스에 동일 세팅:

```java
config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1500);
config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000);
config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 500);
```

근거: `kafka-design.md §4 Producer 타임아웃 정합` — 앱 `.get(2s)`와 Producer 내부 타임아웃 정합으로 이중 발행 차단.

### 4-B. Repository 메서드명·연산자 통일 (3모듈 공통, 선택이나 강권장)

- 메서드명: `findPendingToPublish`
- 쿼리 연산자: `< :now`

근거: 모듈 간 코드 가독성·교차 리뷰·템플릿 복제 시 혼란 차단.

### 4-C. F2: 패키지 경로 통일 — `common.outbox` 현행 유지 (3모듈 공통)

> **2026-04-22 결정**: #495 본문의 `common.outbox` → `infrastructure.messaging` 이동 방향을 **번복**.
> 표준 경로는 **`common.outbox` 로 확정** (현행 유지).
> 기존에 일부 모듈이 `infrastructure.messaging` 으로 **이동 작업된 부분이 있으면 되돌려 `common.outbox` 로 복귀**시킨다.
> #495 이슈 본문 F2 항목은 방향 정정 업데이트 필요 (별건).

#### 작업 체크리스트 (3모듈 공통)

- [ ] **표준 경로 확정**: `{module}/src/main/java/com/devticket/{module}/common/outbox/`
  - 대상 클래스: `Outbox`, `OutboxStatus`, `OutboxEventMessage`, `OutboxRepository`, `OutboxService`, `OutboxScheduler`, `OutboxEventProducer`, `OutboxPublishException`
- [ ] **`infrastructure.messaging` 이동분 존재 여부 실사 후 되돌림**
  - 대상 탐색: 각 모듈 `src/main/java/**/infrastructure/messaging/**` 내 `Outbox*` 클래스 유무 확인
  - 존재 시: 파일 이동 `infrastructure/messaging/` → `common/outbox/`
  - 이동 후: 파일 상단 `package ...;` 선언을 `com.devticket.{module}.common.outbox;` 로 정정
- [ ] **import 경로 전수 치환**
  - `com.devticket.{module}.infrastructure.messaging.Outbox*` → `com.devticket.{module}.common.outbox.Outbox*`
  - 호출부(Service, Controller, Consumer, Config 등) 전체
  - 치환 후 IDE 컴파일 오류 제로 확인
- [ ] **빌드·테스트 통과 검증**
  - `./gradlew :{module}:build` 각 모듈 성공
  - 기존 Outbox 관련 단위·통합 테스트 통과

#### 근거

- Payment 현재 구조(`payment.common.outbox.*`)가 이미 정착되어 있고, Commerce·Event도 동일 표준 유지 중 (2026-04-22 실사 기준)
- 계층 이동(`common` → `infrastructure`)은 **실익 대비 광범위 변경** — 패키지 선언·import 전수·PR 리뷰 부담
- AGENTS.md 컨벤션과도 정합 — 공통 인프라성 클래스는 `common/` 하위 유지가 일관
- #495 본문은 이슈 작성 시점의 초기 안이었으며, 후속 논의 결과 **현행 유지로 수렴**

---

## 5. 검증 포인트

### 5-A. 타임아웃 정합 검증 (필수)

- `.get(2s)` **이전에** Producer 내부 재시도가 확정 종료되는지 (`delivery.timeout.ms=1500 < 2000`)
- 앱 레벨 Outbox `markFailed()` → `nextRetryAt=+1s` 재발행 예약 시점에 Kafka Producer가 **이미 송신 확정**된 상태여야 함 (이중 발행 차단)

### 5-B. Scheduler 트랜잭션 경계 검증 (Event, Payment)

- Scheduler 메서드에 `@Transactional` **없음** 확인
- `processOne()` 별도 빈 호출 경로 확인 (self-invocation 아님 — 다른 빈 주입 → 메서드 호출)
- 개별 메시지 실패가 루프 전체를 롤백시키지 않는지 확인 (건별 경계 격리)

### 5-C. `messageId` 타입 전환 영향 (Payment)

- DB 컬럼 타입 변경 → 기존 데이터 마이그레이션 필요 여부 확인
- Kafka 헤더 `X-Message-Id` 세팅 시 String 직접 전달 확인
- `processed_message.message_id` 타입과 정합 확인

### 5-D. 재시도 정책 전환 영향 (Payment)

- 기존 선형 5회 상태로 PENDING 저장된 레코드의 `nextRetryAt` 재산정 필요 여부
- `retryCount >= 6` 도달 시 FAILED 전환 로직 — 기존 `MAX_RETRY=5` 상수 일괄 치환

### 5-E. `lockAtMostFor` 확장 부작용 (3모듈)

- 인스턴스 장애 시 락 홀딩 5분 → 스케줄러 5분간 휴지 → **Outbox 지연 5분** 가능성 인지
- 대안: Spring Boot graceful shutdown 훅에서 ShedLock 명시 해제 여부 검토

### 5-F. Frontend 폴링·상태머신 검증 (Frontend)

- `PaymentSuccess.tsx:39` `setTimeout(2000)` 제거 확인 — confirm 성공 → `/payment/complete` 즉시 이동
- 폴링 상수 (`POLL_INTERVAL_MS=2000` / `POLL_MAX_ATTEMPTS=30` / `POLL_TIMEOUT_MS=60000`) 단일 소스 일관성
- 종단 상태(`PAID | FAILED | CANCELLED`) 도달 시 폴링 **자동 중지** — 불필요한 DB/네트워크 호출 방지
- 언마운트 시 `clearInterval` 정리 — 메모리 누수·고아 요청 방지
- Gateway Rate limit 미차단 확인 (`GET /orders` 경로)
- 5개 상태(`LOADING | PAID | FAILED | CANCELLED | TIMEOUT`) UI 회귀 테스트 — 각 상태 문구·CTA 정상 노출

---

## 6. 관련 문서

- `kafka-design.md §4` — Outbox 패턴 단일 정책 (권위 문서)
- `kafka-impl-plan.md` — Phase별 구현 체크리스트
- `kafka-idempotency-guide.md` — Consumer 멱등성 (processed_message 패턴)
- `front-server-idempotency-guide.md` — HTTP 멱등성 + 폴링 가이드 (Outbox 재시도 창 참조)

---

## 🔑 주요내용 정리

- 기준안 = **Commerce** (대부분 통합 결정값과 일치)
- **Event 필수 수정 6건** + 선택 3건 — Producer 시그니처 / 타임아웃 / ShedLock / self-invocation 버그
- **Payment 필수 수정 10건** + 선택 3건 — schema / messageId 타입 / save 시그니처·전파 / Producer / 타임아웃 / 장기 트랜잭션 / 재시도 전면 개편
- **Frontend 필수 수정 5건** + 선행 확인 3건 — `setTimeout` 제거 / `/payment/complete` 상태머신화 / `useOrderStatus` 훅 신규 / API 래퍼 / 폴링 상수 분리
- **공통**: ProducerConfig 타임아웃 3종(1500/1000/500) 3모듈 적용 + **F2 패키지 `common.outbox` 표준 확정** (이동분 되돌림, #495 본문 방향 번복)
- **검증**: 타임아웃 정합 / Scheduler 트랜잭션 경계 / messageId 타입 전환 / 재시도 정책 전환 / `lockAtMostFor` 부작용 / **Frontend 폴링·상태머신**
