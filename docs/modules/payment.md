# payment

> 본 페이지는 ServiceOverview.md §3 payment 섹션의 확장판입니다.

## 1. 모듈 책임

결제 처리 (PG / WALLET / WALLET_PG 복합 분기) + 환불 처리 + 지갑(예치금) 관리(충전·출금·정산금 입금·환불 복구).

**위임 (담당 안 함)**:
- 주문 상태 관리 → commerce 모듈 (Outbox/Kafka 통지)
- 재고 차감 / 검증 → event 모듈
- 정산서 생성 → settlement 모듈

## 2. 외부 API

상세는 [api/api-summary.md](../api/api-summary.md) §payment 섹션 참조 (총 17개). ★ 핵심 플로우 발췌 + 환불 / 지갑:

| 메서드 | 경로 | Controller | Service 1줄 (service-status.md) |
|---|---|---|---|
| POST | `/api/payments/ready` ★ | `PaymentController.readyPayment` | 주문 검증 후 결제수단별(PG/WALLET/WALLET_PG) Payment를 생성한다 |
| POST | `/api/payments/confirm` ★ | `PaymentController.confirm` | PG 승인 후 Payment APPROVED + `payment.completed` Outbox를 발행한다 |
| POST | `/api/payments/fail` ★ | `PaymentController.fail` | PG 실패 반영 + WALLET_PG면 예치금 복구 + `payment.failed` Outbox를 발행한다 |
| GET | `/api/wallet` | `WalletController.getBalance` | (잔액 조회, MEDIUM) |
| POST | `/api/wallet/charge` | `WalletController.charge` | (충전 시작, MEDIUM) |
| POST | `/api/wallet/charge/confirm` | `WalletController.confirmCharge` | (충전 승인, MEDIUM) |
| POST | `/api/refunds/orders/{orderId}` | `RefundController.refundOrder` | (주문 환불, MEDIUM) |
| POST | `/api/refunds/pg/{ticketId}` | `RefundController.refundPgTicket` | (PG 티켓 환불, MEDIUM) |
| POST | `/api/admin/events/{eventId}/cancel` | `AdminRefundController.cancelAdminEvent` | (관리자 이벤트 취소 환불, MEDIUM) |
| POST | `/api/seller/events/{eventId}/cancel` | `SellerRefundController.cancelSellerEvent` | (판매자 이벤트 취소 환불, MEDIUM) |

**대상 구분**: 일반 사용자(payments/wallet/refunds), 판매자(seller/refunds, seller/events 취소), 관리자(admin/events 취소).

## 3. 내부 API (다른 서비스가 호출)

| 메서드 | 경로 | Controller | 호출 주체 | 비고 |
|---|---|---|---|---|
| GET | `/internal/payments/by-order/{orderId}` | `PaymentInternalController.getPaymentByOrderId` | (commerce 등) | active |
| POST | `/internal/wallet/settlement-deposit` ★ | `WalletInternalController.depositFromSettlement` | settlement | ★ 정산금 → 판매자 예치금 입금 |
| GET | `/internal/order-items/by-ticket/{ticketId}` | `MockCommerceController` | (개발/테스트) | ⚠ Mock — commerce 측 실제 엔드포인트 모킹 |
| GET | `/internal/orders/{orderId}` | `MockCommerceController` | (개발/테스트) | ⚠ Mock — 동일 |
| POST | `/mock/wallet/charge` | `MockCommerceController.mockCharge` | (개발/테스트) | ⚠ Mock |

## 4. Kafka

### 발행 (Producer) — kafka-design §3 line 72 (+ 발행 시점 line 85-86 / 88 / 92-94)

| 이벤트 | 분류 | 트리거 |
|---|---|---|
| `payment.completed` ★ | 1-B Outbox | PG 승인 성공 (`confirmPgPayment`) 또는 WALLET 결제 완료 (`processWalletPayment`) |
| `payment.failed` ★ | 1-B Outbox | PG 실패 / 검증 실패 (`failPgPayment`) |
| `refund.completed` | 1-B Outbox | Refund Saga 마지막 단계 완료 (Orchestrator) |
| `refund.order.cancel` / `refund.ticket.cancel` | 1-B Outbox | Saga 시작 또는 보상 트리거 |
| `refund.stock.restore` | 1-B Outbox | Ticket 취소 완료 수신 시 |
| `refund.order.compensate` / `refund.ticket.compensate` | 1-B Outbox | 보상 saga |

