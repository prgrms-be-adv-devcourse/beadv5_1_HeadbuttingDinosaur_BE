# Kafka 구현용 DB 스키마 변경 계획

> 최종 업데이트: 2026-04-16

---

## 현재 스코프 — 주문생성 → 결제완료

### Payment

| 분류 | 대상 | 변경 내용 | 방법 |
|------|------|-----------|------|
| outbox 수정 | `aggregate_id` | BIGINT → VARCHAR(36) 타입 변경 | 수동 ALTER |
| outbox 수정 | `aggregate_type` | 컬럼 제거 (설계 문서에 없는 컬럼) | 수동 ALTER |
| outbox 수정 | `topic` | VARCHAR(128) 컬럼 추가 | 엔티티 필드 추가 → 자동 |
| outbox 수정 | `partition_key` | VARCHAR(36) 컬럼 추가 | 엔티티 필드 추가 → 자동 |
| outbox 수정 | `next_retry_at` | TIMESTAMP 컬럼 추가 | 엔티티 필드 추가 → 자동 |
| outbox 수정 | `sent_at` | TIMESTAMP 컬럼 추가 | 엔티티 필드 추가 → 자동 |
| processed_message 수정 | `topic` | VARCHAR(128) 컬럼 추가 | 엔티티 필드 추가 → 자동 |
| shedlock 생성 | 신규 테이블 | Outbox 스케줄러 분산 락 | 수동 CREATE TABLE |
| payment 엔티티 | `version` | BIGINT 컬럼 추가 (@Version) | 엔티티 필드 추가 → 자동 |

### Commerce

| 분류 | 대상 | 변경 내용 | 방법 |
|------|------|-----------|------|
| outbox 생성 | 신규 테이블 | id, message_id, aggregate_id, partition_key, event_type, topic, payload, status, retry_count, next_retry_at, created_at, sent_at | @Entity 추가 → 자동 |
| processed_message 생성 | 신규 테이블 | id, message_id(UNIQUE), topic, processed_at | @Entity 추가 → 자동 |
| shedlock 생성 | 신규 테이블 | Outbox 스케줄러 분산 락 | 수동 CREATE TABLE |
| order 엔티티 | `version` | BIGINT 컬럼 추가 (@Version) | 엔티티 필드 추가 → 자동 |
| order 엔티티 | `cart_hash` | VARCHAR(64) 컬럼 추가, 인덱스 (user_id, cart_hash), 해시 대상: (itemId, quantity) — unitPrice 미포함 (팀 합의) | 엔티티 필드 추가 → 자동 |

> **코드 수정 연계 (DB 외)**
> - `Order.create()` 초기 status `PAYMENT_PENDING` → `CREATED` 변경 *(주문생성 Phase 스코프)*
> - 만료 시각은 DB 컬럼 없음 — 런타임에 `created_at + 30분`으로 계산 (팀 합의 완료: 30분 픽스)
> - 만료 스케줄러 스코프 구분:
>   - **본 스코프 (구현 완료)**: `PAYMENT_PENDING` 상태 + `created_at <= now() - 30분` → `CANCELLED` (`OrderExpirationScheduler`)
>   - **추후 (주문생성 Phase)**: `CREATED` 상태 + `created_at <= now() - 30분` → `CANCELLED` + 재고 복구 Outbox — `stock.deducted` 수신 후 `PAYMENT_PENDING` 전이하는 플로우가 도입된 이후 활성화

### Event

| 분류 | 대상 | 변경 내용 | 방법 | 상태 |
|------|------|-----------|------|------|
| outbox 생성 | 신규 테이블 | Commerce와 동일 구조 | @Entity 추가 → 자동 | ⬜ 미구현 |
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
```

---

## 추후 스코프 — WALLET_PG 복합결제

### Payment

| 분류 | 대상 | 변경 내용 | 방법 |
|------|------|-----------|------|
| payment 엔티티 | `wallet_amount` | INTEGER 컬럼 추가 — PG/WALLET 단독결제 시 0, WALLET_PG 시 예치금 차감 금액 | 엔티티 필드 추가 → 자동 |
| payment 엔티티 | `pg_amount` | INTEGER 컬럼 추가 — PG/WALLET 단독결제 시 0, WALLET_PG 시 PG 결제 금액 | 엔티티 필드 추가 → 자동 |

> 기존 `amount`는 총 결제금액 유지. WALLET_PG일 때 `amount = walletAmount + pgAmount`.
> `PaymentMethod` enum에 `WALLET_PG` 추가는 코드 레벨 (DB 스키마 변경 없음).

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
