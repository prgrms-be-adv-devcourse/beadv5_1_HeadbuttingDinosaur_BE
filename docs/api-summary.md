# API 문서 요약

자동 생성 기준: `*Controller.java`의 RequestMapping/메서드 매핑을 기반으로 정리했습니다.

## admin

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/admin/dashboard` | `AdminDashboardController#getAdminDashboard` | 관리자 대시보드 통계 API |
| GET | `/admin/events` | `AdminEventController#getEventList` | 관리자 Event 리스트 조회 |
| PATCH | `/admin/events/{eventId}/force-cancel` | `AdminEventController#cancelEvent` | 관리자 Event 삭제 API |
| GET | `/admin/settlements` | `AdminSettlementController#getAdminSettlementList` | 관리자 정산 내역 조회 API |
| POST | `/admin/settlements/run` | `AdminSettlementController#runSettlement` | 관리자 정산 프로세스 실행 |
| GET | `/admin/users` | `AdminUsersController#getUsers` | 회원 목록 조회 |
| PATCH | `/admin/users/{userId}/role` | `AdminUsersController#updateUserRole` | 회원 권한 변경 api |
| PATCH | `/admin/users/{userId}/status` | `AdminUsersController#penalizeUser` | 회원 제재 api |
| GET | `/api/admin/seller-applications` | `AdminSellerController#getSellerApplicationList` | 판매자 신청 리스트 조회 API |
| PATCH | `/api/admin/seller-applications/{applicationId}` | `AdminSellerController#decideApplication` | 판매자 신청 승인/반려 API |

## apigateway

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/health` | `GatewayHealthController#health` | health |

## commerce

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| DELETE | `/api/cart` | `CartController#deleteCartItemAll` | delete cart item all |
| GET | `/api/cart` | `CartController#getCart` | get cart |
| POST | `/api/cart/items` | `CartController#addToCart` | add to cart |
| DELETE | `/api/cart/items/{cartItemId}` | `CartController#deleteCartItem` | delete cart item |
| PATCH | `/api/cart/items/{cartItemId}` | `CartController#updateCartItemQuantity` | update cart item quantity |
| POST | `/api/orders` | `OrderController#createOrderByCart` | create order by cart |
| GET | `/api/orders/{orderId}` | `OrderController#getOrderDetail` | get order detail |
| PATCH | `/api/orders/{orderId}/cancel` | `OrderController#cancelOrder` | cancel order |
| GET | `/api/tickets` | `TicketController#getTicketList` | get ticket list |
| POST | `/api/tickets` | `TicketController#createTickets` | create tickets |
| GET | `/api/tickets/{ticketId}` | `TicketController#getTicketDetail` | get ticket detail |
| GET | `/internal/order-items/by-ticket/{ticketId}` | `InternalOrderController#getOrderItemByTicketId` | get order item by ticket id |
| GET | `/internal/orders/settlement-data` | `InternalOrderController#getSettlementData` | get settlement data |
| GET | `/internal/orders/{id}/items` | `InternalOrderController#getOrderListForSettlement` | get order list for settlement |
| GET | `/internal/orders/{orderId}` | `InternalOrderController#getOrderInfo` | get order info |
| POST | `/internal/orders/{orderId}/payment-completed` | `InternalOrderController#completeOrder` | complete order |
| PATCH | `/internal/orders/{orderId}/payment-failed` | `InternalOrderController#failOrder` | fail order |
| PATCH | `/internal/tickets/{ticketId}/refund-completed` | `InternalOrderController#completeRefund` | complete refund |
| GET | `/seller/events/{eventId}/participants` | `SellerTicketController#getParticipantList` | get participant list |

## event

