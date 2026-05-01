# payment API summary

> 본 문서는 `docs/api/api-overview.md §9 payment` 의 깊이 확장판.
> 결제 (PG/WALLET/WALLET_PG 복합 분기) + 환불 saga + 지갑(예치금) 관리.

## 외부 API

| 영역 | HTTP | Path | Controller#Method | 요청 DTO | 응답 DTO | 호출 주체 | 설명 |
|---|---|---|---|---|---|---|---|
| Payment | POST | `/api/payments/ready` ★ | `PaymentController#readyPayment` | `PaymentReadyRequest` | `PaymentReadyResponse` | 사용자 (commerce 주문 후) | 결제수단별(PG/WALLET/WALLET_PG) Payment 생성 |
| Payment | POST | `/api/payments/confirm` ★ | `PaymentController#confirm` | `PaymentConfirmRequest` | `PaymentConfirmResponse` | 사용자 / PG callback | PG 승인 + `payment.completed` Outbox 발행 |
| Payment | POST | `/api/payments/fail` ★ | `PaymentController#fail` | `PaymentFailRequest` | `PaymentFailResponse` | 사용자 / PG callback | PG 실패 + WALLET_PG 예치금 복구 + `payment.failed` Outbox 발행 |
| Refund | GET | `/api/refunds` | `RefundController#getRefundList` | (query) | `RefundListResponse` | 사용자 | 환불 목록 |
| Refund | GET | `/api/refunds/info` | `RefundController#getRefundInfo` | (query: orderId 등) | `RefundInfoResponse` | 사용자 | 환불 정보 (사전 조회) |
| Refund | GET | `/api/refunds/{refundId}` | `RefundController#getRefundDetail` | - | `RefundDetailResponse` | 사용자 | 환불 상세 |
| Refund | POST | `/api/refunds/orders/{orderId}` | `RefundController#refundOrder` | - | - | 사용자 | 주문 환불 |
| Refund | POST | `/api/refunds/pg/{ticketId}` | `RefundController#refundPgTicket` | - | - | 사용자 | PG 티켓 환불 |
| Seller Refund | POST | `/api/seller/events/{eventId}/cancel` | `SellerRefundController#cancelSellerEvent` | - | - | 판매자 | 판매자 이벤트 취소 환불 |
| Seller Refund | GET | `/api/seller/refunds/events/{eventId}` | `SellerRefundController#getSellerRefundListByEventId` | - | `SellerRefundListResponse` | 판매자 | 판매자 환불 목록 (이벤트별) |
| Admin Refund | POST | `/api/admin/events/{eventId}/cancel` | `AdminRefundController#cancelAdminEvent` | - | - | 관리자 | 관리자 이벤트 취소 환불 (admin → event 강제취소 흐름과 별도 진입점) |
| Wallet | GET | `/api/wallet` | `WalletController#getBalance` | - | `WalletBalanceResponse` | 사용자 | 예치금 잔액 조회 |
| Wallet | POST | `/api/wallet/charge` | `WalletController#charge` | `WalletChargeRequest` | `WalletChargeResponse` | 사용자 | 예치금 충전 시작 (PG ready) |
| Wallet | POST | `/api/wallet/charge/confirm` | `WalletController#confirmCharge` | `WalletChargeConfirmRequest` | `WalletChargeConfirmResponse` | 사용자 | 예치금 충전 승인 (PG confirm) |
| Wallet | PATCH | `/api/wallet/charge/{chargeId}/fail` | `WalletController#failCharge` | `WalletChargeFailRequest` | - | 사용자 | 예치금 충전 실패 처리 |
| Wallet | POST | `/api/wallet/withdraw` | `WalletController#withdraw` | `WalletWithdrawRequest` | `WalletWithdrawResponse` | 사용자 | 예치금 출금 요청 |
| Wallet | GET | `/api/wallet/transactions` | `WalletController#getTransactions` | (query) | `WalletTransactionListResponse` | 사용자 | 예치금 거래 내역 |

## 내부 API

| 영역 | HTTP | Path | Controller#Method | 응답 DTO | 호출 주체 | 설명 |
|---|---|---|---|---|---|---|
| Payment Internal | GET | `/internal/payments/by-order/{orderId}` | `PaymentInternalController#getPaymentByOrderId` | `InternalPaymentInfoResponse` | (commerce 등) | 주문 ID 로 결제 조회 |
| Wallet Internal | POST | `/internal/wallet/settlement-deposit` ★ | `WalletInternalController#depositFromSettlement` | - | settlement | 정산금 → 판매자 예치금 입금 |

> ⚠ Mock / Test 컨트롤러 (운영 외):
> - `MockCommerceController` (`@Profile("test")`): `/internal/order-items/by-ticket/{ticketId}`, `/internal/orders/{orderId}`, `/mock/wallet/charge` — 테스트 프로파일에서만 활성
> - `MockEventController`, `PaymentTestController`, `RefundTestController`: `@RequestMapping` / `@Profile` 모두 주석 처리 → **dead** (제거 후속)
> - `MockPgController`: PG mock — local/dev 용도

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
| ⚠ ~~`event.force-cancelled`~~ | ~~`WalletService#processBatchRefund`~~ | **22762f2 로 제거**. 강제취소 fan-out 은 commerce.RefundFanoutService → `refund.requested` → payment Saga 경유로 일원화 | — |
| `event.sale-stopped` | (Saga Orchestrator) | 보상 흐름 | dedup |
| `ticket.issue-failed` | (Saga Orchestrator) | 결제 환불 처리 | dedup |
| `refund.requested` | `RefundSagaOrchestrator#start` | Saga 시작 — `event.totalOrderTickets()` 우선 사용(ea7f7cc9), 0(구버전 페이로드)이면 commerce `getOrderInfo` 폴백(431b9fe9), 그래도 실패 시 `ticketIds.size()` 최후 폴백 | dedup |
| `refund.order.done` / `refund.order.failed` | (Orchestrator) | Order 보상 응답 | dedup |
| `refund.ticket.done` / `refund.ticket.failed` | (Orchestrator) | Ticket 보상 응답 | dedup |
| `refund.stock.done` / `refund.stock.failed` | (Orchestrator) | Stock 보상 응답 | dedup |