### Outbox 발행 패턴 (afterCommit 직접 발행 + 스케줄러 fallback)

위 표의 모든 1-B Outbox 이벤트는 다음 2단계 경로로 처리된다 (1-B Outbox 유지 — `kafka-sync-async-policy.md §1-B`).

1. **afterCommit 직접 발행 — 정상 경로** (`OutboxAfterCommitPublisher`)
   - 비즈니스 `@Transactional` 안에서 Outbox row 가 `PENDING` 으로 저장된다.
   - 커밋 직후 `afterCommit` 훅이 별도 executor 로 발행 작업을 위임한다 (`OutboxAsyncConfig`).
   - 워커 스레드가 `OutboxEventProducer.publish` 호출 후, 별도 `REQUIRES_NEW` 트랜잭션으로 row 를 `SENT` 로 전이한다.
   - 직접 발행 / `markSent` 어느 단계의 예외도 throw 하지 않고 `warn` 로그만 남긴다 → 비즈니스 TX 는 영향받지 않음.
2. **OutboxScheduler fallback — 보완 경로**
   - executor 큐 reject, Kafka 일시 장애, `markSent` 실패, 프로세스 다운 등으로 row 가 `PENDING` 에 남으면 스케줄러가 흡수한다.
   - 설정(`payment/src/main/resources/application.yml`):
     ```yaml
     outbox:
       publish-grace-seconds: 5      # 직접 발행 경로 동작 시간 확보
       poll-interval-ms: 60000       # fallback 폴링 주기 — 정상 경로가 즉시 처리하므로 60초로 완화 (3s → 60s)
     ```
   - 중복 발행은 consumer 측 `X-Message-Id` dedup 으로 무해화된다.

근거 커밋: `057ddf6d` (payment: afterCommit 직접 발행 + 스케줄러 fallback 전환), `dc383f70` (outbox 폴링 주기 3s → 60s 완화).

### 수신 (Consumer) — kafka-design §3 line 72 + 코드 변경 반영

| 토픽 | 처리 메서드 | 처리 내용 | 멱등성 |
|---|---|---|---|
| `refund.completed` | `WalletService.restoreBalance` | 예치금 복구 | dedup (transactionKey) |
| ⚠ ~~`event.force-cancelled`~~ | ~~`WalletService.processBatchRefund`~~ | **22762f2로 제거**. 강제취소 fan-out은 commerce.RefundFanoutService → `refund.requested` → payment Saga 경유로 일원화 | — |
| `event.sale-stopped` | (Saga Orchestrator) | 보상 흐름 | dedup |
| `ticket.issue-failed` | (Saga Orchestrator) | 결제 환불 처리 | dedup |
| `refund.requested` | `RefundSagaOrchestrator.start` | Saga 시작 — `event.totalOrderTickets()` 우선 사용(ea7f7cc9), 0(구버전 페이로드)이면 commerce `getOrderInfo` 폴백(431b9fe9), 그래도 실패 시 `ticketIds.size()` 최후 폴백 | dedup |
| `refund.order.done` / `refund.order.failed` | (Orchestrator) | Order 보상 응답 | dedup |
| `refund.ticket.done` / `refund.ticket.failed` | (Orchestrator) | Ticket 보상 응답 | dedup |
| `refund.stock.done` / `refund.stock.failed` | (Orchestrator) | Stock 보상 응답 | dedup |

> ⚠ `RefundRequestedEvent` 실 필드는 코드 기준 13개 (`refundId, orderRefundId, orderId, userId, paymentId, paymentMethod, ticketIds, refundAmount, refundRate, wholeOrder, reason, timestamp, totalOrderTickets`). kafka-design §3 line 298-311 정의는 구버전(8 필드)이라 드리프트 — 인용만, 실 정의는 `payment/.../refund/application/saga/event/RefundRequestedEvent.java` 참조.

