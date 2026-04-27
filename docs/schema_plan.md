# Kafka 구현용 DB 스키마 변경 계획

> 최종 업데이트: 2026-04-27

> **마이그레이션 운영 정책**
> 본 저장소는 Flyway/Liquibase 같은 SQL 마이그레이션 도구를 **사용하지 않습니다**. 컬럼·테이블 추가는 JPA `ddl-auto: update`가 자동 처리하고, 타입 변경·테이블 삭제·`shedlock`처럼 자동화 불가 항목은 아래 "수동 실행 필요 항목 전체 모음" SQL을 운영자가 직접 실행합니다. 따라서 본 문서가 곧 **마이그레이션 대장(ledger)** 역할을 겸합니다 (`db/migration/V*.sql` 파일은 존재하지 않습니다).

---

## 현재 스코프 — 주문생성 → 결제완료

### Payment

| 분류 | 대상 | 변경 내용 | 방법 |
|------|------|-----------|------|
| outbox 수정 | `aggregate_id` | BIGINT → VARCHAR(36) 타입 변경 | 수동 ALTER |
| outbox 수정 | `aggregate_type` | 컬럼 제거 (설계 문서에 없는 컬럼) | 수동 ALTER |
| outbox 수정 | `message_id` | UUID → VARCHAR(36) 타입 변경 (⚠️ B4-2 후속 — 별건 이슈 분리 권고) | 수동 ALTER (PostgreSQL `USING message_id::text` 명시 필요) |
| outbox 수정 | `topic` | VARCHAR(128) 컬럼 추가 | 엔티티 필드 추가 → 자동 |
| outbox 수정 | `partition_key` | VARCHAR(36) 컬럼 추가 | 엔티티 필드 추가 → 자동 |
| outbox 수정 | `next_retry_at` | TIMESTAMP 컬럼 추가 | 엔티티 필드 추가 → 자동 |
| outbox 수정 | `sent_at` | TIMESTAMP 컬럼 추가 | 엔티티 필드 추가 → 자동 |
| processed_message 수정 | `topic` | VARCHAR(128) 컬럼 추가 | 엔티티 필드 추가 → 자동 |
| processed_message 수정 | `message_id` | UUID → VARCHAR(36) 타입 변경 — ✅ **PR #584 머지 완료** (2026-04-27, 엔티티 필드도 `String` 으로 통일) | 수동 ALTER (PostgreSQL `USING message_id::text` 명시 필요) |
| shedlock 생성 | 신규 테이블 | Outbox 스케줄러 분산 락 | 수동 CREATE TABLE |
| payment 엔티티 | `version` | BIGINT 컬럼 추가 (@Version) | 엔티티 필드 추가 → 자동 |

### Commerce

