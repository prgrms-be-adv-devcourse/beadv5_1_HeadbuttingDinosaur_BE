# API 문서 요약

자동 생성 기준: `*Controller.java`의 RequestMapping/메서드 매핑을 기반으로 정리했습니다.

## admin

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/api/admin/dashboard` | `AdminDashboardController#getAdminDashboard` | 관리자 대시보드 통계 API |
| GET | `/api/admin/events` | `AdminEventController#getEventList` | 관리자 Event 리스트 조회 |
| PATCH | `/api/admin/events/{eventId}/force-cancel` | `AdminEventController#cancelEvent` | 관리자 Event 삭제 API |
| GET | `/api/admin/seller-applications` | `AdminSellerController#getSellerApplicationList` | 판매자 신청 리스트 조회 API |
| PATCH | `/api/admin/seller-applications/{applicationId}` | `AdminSellerController#decideApplication` | 판매자 신청 승인/반려 API |
| GET | `/api/admin/settlements` | `AdminSettlementController#getAdminSettlementList` | 관리자 정산 내역 조회 API |
| POST | `/api/admin/settlements/run` | `AdminSettlementController#runSettlement` | 관리자 정산 프로세스 실행 |
| GET | `/api/admin/techstacks` | `TechStackController#getTechStacks` | TechStack 전체 조회 |
| POST | `/api/admin/techstacks` | `TechStackController#createTechStack` | TechStack 생성 |
| POST | `/api/admin/techstacks/reindex` | `TechStackController#reindexEmptyEmbeddings` | reindex empty embeddings |
| DELETE | `/api/admin/techstacks/{id}` | `TechStackController#deleteTechStack` | TechStack 삭제 |
| PUT | `/api/admin/techstacks/{id}` | `TechStackController#updateTechStack` | TechStack 수정 |
| GET | `/api/admin/users` | `AdminUsersController#getUsers` | 회원 목록 조회 |
| GET | `/api/admin/users/{userId}` | `AdminUsersController#getUserDetail` | get user detail |
| PATCH | `/api/admin/users/{userId}/role` | `AdminUsersController#updateUserRole` | 회원 권한 변경 api |
| PATCH | `/api/admin/users/{userId}/status` | `AdminUsersController#penalizeUser` | 회원 제재 api |

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
| GET | `/api/seller/events/{eventId}/participants` | `SellerTicketController#getParticipantList` | get participant list |
| GET | `/api/tickets` | `TicketController#getTicketList` | get ticket list |
| POST | `/api/tickets` | `TicketController#createTickets` | create tickets |
| GET | `/api/tickets/{ticketId}` | `TicketController#getTicketDetail` | get ticket detail |
| GET | `/internal/order-items/by-ticket/{ticketId}` | `InternalOrderController#getOrderItemByTicketId` | get order item by ticket id |
| GET | `/internal/orders/settlement-data` | `InternalOrderController#getSettlementData` | get settlement data |
| GET | `/internal/orders/{id}/items` | `InternalOrderController#getOrderListForSettlement` | get order list for settlement |
| GET | `/internal/orders/{orderId}` | `InternalOrderController#getOrderInfo` | get order info |
| POST | `/internal/orders/{orderId}/payment-completed` | `InternalOrderController#completeOrder` | complete order |
| PATCH | `/internal/orders/{orderId}/payment-failed` | `InternalOrderController#failOrder` | fail order |
| GET | `/internal/orders/{orderId}/tickets` | `InternalOrderController#getOrderTickets` | get order tickets |
| POST | `/internal/tickets/settlement-data` | `InternalTicketController#getSettlementData` | get settlement data |

## event

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/api/events` | `EventController#getEventList` | get event list |
| GET | `/api/events/user/recommendations` | `EventController#getRecommendations` | get recommendations |
| GET | `/api/events/{eventId}` | `EventController#getEvent` | get event |
| POST | `/api/events/{eventId}/dwell` | `DwellController#reportDwell` | report dwell |
| GET | `/api/seller/events` | `SellerEventController#getSellerEvents` | get seller events |
| POST | `/api/seller/events` | `SellerEventController#createEvent` | create event |
| GET | `/api/seller/events/{eventId}` | `SellerEventController#getSellerEventDetail` | get seller event detail |
| PATCH | `/api/seller/events/{eventId}` | `SellerEventController#updateEvent` | update event |
| GET | `/api/seller/events/{eventId}/statistics` | `SellerEventController#getEventSummary` | get event summary |
| POST | `/api/seller/images/upload` | `SellerImageUploadController#uploadImage` | upload image |
| GET | `/internal/events` | `EventInternalController#getEvents` | get events |
| POST | `/internal/events/bulk` | `EventInternalController#getBulkEventInfo` | get bulk event info |
| GET | `/internal/events/by-seller/{sellerId}` | `EventInternalController#getEventsBySeller` | get events by seller |
| GET | `/internal/events/by-seller/{sellerId}/settlement` | `EventInternalController#getEventsBySellerForSettlement` | get events by seller for settlement |
| GET | `/internal/events/ended` | `EventInternalController#getEndedEventsByDate` | get ended events by date |
| POST | `/internal/events/popular` | `EventInternalController#getPopularEvents` | get popular events |
| PATCH | `/internal/events/stock-adjustments` | `EventInternalController#adjustStockBulk` | adjust stock bulk |
| GET | `/internal/events/{eventId}` | `EventInternalController#getEventInfo` | get event info |
| POST | `/internal/events/{eventId}/deduct-stock` | `EventInternalController#deductStock` | deduct stock |
| PATCH | `/internal/events/{eventId}/force-cancel` | `EventInternalController#forceCancel` | force cancel |
| POST | `/internal/events/{eventId}/restore-stock` | `EventInternalController#restoreStock` | restore stock |
| GET | `/internal/events/{eventId}/validate-purchase` | `EventInternalController#validatePurchase` | validate purchase |

