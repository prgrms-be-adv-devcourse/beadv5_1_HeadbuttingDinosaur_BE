# payment API summary

> ★ = 기능 요구사항 + 기술스택 (`requirements-check.md` §1 / §2)

결제 (PG/WALLET/WALLET_PG 복합 분기) + 환불 saga + 지갑(예치금) 관리.

★ 요구사항 :
- 장바구니 → 예치금 구매 — `payments/ready/confirm/fail` 외부 endpoint + Kafka `payment.completed/failed` 발행
- 매월 정산 — `WalletInternalController#depositFromSettlement` (정산금 → 판매자 예치금 입금)

## 외부 API

| 영역 | HTTP | Path | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|---|
| Payment | POST | `/api/payments/ready` ★ | `PaymentController#readyPayment` | 사용자 (commerce 주문 후) | 결제수단별(PG/WALLET/WALLET_PG) Payment 생성 |
| Payment | POST | `/api/payments/confirm` ★ | `PaymentController#confirm` | 사용자 / PG callback | PG 승인 + `payment.completed` Outbox 발행 |
| Payment | POST | `/api/payments/fail` ★ | `PaymentController#fail` | 사용자 / PG callback | PG 실패 + WALLET_PG 예치금 복구 + `payment.failed` Outbox 발행 |
| Refund | GET | `/api/refunds` | `RefundController#getRefundList` | 사용자 | 환불 목록 |
| Refund | GET | `/api/refunds/info` | `RefundController#getRefundInfo` | 사용자 | 환불 정보 (사전 조회) |
| Refund | GET | `/api/refunds/{refundId}` | `RefundController#getRefundDetail` | 사용자 | 환불 상세 |
| Refund | POST | `/api/refunds/orders/{orderId}` | `RefundController#refundOrder` | 사용자 | 주문 환불 |
| Refund | POST | `/api/refunds/pg/{ticketId}` | `RefundController#refundPgTicket` | 사용자 | PG 티켓 환불 |
| Seller Refund | POST | `/api/seller/events/{eventId}/cancel` | `SellerRefundController#cancelSellerEvent` | 판매자 | 판매자 이벤트 취소 환불 |
| Seller Refund | GET | `/api/seller/refunds/events/{eventId}` | `SellerRefundController#getSellerRefundListByEventId` | 판매자 | 판매자 환불 목록 (이벤트별) |
| Admin Refund | POST | `/api/admin/events/{eventId}/cancel` | `AdminRefundController#cancelAdminEvent` | 관리자 | 관리자 이벤트 취소 환불 |
| Wallet | GET | `/api/wallet` | `WalletController#getBalance` | 사용자 | 예치금 잔액 조회 |
| Wallet | POST | `/api/wallet/charge` | `WalletController#charge` | 사용자 | 예치금 충전 시작 (PG ready) |
| Wallet | POST | `/api/wallet/charge/confirm` | `WalletController#confirmCharge` | 사용자 | 예치금 충전 승인 (PG confirm) |
| Wallet | PATCH | `/api/wallet/charge/{chargeId}/fail` | `WalletController#failCharge` | 사용자 | 예치금 충전 실패 처리 |
| Wallet | POST | `/api/wallet/withdraw` | `WalletController#withdraw` | 사용자 | 예치금 출금 요청 |
| Wallet | GET | `/api/wallet/transactions` | `WalletController#getTransactions` | 사용자 | 예치금 거래 내역 |

## 내부 API

| 영역 | HTTP | Path | Controller#Method | 호출 주체 | 설명 |
|---|---|---|---|---|---|
| Payment Internal | GET | `/internal/payments/by-order/{orderId}` | `PaymentInternalController#getPaymentByOrderId` | (commerce 등) | 주문 ID 로 결제 조회 |
| Wallet Internal | POST | `/internal/wallet/settlement-deposit` ★ | `WalletInternalController#depositFromSettlement` | settlement | 정산금 → 판매자 예치금 입금 |

## Kafka

### 발행 (Producer) — kafka-design §3 line 72

| 토픽 | 분류 | 트리거 |
|---|---|---|
| `payment.completed` ★ | 1-B Outbox | PG 승인 성공 (`confirmPgPayment`) 또는 WALLET 결제 완료 (`processWalletPayment`) |
| `payment.failed` ★ | 1-B Outbox | PG 실패 / 검증 실패 (`failPgPayment`) |
| `refund.completed` | 1-B Outbox | Refund Saga 마지막 단계 완료 (Orchestrator) |
| `refund.order.cancel` / `refund.ticket.cancel` | 1-B Outbox | Saga 시작 또는 보상 트리거 |
| `refund.stock.restore` | 1-B Outbox | Ticket 취소 완료 수신 시 |
| `refund.order.compensate` / `refund.ticket.compensate` | 1-B Outbox | 보상 saga |

### 수신 (Consumer)

| 토픽 | 처리 메서드 | 처리 내용 | 멱등성 |
|---|---|---|---|
| `refund.completed` | `WalletService#restoreBalance` | 예치금 복구 | dedup (transactionKey) |
| `event.sale-stopped` | (Saga Orchestrator) | 보상 흐름 | dedup |
| `ticket.issue-failed` | (Saga Orchestrator) | 결제 환불 처리 | dedup |
| `refund.requested` | `RefundSagaOrchestrator#start` | Saga 시작 — `event.totalOrderTickets()` 우선, 0이면 commerce `getOrderInfo` 폴백, 그래도 실패 시 `ticketIds.size()` 최후 폴백 | dedup |
| `refund.order.done` / `refund.order.failed` | (Orchestrator) | Order 보상 응답 | dedup |
| `refund.ticket.done` / `refund.ticket.failed` | (Orchestrator) | Ticket 보상 응답 | dedup |
| `refund.stock.done` / `refund.stock.failed` | (Orchestrator) | Stock 보상 응답 | dedup |

### Outbox 발행 패턴

상세는 `docs/modules/payment.md §4 Outbox 발행 패턴` 참조 (afterCommit + 스케줄러 fallback).

### WALLET_PG 결제수단

event `PaymentMethod` enum 에 `WALLET_PG` 추가됨 — payment 가 발행하는 `RefundCompletedEvent.paymentMethod` 에 `WALLET_PG` 가 실릴 수 있도록 정합성 확보.

## 호출 의존성

### 호출 (REST)

- commerce: `getOrderInfo` (`RefundSagaOrchestrator.lookupCommerceTotalTickets` — 폴백 안전망), `getOrdersByEvent`
- 외부: PG (Toss `pgPaymentClient`)

### 피호출 (REST)

- settlement: `POST /internal/wallet/settlement-deposit` ★ → `depositFromSettlement`
- commerce: `getPaymentByOrderId`


## 인프라 / 구조

- **WALLET_PG 복합 결제**: `PaymentServiceImpl.readyPayment` 가 결제수단별 분기, `WalletPgTimeoutScheduler` 가 READY 상태 만료된 WALLET_PG 결제 정리 (예치금 환원). WALLET_PG 재시도 시 USE 키 무효화 처리.
- **RefundSaga totalOrderTickets 자체완결**: payload 진화로 동기 HTTP 호출 제거.
