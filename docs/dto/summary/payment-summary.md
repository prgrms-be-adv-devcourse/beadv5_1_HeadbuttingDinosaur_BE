# payment DTO summary

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

presentation/dto 25건 + 외부 PG (Toss) DTO. Payment/Wallet/Refund + Outbox payload.

## Payment — Request

### PaymentReadyRequest (record) ★
- source: `payment/.../payment/presentation/dto/req/PaymentReadyRequest.java`
- 필드: `orderId`, `paymentMethod` (`PaymentMethod` enum: WALLET/PG/WALLET_PG), `walletAmount` (WALLET_PG 시), `pgAmount` (WALLET_PG 시), `totalAmount`

### PaymentConfirmRequest (record) ★
- source: `payment/.../payment/presentation/dto/req/PaymentConfirmRequest.java`
- 필드: `orderId`, `paymentKey`, `amount` (PG confirm)

### PaymentFailRequest (record) ★
- source: `payment/.../payment/presentation/dto/req/PaymentFailRequest.java`
- 필드: `orderId`, `code`, `message`

## Payment — Response

### PaymentReadyResponse / PaymentConfirmResponse / PaymentFailResponse (record)
- source: `payment/.../payment/presentation/dto/res/`
- 핵심 필드: `paymentId`, `status` (`PaymentStatus`)

### InternalPaymentInfoResponse (record)
- source: `payment/.../payment/presentation/dto/res/InternalPaymentInfoResponse.java`
- 사용처: commerce 등 `getPaymentByOrderId` (`/internal/payments/by-order/{orderId}`)

## Wallet — Request

### WalletChargeRequest (record)
- source: `payment/.../wallet/presentation/dto/req/WalletChargeRequest.java`
- 필드: `userId`, `amount`, `chargeId` (idempotency key)

### WalletChargeConfirmRequest (record)
- source: `payment/.../wallet/presentation/dto/req/WalletChargeConfirmRequest.java`
- 필드: `chargeId`, `paymentKey`, `amount`

### WalletChargeFailRequest (record)
- source: `payment/.../wallet/presentation/dto/req/WalletChargeFailRequest.java`
- 필드: `chargeId`, `code`, `message`

### WalletWithdrawRequest (record)
- source: `payment/.../wallet/presentation/dto/req/WalletWithdrawRequest.java`
- 필드: `userId`, `amount`, `bankName`, `accountNumber`

### SettlementDepositRequest (record) ★ (#7)
- source: `payment/.../wallet/presentation/dto/req/SettlementDepositRequest.java`
- 사용처: settlement `POST /internal/wallet/settlement-deposit` 요청
- 필드: `sellerId`, `settlementId`, `amount`

## Wallet — Response

### WalletChargeResponse / WalletChargeConfirmResponse / WalletWithdrawResponse (record)
- source: `payment/.../wallet/presentation/dto/res/`

### WalletBalanceResponse (record)
- source: `payment/.../wallet/presentation/dto/res/WalletBalanceResponse.java`
- 필드: `userId`, `balance`

### WalletTransactionListResponse (record)
- source: `payment/.../wallet/presentation/dto/res/WalletTransactionListResponse.java`
- 필드: `transactions` (List of `transactionType`, `amount`, `createdAt` 등)

## Refund — Response

### RefundInfoResponse (record)
- source: `payment/.../refund/presentation/dto/res/RefundInfoResponse.java`
- 사용처: `GET /api/refunds/info` (사전 환불 정보 조회)

### RefundListResponse (record)
- source: `payment/.../refund/presentation/dto/res/RefundListResponse.java`
- 필드: `refunds` (List of `RefundDetailResponse` 요약형)

### RefundDetailResponse (record)
- source: `payment/.../refund/presentation/dto/res/RefundDetailResponse.java`
- 필드: `refundId`, `orderId`, `paymentMethod`, `refundAmount`, `status`, `createdAt`, `completedAt`

### SellerRefundListResponse (record)
- source: `payment/.../refund/presentation/dto/res/SellerRefundListResponse.java`
- 사용처: `GET /api/seller/refunds/events/{eventId}`

## Kafka payload (Outbox)

`payment/src/main/java/com/devticket/payment/.../messaging/event/**` 또는 `payment/.../refund/application/saga/event/**`

### Producer (8종)
- `PaymentCompletedEvent` ★ (#4) — PG 승인 / WALLET 결제 완료 시
- `PaymentFailedEvent` ★ (#4) — PG 실패 시
- `RefundCompletedEvent` — Refund Saga 마지막 단계
- `RefundOrderCancelEvent` — Saga 시작 (Order 취소 트리거)
- `RefundTicketCancelEvent` — Saga 시작 (Ticket 취소 트리거)
- `RefundStockRestoreEvent` — Ticket 취소 완료 후
- `RefundOrderCompensateEvent` — 보상 saga (Order)
- `RefundTicketCompensateEvent` — 보상 saga (Ticket)

### Saga 내부 record (1종)
- `RefundRequestedEvent`
  - source: `payment/.../refund/application/saga/event/RefundRequestedEvent.java`
  - **13 필드** (`refundId, orderRefundId, orderId, userId, paymentId, paymentMethod, ticketIds, refundAmount, refundRate, wholeOrder, reason, timestamp, totalOrderTickets`)
  - `totalOrderTickets` 자체완결 — 0(구버전)이면 commerce `getOrderInfo` 폴백, 그래도 실패 시 `ticketIds.size()` 최후 폴백

### Consumer record (참고)
- `EventSaleStoppedEvent` (event 발행) — Saga Orchestrator 보상
- `TicketIssueFailedEvent` (commerce 발행) — 결제 환불 처리
- `RefundOrderDoneEvent` / `RefundOrderFailedEvent` (commerce 발행) — Order 보상 응답
- `RefundTicketDoneEvent` / `RefundTicketFailedEvent` (commerce 발행) — Ticket 보상 응답
- `RefundStockDoneEvent` / `RefundStockFailedEvent` (event 발행) — Stock 보상 응답

> 모든 1-B Outbox 이벤트는 afterCommit 직접 발행 + 스케줄러 fallback 패턴. 상세는 `docs/modules/payment.md §4 Outbox 발행 패턴`.

## 외부 PG (Toss)

### PgPaymentConfirmCommand (record)
- source: `payment/.../external/dto/PgPaymentConfirmCommand.java`
- 필드: `paymentKey`, `orderId`, `amount`

### PgPaymentConfirmResult (record)
- source: `payment/.../external/dto/PgPaymentConfirmResult.java`
- PG 측 응답 매핑

### TossPaymentStatusResponse (record)
- source: `payment/.../external/dto/TossPaymentStatusResponse.java`

### TossErrorResponse (record)
- source: `payment/.../external/dto/TossErrorResponse.java`

## PaymentMethod / PaymentStatus enum

### PaymentMethod
- source: `payment/.../payment/domain/enums/PaymentMethod.java`
- 값: `WALLET`, `PG`, `WALLET_PG`

### PaymentStatus
- source: `payment/.../payment/domain/enums/PaymentStatus.java`
- 값: `READY`, `APPROVED`, `FAILED`, `CANCELLED` 등 (코드 검증 필요)

## ⚠ 미결

- `WalletServiceImpl` 전용 메서드 3건 DTO (`claimChargeForRecovery`, `revertTopending`, `applyRecoveryResult` 관련 내부 전달 객체) — `dto-doc-standard.md "Impl 전용"` 분류 대상