> ⚠ `RefundRequestedEvent` 실 필드는 코드 기준 13개 (`refundId, orderRefundId, orderId, userId, paymentId, paymentMethod, ticketIds, refundAmount, refundRate, wholeOrder, reason, timestamp, totalOrderTickets`). kafka-design §3 line 298-311 정의(8 필드)와 드리프트 — 실 정의는 `payment/.../refund/application/saga/event/RefundRequestedEvent.java` 참조.

### Outbox 발행 패턴

상세는 `docs/modules/payment.md §4 Outbox 발행 패턴` 참조 (afterCommit + 스케줄러 fallback, publish-grace 5s, poll 60s, 057ddf6d/dc383f70).

### WALLET_PG 결제수단

✅ event `PaymentMethod` enum 에 `WALLET_PG` 추가 (f3f61b55) — payment 가 발행하는 `RefundCompletedEvent.paymentMethod` 에 `WALLET_PG` 가 실릴 수 있도록 정합성 확보.

## 호출 의존성

### 호출 (REST)

- commerce: `getOrderInfo` (`RefundSagaOrchestrator.lookupCommerceTotalTickets` — 구버전 `refund.requested` 페이로드 폴백 안전망 431b9fe9), `getOrdersByEvent`
- 외부: PG (Toss `pgPaymentClient`)

> ✅ 정리됨 (ea44e72): `completePayment`, `failOrder` dead client 2건 + `WalletServiceImpl` 의 `CommerceInternalClient` 의존성 제거.
> ✅ 단순화 (ea7f7cc9): `RefundSagaOrchestrator` 가 fan-out 시점에 호출하던 `getOrderInfo` 동기 호출은 `RefundRequestedEvent.totalOrderTickets` 자체완결로 정상 경로에서 제거 (안전망만 잔존).

### 피호출 (REST)

- settlement: `POST /internal/wallet/settlement-deposit` → `depositFromSettlement` (★ 정산 → 예치금)
- commerce: `getPaymentByOrderId` (`/internal/payments/by-order/{orderId}`)

## DTO 발췌

- **Payment**: `PaymentReadyRequest`, `PaymentConfirmRequest`, `PaymentFailRequest` / `PaymentReadyResponse`, `PaymentConfirmResponse`, `PaymentFailResponse`, `InternalPaymentInfoResponse`
- **Wallet**: `WalletChargeRequest`, `WalletChargeConfirmRequest`, `WalletChargeFailRequest`, `WalletWithdrawRequest`, `SettlementDepositRequest` / `WalletChargeResponse`, `WalletChargeConfirmResponse`, `WalletWithdrawResponse`, `WalletBalanceResponse`, `WalletTransactionListResponse`
- **Refund**: `RefundInfoResponse`, `RefundListResponse`, `RefundDetailResponse`, `SellerRefundListResponse`
- **Kafka payload (Outbox)**: `PaymentCompletedEvent`, `PaymentFailedEvent`, `RefundCompletedEvent`, `RefundOrderCancelEvent`, `RefundTicketCancelEvent`, `RefundStockRestoreEvent`, `RefundOrderCompensateEvent`, `RefundTicketCompensateEvent`, Saga 내부 record `RefundRequestedEvent` (★ 13 필드)
- **외부 PG (Toss)**: `PgPaymentConfirmCommand`, `PgPaymentConfirmResult`, `TossPaymentStatusResponse`, `TossErrorResponse`

> DTO 필드 표 / source 경로 깊이: `docs/dto/summary/payment-summary.md`

## ⚠ 미결 / 후속

- `WalletServiceImpl` 전용 메서드 3건 (`claimChargeForRecovery`, `revertTopending`, `applyRecoveryResult`): 인터페이스 외 노출 — `dto-doc-standard.md "Impl 전용"` 분류 대상
- ✅ 정리됨 (ea44e72): `CommerceInternalClient.completePayment`/`failOrder` dead 메서드 제거
- ✅ 정리됨 (22762f2): `WalletService.processBatchRefund` dead stub 제거 (강제취소 fan-out 은 commerce → payment Refund Saga 일원화)

## 신규 인프라 / 구조 변경 (참고)

- **WALLET_PG 복합 결제**: `PaymentServiceImpl.readyPayment` 가 결제수단별 분기, `WalletPgTimeoutScheduler` 가 READY 상태 만료된 WALLET_PG 결제 정리 (예치금 환원). `WALLET_PG 재시도 시 USE 키 무효화` 적용 (cfc20d0c)
- **RefundSaga totalOrderTickets 자체완결**: 31fa70ba/e3d316ac/ea7f7cc9 — payload 진화로 동기 HTTP 호출 제거