## 5. DTO

상세는 [dto/dto-overview.md](../dto/dto-overview.md) payment 섹션 참조. 핵심 발췌:

- **Payment**: `PaymentReadyRequest/Response`, `PaymentConfirmRequest/Response`, `PaymentFailRequest/Response`, `InternalPaymentInfoResponse`
- **Wallet**: `WalletChargeRequest/Response`, `WalletChargeConfirmRequest/Response`, `WalletWithdrawRequest/Response`, `WalletBalanceResponse`, `WalletTransactionListResponse`, `SettlementDepositRequest`
- **Refund**: `RefundInfoResponse`, `RefundListResponse`, `RefundDetailResponse`, `SellerRefundListResponse`
- **Kafka payload (Outbox)**: `PaymentCompletedEvent`, `PaymentFailedEvent`, `RefundCompletedEvent`
- **외부 PG**: `PgPaymentConfirmCommand`, `PgPaymentConfirmResult`, `TossPaymentStatusResponse`

## 6. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출**:
  - commerce: `getOrderInfo` (`RefundSagaOrchestrator.lookupCommerceTotalTickets` — 구버전 `refund.requested` 페이로드 폴백 안전망, 정상 트래픽에선 호출 안 됨, 431b9fe9), `getOrdersByEvent`
  - 외부: PG (Toss `pgPaymentClient`)
  - ✅ 정리됨 (ea44e72): `completePayment`, `failOrder` dead client 2건 + WalletServiceImpl의 CommerceInternalClient 의존성 제거
  - ✅ 단순화 (ea7f7cc9): `RefundSagaOrchestrator`가 fan-out 시점에 호출하던 `getOrderInfo` 동기 호출은 `RefundRequestedEvent.totalOrderTickets` 자체완결로 정상 경로에서 제거됨 (안전망만 잔존)
- **Kafka 구독**: commerce 발행(`refund.completed`, `ticket.issue-failed`, `refund.requested`, `refund.order.done`/`failed`, `refund.ticket.done`/`failed`), event 발행(`event.force-cancelled`, `event.sale-stopped`, `refund.stock.done`/`failed`)

### 피의존 모듈 (호출됨 / 구독됨)

- **REST 피호출**:
  - settlement: `POST /internal/wallet/settlement-deposit` → `depositFromSettlement` (★ 정산 → 예치금)
  - commerce: `getPaymentByOrderId` (`/internal/payments/by-order/{orderId}`)
- **Kafka 피구독**: commerce(`payment.completed`/`failed`/`refund.*`), event(`payment.failed`, `refund.completed`, `refund.stock.restore`), log(`payment.completed` PURCHASE INSERT)

### ⚠ 미결 (모듈 누적 — ServiceOverview §3 / §4 인계)

- `WalletServiceImpl` 전용 메서드 3건 (`claimChargeForRecovery`, `revertTopending`, `applyRecoveryResult`): 인터페이스 외 노출 — `dto-doc-standard.md "Impl 전용"` 분류 대상.

### ✅ 정리 완료 (이전 ⚠ 미결 → 해소)

- `CommerceInternalClient.completePayment` / `failOrder` dead 메서드 2건 — **ea44e72로 제거**. 추가 발견(commit msg): `failOrder`는 client `POST` vs controller `@PatchMapping`으로 빌드 시점부터 깨진 상태였음 (드리프트 패턴 C 사례).
- `WalletService.processBatchRefund` dead stub (인터페이스 + 구현체 + 테스트 91 lines) — **22762f2로 제거**. 강제취소 fan-out은 commerce `RefundFanoutService` → `refund.requested` → payment Refund Saga 경로로 일원화. 동반 정리: `WalletServiceImpl`의 `InternalEventOrdersResponse` / `PaymentStatus` / `CommerceInternalClient` 미사용 의존성 제거.

처리 계획 상세: [ServiceOverview.md §4-1](../ServiceOverview.md) (dead REST 진행 상황), §3 payment 섹션 (Impl 전용 잔존).