| HTTP | Path | Controller#Method | 설명 |                                                                                                          
|---|---|---|---|                                                                                                                                   
| GET | `/api/events` | `EventController#getEventList` | get event list (action.log `VIEW` 발행) |                                                  
| POST | `/api/events` | `EventController#createEvent` | create event |
| GET | `/api/events/seller/{eventId}` | `EventController#getSellerEventDetail` | get seller event detail |                                         
| GET | `/api/events/{eventId}` | `EventController#getEvent` | get event (action.log `DETAIL_VIEW` 발행) |
| PATCH | `/api/events/{eventId}` | `EventController#updateEvent` | update event |
| POST | `/api/events/{eventId}/dwell` | `DwellController#reportDwell` | 페이지 이탈 체류 시간 기록 (action.log `DWELL_TIME` 발행, 204 No Content) |
| GET | `/api/events/{eventId}/statistics` | `EventController#getEventSummary` | get event summary |
| POST | `/internal/events/bulk` | `EventInternalController#getBulkEventInfo` | get bulk event info |
| GET | `/internal/events/by-seller/{sellerId}` | `EventInternalController#getEventsBySeller` | get events by seller |
| PATCH | `/internal/events/stock-adjustments` | `EventInternalController#adjustStockBulk` | adjust stock bulk |
| GET | `/internal/events/{eventId}` | `EventInternalController#getEventInfo` | get event info |
| POST | `/internal/events/{eventId}/deduct-stock` | `EventInternalController#deductStock` | deduct stock |
| POST | `/internal/events/{eventId}/restore-stock` | `EventInternalController#restoreStock` | restore stock |
| GET | `/internal/events/{eventId}/validate-purchase` | `EventInternalController#validatePurchase` | validate purchase |
## member

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| POST | `/api/auth/login` | `AuthController#login` | login |
| POST | `/api/auth/logout` | `AuthController#logout` | logout |
| POST | `/api/auth/reissue` | `AuthController#reissue` | reissue |
| POST | `/api/auth/signup` | `AuthController#signup` | signup |
| POST | `/api/auth/social/google` | `AuthController#socialLogin` | social login |
| GET | `/api/members/health` | `MemberController#health` | Member 서비스 헬스 체크 |
| POST | `/api/seller-applications` | `SellerApplicationController#apply` | apply |
| GET | `/api/seller-applications/me` | `SellerApplicationController#getMyApplication` | get my application |
| GET | `/api/tech-stacks` | `TechStackController#getTechStacks` | 기술 스택 목록 조회 |
| DELETE | `/api/users/me` | `UserController#withdraw` | withdraw |
| GET | `/api/users/me` | `UserController#getProfile` | get profile |
| PATCH | `/api/users/me` | `UserController#updateProfile` | update profile |
| PATCH | `/api/users/me/password` | `UserController#changePassword` | change password |
| POST | `/api/users/profile` | `UserController#createProfile` | create profile |
| GET | `/internal/members/seller-applications` | `InternalMemberController#getSellerApplications` | 판매자 신청 목록 조회 |
| PATCH | `/internal/members/seller-applications/{applicationId}` | `InternalMemberController#decideSellerApplication` | decide seller application |
| GET | `/internal/members/{userId}` | `InternalMemberController#getMemberInfo` | get member info |
| GET | `/internal/members/{userId}/role` | `InternalMemberController#getMemberRole` | get member role |
| GET | `/internal/members/{userId}/seller-info` | `InternalMemberController#getSellerInfo` | get seller info |
| GET | `/internal/members/{userId}/status` | `InternalMemberController#getMemberStatus` | get member status |

## payment

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| POST | `/api/payments/ready` | `PaymentController#readyPayment` | 결제 준비 (PG / WALLET / WALLET_PG 분기) |
| POST | `/api/payments/confirm` | `PaymentController#confirm` | PG 결제 승인 (WALLET_PG 분기 포함) |
| POST | `/api/payments/fail` | `PaymentController#fail` | PG 결제 실패 처리 (WALLET_PG 예치금 복구 포함) |
| GET | `/api/refunds` | `RefundController#getRefundList` | 환불 내역 목록 조회 |
| GET | `/api/refunds/info` | `RefundController#getRefundInfo` | 환불 정보 조회 |
| POST | `/api/refunds/pg/{ticketId}` | `RefundController#refundPgTicket` | PG 티켓 환불 |
| GET | `/api/refunds/{refundId}` | `RefundController#getRefundDetail` | 환불 상세 조회 |
| GET | `/api/seller/refunds/events/{eventId}` | `SellerRefundController#getSellerRefundListByEventId` | 판매자 이벤트별 환불 내역 조회 |
| GET | `/api/wallet` | `WalletController#getBalance` | 예치금 잔액 조회 |
| POST | `/api/wallet/charge` | `WalletController#charge` | 예치금 충전 시작 |
| POST | `/api/wallet/charge/confirm` | `WalletController#confirmCharge` | 예치금 충전 승인 |
| GET | `/api/wallet/transactions` | `WalletController#getTransactions` | 예치금 거래 내역 조회 |
| POST | `/api/wallet/withdraw` | `WalletController#withdraw` | 예치금 출금 요청 |
| GET | `/internal/payments/by-order/{orderId}` | `PaymentInternalController#getPaymentByOrderId` | 주문 기반 결제 조회 |

## settlement

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/internal/orders/settlement-data` | `MockCommerceController#getSettlementData` | get settlement data |
| GET | `/seller/settlements` | `SettlementController#getSellerSettlements` | 판매자 정산 내용 조회 |
| GET | `/seller/settlements/fetch` | `SettlementController#fetchSettlementData` | fetch settlement data |
| GET | `/seller/settlements/{settlementId}` | `SettlementController#getSellerSettlement` | 판매자 정산 내용 상세 조회 |
| GET | `/test/batch` | `SettlementController#runBatch` | run batch |

