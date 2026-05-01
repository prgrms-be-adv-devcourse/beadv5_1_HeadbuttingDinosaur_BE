# Kafka Sync vs Async Policy

> 최종 업데이트: 2026-04-21
> 목적: DevTicket 프로젝트에서 서비스 간·내부 통신 경계를 **Sync(HTTP)** vs **Async(Kafka)** 중 무엇으로 선택할지에 대한 **의사결정 기준**을 제공한다. `kafka-design.md` / `actionLog.md` / `front-server-idempotency-guide.md`의 **상위 원칙** 문서.
>
> 참조: `kafka-design.md` §4 Outbox, §6 Producer 설정 / `actionLog.md` §1~§2 / `front-server-idempotency-guide.md` §1~§2

---

## 목차

0. [적용 범위 및 용어](#0-적용-범위-및-용어)
1. [통신 경계 3분류](#1-통신-경계-3분류)
2. [결정 매트릭스](#2-결정-매트릭스)
3. [현재 스코프 적용 현황](#3-현재-스코프-적용-현황)
4. [예외 / 회색지대](#4-예외--회색지대)
5. [확장 지침 — 새 기능 추가 시](#5-확장-지침--새-기능-추가-시)

---

## 0. 적용 범위 및 용어

### 적용 범위
- 모든 **서비스 간** 통신(Commerce ↔ Event ↔ Payment ↔ Log)의 방향 결정
- 사용자 ↔ 백엔드 API (Gateway 포함)의 동기/비동기 경계
- 신규 기능 설계 시 **어느 채널을 통해 상태를 전파할지** 결정

### 적용 외
- 같은 서비스 내 내부 메서드 호출 / DB 조회 — 통신 경계 아님
- PG 등 외부 시스템 연동 — 외부 정책에 따름 (상세: `kafka-design.md` §10)

### 용어 정의

| 용어 | 의미 |
|---|---|
| **Sync HTTP** | 요청-응답 즉시성, 강한 일관성, 사용자 트리거 |
| **Async Kafka + Outbox** | 비즈니스 트랜잭션 원자성 보장하는 이벤트 발행 (Saga 계열) |
| **Async Kafka fire-and-forget** | 손실 허용 / 트랜잭션 경계 밖 / `acks=0` (analytics 계열) |

---

## 1. 통신 경계 3분류

### 1-A. Sync HTTP — 사용자 트리거 요청
- **성격**: 사용자가 결과를 **즉시 화면에서 봐야 하는** 요청
- **전제**: 요청-응답 일치, 강한 일관성, 오류 시 즉시 사용자 피드백
- **멱등성**: `Idempotency-Key` 헤더 + 서비스 레이어 상태 가드 (상세: `front-server-idempotency-guide.md` §1~§2)
- **예시**: 주문 생성, 결제 Ready/Confirm, 로그인, 장바구니 조회

### 1-B. Async Kafka + Outbox — 비즈니스 Saga 이벤트
- **성격**: 서비스 간 **상태 전파가 트랜잭션과 원자적이어야 하는** 이벤트
- **전제**: 최종적 일관성, 실패 시 자동 재시도/보상, 메시지 손실 불허
- **설정** (`kafka-design.md` §6 기준):
 - Producer: `acks=all`, `enable.idempotence=true`, `retries=3`
 - Outbox 패턴 필수 — 비즈니스 로직 + `outboxService.save()` **단일 `@Transactional` 경계**
 - `X-Message-Id` 헤더 필수 (Consumer dedup용)
 - Consumer: `AckMode=MANUAL`, ExponentialBackOff(2→4→8초, 3회), DLT
- **멱등성**: `processed_message` + `canTransitionTo()` + `@Version` 3중 방어 (상세: `kafka-idempotency-guide.md`)
- **예시**: `order.created`, `stock.deducted`, `stock.failed`, `payment.completed`, `payment.failed`, `ticket.issue-failed`, `refund.*`, `event.force-cancelled`, `event.sale-stopped`

### 1-C. Async Kafka fire-and-forget — Analytics `action.log`
- **성격**: 사용자 행동 로그, **손실 허용**, 비즈니스 트랜잭션과 무관
- **전제**: API 응답 지연 제로, 재시도·DLT 운영 오버헤드 제거
- **설정** (`kafka-design.md` §6 action.log 예외 설정 / `actionLog.md`):
 - Producer: `acks=0`, `retries=0`, `enable.idempotence=false`
 - Outbox **미사용**, 트랜잭션 경계 **밖** 비동기 발행
 - `X-Message-Id` 헤더 미사용
 - 전용 `ActionLogKafkaProducerConfig` Bean **분리** (기존 Producer Bean과 공유 금지)
- **멱등성**: 불필요 (dedup 미적용, at-most-once)
- **예시**: `VIEW`, `DETAIL_VIEW`, `DWELL_TIME`, `CART_ADD`, `CART_REMOVE`, `PURCHASE`

---

## 2. 결정 매트릭스

| 판단 축 | 1-A Sync HTTP | 1-B Kafka + Outbox | 1-C Kafka fire-and-forget |
|---|---|---|---|
| 사용자 응답 즉시성 | **필요** | 불요 | 불요 |
| 일관성 수준 | 강한 일관성 | 최종적 일관성 | 베스트에포트 |
| 데이터 손실 허용도 | 불허 | 불허 (재시도 + 보상) | **허용** |
| 실패 시 처리 | 사용자 재요청 / HTTP 5xx | 자동 재시도 → DLT → 수동 | 없음 (fire-and-forget) |
| 트랜잭션 원자성 | 단일 DB 트랜잭션 | Outbox로 원자성 보장 | 트랜잭션 경계 밖 |
| Producer 설정 | N/A | `acks=all` + idempotence + retries | `acks=0`, retries=0 |
| Consumer 멱등성 | Idempotency-Key | `processed_message` + `canTransitionTo` + `@Version` | 불필요 |
| 서비스 간 결합도 | 강결합 | 느슨한 결합 (이벤트) | 느슨한 결합 (단방향) |

### 판단 흐름 (질문 체크리스트)

1. **사용자가 이 요청의 결과를 즉시 화면에서 봐야 하는가?** → YES면 **1-A Sync HTTP**
2. **다른 서비스로 상태 전파가 필요한가?** → NO면 현재 서비스 내부 처리 / YES면 아래로
3. **이벤트 발행이 비즈니스 트랜잭션과 원자적이어야 하는가?** (실패 시 정합성 깨짐) → YES면 **1-B Kafka + Outbox**
4. **손실 허용 가능한 로그·분석 성격인가?** (응답 지연 제로 우선) → YES면 **1-C Kafka fire-and-forget**

---

## 3. 현재 스코프 적용 현황

| 기능 | 분류 | 근거 문서 |
|---|---|---|
| 주문 생성 API (`POST /api/orders`) | 1-A Sync HTTP | `front-server-idempotency-guide.md` §4-1 |
| 결제 Ready (`POST /api/payments/ready`) | 1-A Sync HTTP | `kafka-design.md` §8 락 전략 |
| 결제 Confirm (`POST /api/payments/confirm`) | 1-A Sync HTTP | `kafka-design.md` §8 |
| 장바구니 담기/삭제 API | 1-A Sync HTTP | (일반 CRUD) |
| 이벤트 조회 API | 1-A Sync HTTP | (일반 조회) |
| `order.created` 발행 (Commerce) | 1-B Kafka + Outbox | `kafka-design.md` §3 / `kafka-impl-plan.md` §3-2 |
| `stock.deducted` / `stock.failed` 발행 (Event) | 1-B Kafka + Outbox | `kafka-design.md` §3 / `kafka-impl-plan.md` §3-3 |
| `payment.completed` / `payment.failed` 발행 (Payment) | 1-B Kafka + Outbox | `kafka-design.md` §3 / `kafka-impl-plan.md` §3-1 |
| `payment.failed` 수신 → 재고 복원 (Event) | 1-B Kafka + Outbox | `kafka-design.md` §5 |
| `payment.completed` 수신 → PAID 전이 + 티켓 발급 + 장바구니 차감 (Commerce) | 1-B Kafka + Outbox | `kafka-impl-plan.md` §3-2 |
| 재고 차감 (Event 내부) | Consumer 내부 동기 DB 트랜잭션 | `kafka-impl-plan.md` §3-3 |
| 티켓 발급 / 장바구니 매칭 차감 (Commerce 내부) | Consumer 내부 동기 DB 트랜잭션 | `kafka-impl-plan.md` §3-2 |
| `CART_ADD` / `CART_REMOVE` 발행 (Commerce) | 1-C Kafka fire-and-forget | `actionLog.md` / `kafka-impl-plan.md` §3-2 |
| `VIEW` / `DETAIL_VIEW` / `DWELL_TIME` 발행 (Event) | 1-C Kafka fire-and-forget | `actionLog.md` / `kafka-impl-plan.md` §3-3 |
| PURCHASE 수집 (Log 서비스) | 1-B 재소비 → DB 직접 INSERT (4-3 참조) | `actionLog.md` §2 #12 / §4 |

---

## 4. 예외 / 회색지대

### 4-1. 운영 취소 이벤트 (`event.force-cancelled` / `event.sale-stopped`)
- **분류**: 1-B Kafka + Outbox
- **경계**: Admin/Seller HTTP API(Sync) 진입 → 트랜잭션 내 Outbox INSERT → Kafka fan-out (Async)
- **특징**: 사용자 트리거 Sync + 비즈니스 이벤트 Async의 **혼합 진입**. 요청자는 "접수" 응답만 즉시 받고, 후속 영향(주문 환불 fan-out)은 Async 처리

### 4-2. 환불 Saga (Orchestrator 패턴)
- **분류**: 1-B Kafka + Outbox
- 상세: `kafka-design.md` §9-3 / `kafka-impl-plan.md` §2-7

### 4-3. PURCHASE 로그
- **분류**: 하이브리드 — **`payment.completed` 재소비** (1-B 토픽을 Log 서비스가 별도 Consumer로 구독 → `log.action_log` 직접 INSERT, Kafka 재발행 없음)
- **이유**: PURCHASE는 결제 완료 시점과 정확히 일치해야 하므로 별도 Producer 발행이 아니라 **기존 1-B 이벤트 재활용**
- **구현 제약**: `PaymentCompletedEvent.orderItems` 필드 필수 (Log 측 PURCHASE 레코드 매핑용). Commerce/Event Consumer는 `@JsonIgnoreProperties(ignoreUnknown=true)` / `FAIL_ON_UNKNOWN_PROPERTIES=false` 선배포 전제
- 상세: `actionLog.md` §2 #12, §4

### 4-4. 조회 API + analytics 병행
- 조회 API 응답은 1-A Sync HTTP, **로깅만** 1-C fire-and-forget 병행
- 로깅 실패가 조회 응답에 영향 주면 안 됨 → **트랜잭션 경계 밖 필수**

---

## 5. 확장 지침 — 새 기능 추가 시

### 5-1. 체크리스트
1. 사용자 응답 즉시성이 필요한가?
2. 다른 서비스로 상태 전파가 필요한가?
3. 이벤트 발행이 비즈니스 트랜잭션과 원자적이어야 하는가?
4. 손실 허용 가능한 로그·분석 성격인가?
5. Producer 설정이 1-B vs 1-C 중 어디에 속하는가 → **Bean 분리 여부** 확인

### 5-2. 금지 사항
- **1-C 용도로 1-B Producer Bean 재사용 금지** — `acks=0` 경로는 반드시 전용 Bean (`ActionLogKafkaProducerConfig`)
- **1-B 이벤트에 Outbox 생략 금지** — 트랜잭션 내 Kafka 직접 발행은 원자성 깨짐
- **1-A Sync HTTP 안에서 동기 cross-service 호출 체인(2홉 이상) 생성 금지** — 장애 전파, 응답 지연. 반드시 1-B Async 전환 검토

### 5-3. 변경 영향도 큰 경우 — 팀 합의 필수
- 신규 토픽 추가
- 기존 토픽의 Producer 설정 변경 (예: `acks` 값 변경)
- 분류 변경 (1-B ↔ 1-C)
- 상세: `kafka-design.md` **공통 원칙** (팀 단위 설계 합의 필수)

---

## 참조 문서

- `kafka-design.md` — 이벤트 DTO 계약, Producer/Consumer 설정 상세
- `kafka-impl-plan.md` — 서비스별 구현 체크리스트, Phase 순서
- `kafka-idempotency-guide.md` — Consumer 멱등성 3중 방어선 상세
- `front-server-idempotency-guide.md` — HTTP 멱등성 (Idempotency-Key)
- `actionLog.md` — `action.log` 토픽 전용 설계 / PURCHASE 수집 체인