| 분류 | 대상 | 변경 내용 | 방법 |
|------|------|-----------|------|
| outbox 생성 | 신규 테이블 | id, message_id, aggregate_id, partition_key, event_type, topic, payload, status, retry_count, next_retry_at, created_at, sent_at | @Entity 추가 → 자동 |
| processed_message 생성 | 신규 테이블 | id, message_id(UNIQUE), topic, processed_at | @Entity 추가 → 자동 |
| shedlock 생성 | 신규 테이블 | Outbox 스케줄러 분산 락 | 수동 CREATE TABLE |
| order 엔티티 | `version` | BIGINT 컬럼 추가 (@Version) | 엔티티 필드 추가 → 자동 |
| order 엔티티 | `cart_hash` | VARCHAR(64) 컬럼 추가, 인덱스 (user_id, cart_hash), 해시 대상: (eventId, quantity) — unitPrice 미포함 (팀 합의) ✅ 완료 (2026-04-19) | 엔티티 필드 추가 → 자동 |
| cart_item 엔티티 | `(cart_id, event_id)` UNIQUE ✅ | 광클 동시성 결함 방어 + cart_hash 분기 삭제(A안) 매칭 로직 단순화 — 구현 완료 (#416, 2026-04-19, 제약명 `uk_cart_item_cart_event`) | 엔티티 필드 추가 → 자동 |

> **코드 수정 연계 (DB 외)**
> - `Order.create()` 초기 status `CREATED` (주문생성 Phase 코드 취합 완료 — `stock.deducted` 수신 후 `PAYMENT_PENDING` 전이)
> - 만료 시각은 DB 컬럼 없음 — `BaseEntity.updated_at` (`@LastModifiedDate`) 재활용, `PAYMENT_PENDING` 진입 시각 기준 (팀 합의 완료: 30분 픽스)
>   - `created_at` 기준은 `CREATED` 진입 시각이라 `stock.deducted` 지연 시 결제 시간 단축 문제 발생 → 폐기
>   - ⚠️ 가정: PAYMENT_PENDING 상태에서 Order 엔티티 수정 경로 없음. 향후 mutation 추가 시 `payment_pending_at` 전용 컬럼 신설로 이관 검토
> - 만료 스케줄러 스코프:
>   - **본 스코프**: `PAYMENT_PENDING` 상태 + `updated_at <= now() - 30분` → `CANCELLED` + **재고 복구 Outbox 발행** (`payment.failed`, `reason="ORDER_TIMEOUT"`) — `OrderExpirationScheduler`
>     - `PAYMENT_PENDING` 상태는 이미 `stock.deducted` 이후이므로 재고가 선점된 상태 → 복구 필수
>     - Event 모듈 `PaymentFailedConsumer`가 수신하여 재고 `DEDUCTED → RESTORED` 전이 (`kafka-idempotency-guide.md §10` 원칙 준수)
>     - reason 값 정의: `docs/kafka-design.md §3 PaymentFailedEvent`
>   - **추후 (주문생성 Phase 보강)**: `CREATED` 상태 만료 처리 — 재고 차감 전이라 재고 복구 불필요, Order 상태만 `CANCELLED` 또는 `FAILED`로 종단 처리

### Event

| 분류 | 대상 | 변경 내용 | 방법 | 상태 |
|------|------|-----------|------|------|
| outbox 생성 | 신규 테이블 | Commerce와 동일 구조 | @Entity 추가 → 자동 | ✅ 완료 (2026-04-22 실코드 대조 확인) |
| processed_message 생성 | 신규 테이블 | Commerce와 동일 구조 (schema=`event`, `topic VARCHAR` 컬럼 포함) | @Entity 추가 → 자동 | ✅ 완료 |
| shedlock 생성 | 신규 테이블 | Outbox 스케줄러 분산 락 | 수동 CREATE TABLE | ⬜ 미구현 |
| event 엔티티 | `version` | BIGINT 컬럼 추가 (@Version) | 엔티티 필드 추가 → 자동 | ✅ 완료 |

> **합의 완료 (2026-04-14):** OrderCreatedEvent / PaymentFailedEvent 모두 `List<OrderItem>(eventId, quantity)` 리스트 구조 채택 → Stock 신규 엔티티 추가 없음, 기존 event 테이블 quantity 컬럼 사용

---

## 수동 실행 필요 항목 전체 모음

```sql
-- ① Payment: outbox aggregate_id 타입 변경
ALTER TABLE payment.outbox
    ALTER COLUMN aggregate_id TYPE VARCHAR(36) USING aggregate_id::text;

-- ② Payment: outbox aggregate_type 컬럼 제거
ALTER TABLE payment.outbox DROP COLUMN aggregate_type;

-- ③ Payment: shedlock 생성
CREATE TABLE payment.shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- ④ Commerce: order fail_reason 컬럼 제거
ALTER TABLE commerce.order DROP COLUMN fail_reason;

-- ⑤ Commerce: shedlock 생성
CREATE TABLE commerce.shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- ⑥ Event: shedlock 생성
CREATE TABLE event.shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- ⑦ Payment: outbox message_id 타입 변경 (B4-2 후속)
-- ⚠️ 운영 배포 타이밍은 본 리팩토링과 별도 — 별건 이슈로 분리 권고 (outbox_fix.md §3-C B4-2)
-- ddl-auto:update 자동 처리 불가 가능성 높음 — USING 명시 필수
ALTER TABLE payment.outbox
    ALTER COLUMN message_id TYPE VARCHAR(36) USING message_id::text;

-- ⑧ Payment: processed_message message_id 타입 변경 (B4-2 정합) — ✅ PR #584 머지 (2026-04-27)
-- 엔티티 필드도 함께 UUID→String 전환 완료. 운영 DB ALTER 함께 실행됨.
ALTER TABLE payment.processed_message
    ALTER COLUMN message_id TYPE VARCHAR(36) USING message_id::text;

-- ⑨ Commerce: order version 컬럼 추가 (JPA @Version 실효화)
-- ddl-auto 미실행 환경 대비 — IF NOT EXISTS + DEFAULT 0으로 기존 row 호환
ALTER TABLE commerce.order
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- ⑩ Commerce: ticket version 컬럼 추가 (환불 Consumer refund.ticket.cancel 대응)
-- 추후 스코프(환불) 사전 준비 — IF NOT EXISTS + DEFAULT 0
ALTER TABLE commerce.ticket
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- ⑪ Payment: refund_ticket ticket_id UNIQUE 제약 추가
-- 동일 ticketId 동시 환불 요청 race condition 방어
-- 적용 전 중복 row 존재 여부 확인 필수:
--   SELECT ticket_id, COUNT(*) FROM payment.refund_ticket GROUP BY ticket_id HAVING COUNT(*) > 1;
ALTER TABLE payment.refund_ticket
    ADD CONSTRAINT uk_refund_ticket_ticket_id UNIQUE (ticket_id);
```

---

## 현재 스코프(완료) — WALLET_PG 복합결제

### Payment

| 분류 | 대상 | 변경 내용 | 방법 | 상태 |
|------|------|-----------|------|------|
| payment 엔티티 | `wallet_amount` | INTEGER 컬럼 추가 — PG/WALLET 단독결제 시 0, WALLET_PG 시 예치금 차감 금액 | 엔티티 필드 추가 → 자동 | ✅ 완료 |
| payment 엔티티 | `pg_amount` | INTEGER 컬럼 추가 — PG/WALLET 단독결제 시 0, WALLET_PG 시 PG 결제 금액 | 엔티티 필드 추가 → 자동 | ✅ 완료 |

> 기존 `amount`는 총 결제금액 유지. WALLET_PG일 때 `amount = walletAmount + pgAmount`.
> `PaymentMethod` enum에 `WALLET_PG` 추가는 코드 레벨 (DB 스키마 변경 없음) — ✅ 완료.
> 최근 커밋 `c8dd686 feat/payment-kafka 머지 — WALLET_PG 복합결제 develop/payment 반영`으로 병합됨.

---

## 추후 스코프 — 환불

### Payment

| 분류 | 대상 | 변경 내용 | 방법 |
|------|------|-----------|------|
| saga_state 생성 | 신규 테이블 | refund_id(PK), order_id, payment_method, current_step, status, created_at, updated_at | @Entity 추가 → 자동 |
| refund 엔티티 | `version` | BIGINT 컬럼 추가 (@Version) | 엔티티 필드 추가 → 자동 |

### Commerce

| 분류 | 대상 | 변경 내용 | 방법 |
|------|------|-----------|------|
| ticket 엔티티 | `version` | BIGINT 컬럼 추가 (@Version) — refund.ticket.cancel Consumer 대응 | 엔티티 필드 추가 → 자동 |

### Event

추가 스키마 변경 없음. 환불 관련 작업은 코드(Consumer/Producer) 레벨입니다.
