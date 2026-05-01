# payment

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

## 1. 모듈 책임

결제 처리 (PG / WALLET / WALLET_PG 복합 분기) + 환불 처리 + 지갑(예치금) 관리(충전·출금·정산금 입금·환불 복구).

★ 요구사항 :
- 장바구니 → 예치금 구매 — WALLET / WALLET_PG 결제 흐름
- 매월 정산 — `WalletInternalController.depositFromSettlement` (정산금 → 판매자 예치금 입금)

**위임 (담당 안 함)**:
- 주문 상태 관리 → commerce 모듈 (Outbox/Kafka 통지)
- 재고 차감 / 검증 → event 모듈
- 정산서 생성 → settlement 모듈

## 2. 외부 API

상세는 [api/summary/payment-summary.md](../api/summary/payment-summary.md) 참조.

| 메서드 | 경로 | Controller | Service 1줄 |
|---|---|---|---|
| POST | `/api/payments/ready` ★ | `PaymentController.readyPayment` | 주문 검증 후 결제수단별(PG/WALLET/WALLET_PG) Payment 를 생성한다 |
| POST | `/api/payments/confirm` ★ | `PaymentController.confirm` | PG 승인 후 Payment APPROVED + `payment.completed` Outbox 를 발행한다 |
| POST | `/api/payments/fail` ★ | `PaymentController.fail` | PG 실패 반영 + WALLET_PG 면 예치금 복구 + `payment.failed` Outbox 를 발행한다 |
| GET | `/api/wallet` | `WalletController.getBalance` | 예치금 잔액 조회 |
| POST | `/api/wallet/charge` | `WalletController.charge` | 예치금 충전 시작 |
| POST | `/api/wallet/charge/confirm` | `WalletController.confirmCharge` | 예치금 충전 승인 |
| POST | `/api/refunds/orders/{orderId}` | `RefundController.refundOrder` | 주문 환불 |
| POST | `/api/refunds/pg/{ticketId}` | `RefundController.refundPgTicket` | PG 티켓 환불 |
| POST | `/api/admin/events/{eventId}/cancel` | `AdminRefundController.cancelAdminEvent` | 관리자 이벤트 취소 환불 |
| POST | `/api/seller/events/{eventId}/cancel` | `SellerRefundController.cancelSellerEvent` | 판매자 이벤트 취소 환불 |

**대상 구분**: 일반 사용자(payments/wallet/refunds), 판매자(seller/refunds, seller/events 취소), 관리자(admin/events 취소).

## 3. 내부 API (다른 서비스가 호출)

| 메서드 | 경로 | Controller | 호출 주체 | 비고 |
|---|---|---|---|---|
| GET | `/internal/payments/by-order/{orderId}` | `PaymentInternalController.getPaymentByOrderId` | (commerce 등) | 주문 ID 로 결제 조회 |
| POST | `/internal/wallet/settlement-deposit` ★ | `WalletInternalController.depositFromSettlement` | settlement | 정산금 → 판매자 예치금 입금 |

## 4. Kafka

### 발행 (Producer) — kafka-design §3 line 72

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
   - 비즈니스 `@Transactional` 안에서 Outbox row 가 `PENDING` 으로 저장 → 커밋 직후 `afterCommit` 훅이 별도 executor 로 발행 위임 (`OutboxAsyncConfig`) → `OutboxEventProducer.publish` 호출 후 별도 `REQUIRES_NEW` 트랜잭션으로 row `SENT` 전이.
   - 어느 단계의 예외도 throw 하지 않고 `warn` 로그만 — 비즈니스 TX 영향 없음.
2. **OutboxScheduler fallback — 보완 경로**
   - executor reject / Kafka 장애 / `markSent` 실패 / 프로세스 다운 시 `PENDING` row 잔존 → 스케줄러 흡수.
   - 설정(`payment/src/main/resources/application.yml`):
     ```yaml
     outbox:
       publish-grace-seconds: 5
       poll-interval-ms: 60000
     ```
   - 중복 발행은 consumer 측 `X-Message-Id` dedup 으로 무해화.

### 수신 (Consumer) — kafka-design §3 line 72

| 토픽 | 처리 메서드 | 처리 내용 | 멱등성 |
|---|---|---|---|
| `refund.completed` | `WalletService.restoreBalance` | 예치금 복구 | dedup (transactionKey) |
| `event.sale-stopped` | (Saga Orchestrator) | 보상 흐름 | dedup |
| `ticket.issue-failed` | (Saga Orchestrator) | 결제 환불 처리 | dedup |
| `refund.requested` | `RefundSagaOrchestrator.start` | Saga 시작 — `event.totalOrderTickets()` 우선 사용, 0이면 commerce `getOrderInfo` 폴백, 그래도 실패 시 `ticketIds.size()` 최후 폴백 | dedup |
| `refund.order.done` / `refund.order.failed` | (Orchestrator) | Order 보상 응답 | dedup |
| `refund.ticket.done` / `refund.ticket.failed` | (Orchestrator) | Ticket 보상 응답 | dedup |
| `refund.stock.done` / `refund.stock.failed` | (Orchestrator) | Stock 보상 응답 | dedup |

## 5. DTO

상세는 [dto/summary/payment-summary.md](../dto/summary/payment-summary.md) 참조. 핵심 발췌:

- **Payment**: `PaymentReadyRequest/Response`, `PaymentConfirmRequest/Response`, `PaymentFailRequest/Response`, `InternalPaymentInfoResponse`
- **Wallet**: `WalletChargeRequest/Response`, `WalletChargeConfirmRequest/Response`, `WalletWithdrawRequest/Response`, `WalletBalanceResponse`, `WalletTransactionListResponse`, `SettlementDepositRequest`
- **Refund**: `RefundInfoResponse`, `RefundListResponse`, `RefundDetailResponse`, `SellerRefundListResponse`
- **Kafka payload (Outbox)**: `PaymentCompletedEvent`, `PaymentFailedEvent`, `RefundCompletedEvent`, `RefundRequestedEvent` (Saga 내부 record, 13 필드)
- **외부 PG**: `PgPaymentConfirmCommand`, `PgPaymentConfirmResult`, `TossPaymentStatusResponse`

## 6. 의존성

### 의존하는 모듈 (호출 / 구독)

- **REST 호출**:
  - commerce: `getOrderInfo` (`RefundSagaOrchestrator.lookupCommerceTotalTickets` — 구버전 페이로드 폴백 안전망)
  - 외부: PG (Toss `pgPaymentClient`)
- **Kafka 구독**: commerce 발행(`refund.completed`, `ticket.issue-failed`, `refund.requested`, `refund.order.done`/`failed`, `refund.ticket.done`/`failed`), event 발행(`event.force-cancelled`, `event.sale-stopped`, `refund.stock.done`/`failed`)

### 피의존 모듈 (호출됨 / 구독됨)

- **REST 피호출**:
  - settlement: `POST /internal/wallet/settlement-deposit` ★ → `depositFromSettlement`
  - commerce: `getPaymentByOrderId`
- **Kafka 피구독**: commerce(`payment.completed` ★ / `payment.failed` ★ / `refund.*`), event(`payment.failed` ★, `refund.completed`, `refund.stock.restore`), log(`payment.completed` ★ — PURCHASE INSERT)
