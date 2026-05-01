# 구현된 서비스 현황 (메서드별 1줄 요약)

## admin / AdminEventService

- `getEventList`: get event list 기능을 제공.
- `forceCancel`: Event 모듈에 강제취소 REST 호출 + admin 이력을 저장한다 (`event.force-cancelled` 간접 트리거).

## admin / AdminEventServiceImpl

- `getEventList`: get event list 기능을 제공.
- `forceCancel`: force cancel 기능을 제공.

## admin / AdminSellerService

- `getSellerApplicationList`: get seller application list 기능을 제공.
- `decideApplication`: decide application 기능을 제공.

## admin / AdminSellerServiceImpl

- `getSellerApplicationList`: get seller application list 기능을 제공.
- `decideApplication`: decide application 기능을 제공.

## admin / AdminSettlementService

- `getSettlementList`: get settlement list 기능을 제공.
- `runSettlement`: Settlement 측 정산 실행 호출 + admin 이력을 저장한다.

## admin / AdminSettlementServiceImpl

- `getSettlementList`: get settlement list 기능을 제공.
- `runSettlement`: run settlement 기능을 제공.

## admin / AdminUserService

- `getMembers`: get members 기능을 제공.
- `getUserDetail`: get user detail 기능을 제공.
- `penalizeUser`: penalize user 기능을 제공.
- `updateUserRole`: update user role 기능을 제공.

## admin / AdminUserServiceImpl

- `getMembers`: get members 기능을 제공.
- `getUserDetail`: get user detail 기능을 제공.
- `penalizeUser`: penalize user 기능을 제공.
- `updateUserRole`: update user role 기능을 제공.

## admin / TechStackService

- `getTechStacks`: get tech stacks 기능을 제공.
- `createTechStack`: create tech stack 기능을 제공.
- `updateTechStack`: update tech stack 기능을 제공.
- `deleteTechStack`: delete tech stack 기능을 제공.
- `reindexEmptyEmbeddings`: reindex empty embeddings 기능을 제공.

## admin / TechStackServiceImpl

- `getTechStacks`: get tech stacks 기능을 제공.
- `createTechStack`: create tech stack 기능을 제공.
- `updateTechStack`: update tech stack 기능을 제공.
- `deleteTechStack`: delete tech stack 기능을 제공.
- `reindexEmptyEmbeddings`: reindex empty embeddings 기능을 제공.

## ai / RecentVectorService

- `recalculateRecentVector`: recalculate recent vector 기능을 제공.

## ai / RecommendationService

- `recommendByUserVector`: recommend by user vector 기능을 제공.
- `recommendByColdStart`: recommend by cold start 기능을 제공.
- `searchKnn`: search knn 기능을 제공.

## ai / VectorService

- `updatePreferenceVector`: update preference vector 기능을 제공.
- `updateRefund`: update refund 기능을 제공.
- `updateCartVector`: update cart vector 기능을 제공.
- `updateNegativeVector`: update negative vector 기능을 제공.

## commerce / CartService

- `findByUserId`: find by user id 기능을 제공.
- `save`: save 기능을 제공.
- `getCart`: 사용자 장바구니를 조회한다 (Cart 없으면 빈 응답).
- `clearCart`: 장바구니 전체를 비우고 CART_REMOVE를 발행한다.
- `updateTicket`: 장바구니 아이템 수량을 증감한다.
- `deleteTicket`: 장바구니 아이템을 단건 삭제한다.

## commerce / OrderExpirationCancelService

- `cancelOrder`: cancel order 기능을 제공.

## commerce / OrderService