## member

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| POST | `/api/auth/google-signup` | `AuthController#oauthSignUpOrLogin` | oauth sign up or login |
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
| GET | `/internal/members` | `InternalMemberController#searchMembers` | 관리자 회원 목록 조회 |
| GET | `/internal/members/batch` | `InternalMemberController#getMemberInfoBatch` | get member info batch |
| GET | `/internal/members/seller-applications` | `InternalMemberController#getSellerApplications` | 판매자 신청 목록 조회 |
| PATCH | `/internal/members/seller-applications/{applicationId}` | `InternalMemberController#decideSellerApplication` | decide seller application |
| GET | `/internal/members/sellers` | `InternalMemberController#getSellerId` | 판매자 ID 목록 조회 |
| GET | `/internal/members/{userId}` | `InternalMemberController#getMemberInfo` | get member info |
| GET | `/internal/members/{userId}/role` | `InternalMemberController#getMemberRole` | get member role |
| PATCH | `/internal/members/{userId}/role` | `InternalMemberController#updateMemberRole` | update member role |
| GET | `/internal/members/{userId}/seller-info` | `InternalMemberController#getSellerInfo` | get seller info |
| GET | `/internal/members/{userId}/status` | `InternalMemberController#getMemberStatus` | get member status |
| PATCH | `/internal/members/{userId}/status` | `InternalMemberController#updateMemberStatus` | update member status |
| GET | `/internal/members/{userId}/tech-stacks` | `InternalMemberController#getUserTechStacks` | get user tech stacks |

## payment

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| POST | `/api/admin/events/{eventId}/cancel` | `AdminRefundController#cancelAdminEvent` | cancel admin event |
| POST | `/api/payments/confirm` | `PaymentController#confirm` | PG 결제 승인 |
| POST | `/api/payments/fail` | `PaymentController#fail` | PG 결제 실패 처리 |
| POST | `/api/payments/ready` | `PaymentController#readyPayment` | ready payment |
| GET | `/api/refunds` | `RefundController#getRefundList` | get refund list |
| GET | `/api/refunds/info` | `RefundController#getRefundInfo` | get refund info |
| POST | `/api/refunds/orders/{orderId}` | `RefundController#refundOrder` | refund order |
| POST | `/api/refunds/pg/{ticketId}` | `RefundController#refundPgTicket` | refund pg ticket |
| GET | `/api/refunds/{refundId}` | `RefundController#getRefundDetail` | get refund detail |
| POST | `/api/seller/events/{eventId}/cancel` | `SellerRefundController#cancelSellerEvent` | cancel seller event |
| GET | `/api/seller/refunds/events/{eventId}` | `SellerRefundController#getSellerRefundListByEventId` | get seller refund list by event id |
| GET | `/api/wallet` | `WalletController#getBalance` | 예치금 잔액 조회 |
| POST | `/api/wallet/charge` | `WalletController#charge` | 예치금 충전 시작 |
| POST | `/api/wallet/charge/confirm` | `WalletController#confirmCharge` | 예치금 충전 승인 |
| PATCH | `/api/wallet/charge/{chargeId}/fail` | `WalletController#failCharge` | 예치금 충전 실패 처리 |
| GET | `/api/wallet/transactions` | `WalletController#getTransactions` | 예치금 거래 내역 조회 |
| POST | `/api/wallet/withdraw` | `WalletController#withdraw` | 예치금 출금 요청 |
| GET | `/internal/order-items/by-ticket/{ticketId}` | `MockCommerceController#getOrderItemByTicketId` | get order item by ticket id |
| GET | `/internal/orders/{orderId}` | `MockCommerceController#getOrderInfo` | get order info |
| GET | `/internal/payments/by-order/{orderId}` | `PaymentInternalController#getPaymentByOrderId` | get payment by order id |
| POST | `/internal/wallet/settlement-deposit` | `WalletInternalController#depositFromSettlement` | deposit from settlement |
| POST | `/mock/wallet/charge` | `MockCommerceController#mockCharge` | mock charge |

## settlement

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/api/admin/settlements` | `InternalSettlementController#getSettlements` | get settlements |
| POST | `/api/admin/settlements/create-from-items` | `InternalSettlementController#createSettlementFromItems` | create settlement from items |
| POST | `/api/admin/settlements/run` | `InternalSettlementController#runSettlement` | run settlement |
| GET | `/api/admin/settlements/{settlementId}` | `InternalSettlementController#getSettlementDetail` | get settlement detail |
| POST | `/api/admin/settlements/{settlementId}/cancel` | `InternalSettlementController#cancelSettlement` | cancel settlement |
| POST | `/api/admin/settlements/{settlementId}/payment` | `InternalSettlementController#processPayment` | process payment |
| GET | `/api/seller/settlements/preview` | `SettlementController#getSettlementPreview` | 판매자 당월 예상 정산 미리보기 |
| GET | `/api/seller/settlements/{yearMonth:[0-9]{6}}` | `SettlementController#getSettlementByPeriod` | 판매자 월별 정산 조회 |
| GET | `/api/test/settlement-target/preview` | `SettlementController#previewSettlementTarget` | [테스트] 정산대상 데이터 수집 미리보기 |