- `createOrderByCart`: 장바구니로 주문을 생성하고 재고를 차감한다.
- `getOrderStatus`: 본인 주문의 상태를 조회한다.
- `getOrderDetail`: 본인 주문의 상세를 조회한다.
- `getOrderList`: get order list 기능을 제공.
- `getOrderInfo`: get order info 기능을 제공.
- `getOrderListForSettlement`: get order list for settlement 기능을 제공.
- `getSettlementData`: get settlement data 기능을 제공.
- `cancelOrder`: 결제 전 주문을 취소하고 재고를 복구한다 (PAID 차단).
- `processPaymentCompleted`: `payment.completed` 수신, PAID 전이 + 티켓 발급 + 카트 분기 삭제한다.
- `processPaymentFailed`: `payment.failed` 수신, FAILED로 전이한다.
- `processStockDeducted`: stock.deducted 수신 시 dedup 만 수행하는 stub.
- `getOrderItemByTicketId`: get order item by ticket id 기능을 제공.
- `getOrderTickets`: 환불 산정용 주문 티켓 목록을 조회한다.

## commerce / RefundFanoutService

- `processEventForceCancelled`: process event force cancelled 기능을 제공.

## commerce / RefundOrderService

- `processOrderRefundCancel`: process order refund cancel 기능을 제공.
- `processOrderCompensate`: process order compensate 기능을 제공.
- `processRefundCompleted`: process refund completed 기능을 제공.

## commerce / RefundTicketService

- `processTicketRefundCancel`: process ticket refund cancel 기능을 제공.
- `processTicketCompensate`: process ticket compensate 기능을 제공.

## commerce / TicketService

- `getTicketList`: 사용자 티켓 목록을 조회한다.
- `getTicketDetail`: 티켓 단건 상세를 조회한다.
- `createTicket`: 주문의 OrderItem 수량만큼 티켓을 일괄 발행한다.
- `getSettlementData`: get settlement data 기능을 제공.
- `getParticipantList`: get participant list 기능을 제공.

## payment / PaymentService

- `readyPayment`: 주문 검증 후 결제수단별(PG/WALLET/WALLET_PG) Payment를 생성한다.
- `confirmPgPayment`: PG 승인 후 Payment APPROVED + `payment.completed` Outbox를 발행한다.
- `failPgPayment`: PG 실패 반영 + WALLET_PG면 예치금 복구 + `payment.failed` Outbox를 발행한다.
- `getPaymentByOrderId`: get payment by order id 기능을 제공.

## payment / PaymentServiceImpl

- `readyPayment`: ready payment 기능을 제공.
- `confirmPgPayment`: confirm pg payment 기능을 제공.
- `failPgPayment`: fail pg payment 기능을 제공.
- `getPaymentByOrderId`: get payment by order id 기능을 제공.

## payment / RefundService

- `getRefundInfo`: get refund info 기능을 제공.
- `cancelSellerEvent`: cancel seller event 기능을 제공.
- `cancelAdminEvent`: cancel admin event 기능을 제공.
- `refundPgTicket`: refund pg ticket 기능을 제공.
- `refundOrder`: refund order 기능을 제공.
- `getRefundList`: get refund list 기능을 제공.
- `getRefundDetail`: get refund detail 기능을 제공.
- `getSellerRefundListByEventId`: get seller refund list by event id 기능을 제공.

## payment / RefundServiceImpl

- `getRefundInfo`: get refund info 기능을 제공.
- `refundPgTicket`: refund pg ticket 기능을 제공.
- `refundOrder`: refund order 기능을 제공.
- `cancelSellerEvent`: cancel seller event 기능을 제공.
- `cancelAdminEvent`: cancel admin event 기능을 제공.
- `getRefundList`: get refund list 기능을 제공.
- `getRefundDetail`: get refund detail 기능을 제공.
- `getSellerRefundListByEventId`: get seller refund list by event id 기능을 제공.

## payment / WalletChargeTransactionService

- `getWallet`: get wallet 기능을 제공.
- `createChargeWithLimitCheck`: create charge with limit check 기능을 제공.
- `claimChargeForProcessing`: claim charge for processing 기능을 제공.
- `failProcessingCharge`: fail processing charge 기능을 제공.
- `completeChargeAfterPg`: complete charge after pg 기능을 제공.

## payment / WalletService

- `charge`: charge 기능을 제공.
- `confirmCharge`: confirm charge 기능을 제공.
- `failCharge`: fail charge 기능을 제공.
- `withdraw`: withdraw 기능을 제공.
- `getBalance`: get balance 기능을 제공.
- `getTransactions`: get transactions 기능을 제공.
- `processWalletPayment`: 예치금 차감 + WalletTransaction 기록 + `payment.completed` Outbox를 발행한다.
- `restoreBalance`: restore balance 기능을 제공.
- `deductForWalletPg`: deduct for wallet pg 기능을 제공.
- `restoreForWalletPgFail`: restore for wallet pg fail 기능을 제공.
- ~~`processBatchRefund`~~: ✅ 22762f2로 인터페이스/구현체/테스트 제거 — service-status.md 자동 재생성 시 행 제거 예정.
- `recoverStalePendingCharge`: recover stale pending charge 기능을 제공.
- `depositFromSettlement`: 정산 요청 수신, 판매자 예치금에 정산금을 충전한다.

## payment / WalletServiceImpl

- `charge`: charge 기능을 제공.
- `confirmCharge`: confirm charge 기능을 제공.
- `failCharge`: fail charge 기능을 제공.
- `withdraw`: withdraw 기능을 제공.
- `getBalance`: get balance 기능을 제공.
- `getTransactions`: get transactions 기능을 제공.
- `processWalletPayment`: process wallet payment 기능을 제공.
- `restoreBalance`: restore balance 기능을 제공.
- `deductForWalletPg`: deduct for wallet pg 기능을 제공.
- `restoreForWalletPgFail`: restore for wallet pg fail 기능을 제공.
- `processBatchRefund`: process batch refund 기능을 제공.
- `recoverStalePendingCharge`: recover stale pending charge 기능을 제공.
- `claimChargeForRecovery`: claim charge for recovery 기능을 제공.
- `revertTopending`: revert topending 기능을 제공.
- `applyRecoveryResult`: apply recovery result 기능을 제공.
- `depositFromSettlement`: deposit from settlement 기능을 제공.

## settlement / SettlementAdminService

- `getSettlements`: get settlements 기능을 제공.
- `runSettlement`: Commerce 호출로 판매자별 정산서를 생성한다 (현 코드는 controller 본문에서 주석 처리, `createSettlementFromItems` 위임).
- `createSettlementFromItems`: SettlementItem 기반 월별 Batch로 판매자별 정산서를 생성한다.
- `getSettlementDetail`: get settlement detail 기능을 제공.
- `cancelSettlement`: cancel settlement 기능을 제공.
- `processPayment`: Payment 측 예치금 전환 호출 후 Settlement와 이월건을 PAID로 전이한다.
- `getMonthlyRevenue`: 관리자 월별 수익 조회.

## settlement / SettlementAdminServiceImpl

- `getSettlements`: get settlements 기능을 제공.
- `runSettlement`: run settlement 기능을 제공.
- `createSettlementFromItems`: create settlement from items 기능을 제공.
- `getSettlementDetail`: get settlement detail 기능을 제공.
- `cancelSettlement`: cancel settlement 기능을 제공.
- `processPayment`: process payment 기능을 제공.
- `getMonthlyRevenue`: get monthly revenue 기능을 제공.

## settlement / SettlementService

- `fetchSettlementData`: Commerce에서 판매자/기간별 정산 데이터를 조회한다.
- `getSellerSettlements`: get seller settlements 기능을 제공.
- `getSellerSettlementDetail`: get seller settlement detail 기능을 제공.
- `previewSettlementTarget`: preview settlement target 기능을 제공.
- `collectSettlementTargets`: collect settlement targets 기능을 제공.
- `getSettlementByPeriod`: get settlement by period 기능을 제공.
- `getSettlementPreview`: get settlement preview 기능을 제공.

## settlement / SettlementServiceImpl

- `fetchSettlementData`: fetch settlement data 기능을 제공.
- `getSellerSettlements`: get seller settlements 기능을 제공.
- `getSellerSettlementDetail`: get seller settlement detail 기능을 제공.
- `previewSettlementTarget`: preview settlement target 기능을 제공.
- `collectSettlementTargets`: collect settlement targets 기능을 제공.
- `getSettlementByPeriod`: get settlement by period 기능을 제공.
- `getSettlementPreview`: get settlement preview 기능을 제공.

