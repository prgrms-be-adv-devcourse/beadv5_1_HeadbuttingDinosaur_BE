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
| PATCH | `/admin/users/{userId}/role` | `AdminUsersController#updateUserRole` | 회원 제재 api |
| PATCH | `/admin/users/{userId}/status` | `AdminUsersController#penalizeUser` | 회원 목록 조회 |
| GET | `/api/admin/seller-applications` | `AdminSellerController#getSellerApplicationList` | 판매자 신청 리스트 조회 API |
| PATCH | `/api/admin/seller-applications/{applicationId}` | `AdminSellerController#decideApplication` | 판매자 신청 승인/반려 API |

## apigateway

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/health` | `GatewayHealthController#health` | health |

## event

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/` | `EventController#getEventList` | get event list |
| POST | `/` | `EventController#createEvent` | create event |
| POST | `/internal/events/bulk` | `EventInternalController#getBulkEventInfo` | get bulk event info |
| GET | `/internal/events/by-seller/{sellerId}` | `EventInternalController#getEventsBySeller` | get events by seller |
| PATCH | `/internal/events/stock-adjustments` | `EventInternalController#adjustStockBulk` | adjust stock bulk |
| GET | `/internal/events/{eventId}` | `EventInternalController#getEventInfo` | get event info |
| POST | `/internal/events/{eventId}/deduct-stock` | `EventInternalController#deductStock` | deduct stock |
| POST | `/internal/events/{eventId}/restore-stock` | `EventInternalController#restoreStock` | restore stock |
| GET | `/internal/events/{eventId}/validate-purchase` | `EventInternalController#validatePurchase` | validate purchase |
| GET | `/seller/{eventId}` | `EventController#getSellerEventDetail` | get seller event detail |
| GET | `/{eventId}` | `EventController#getEvent` | get event |
| PATCH | `/{eventId}` | `EventController#updateEvent` | update event |
| GET | `/{eventId}/statistics` | `EventController#getEventSummary` | get event summary |

## member

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/` | `TechStackController#getTechStacks` | 기술 스택 목록 조회 |
| POST | `/` | `SellerApplicationController#apply` | 판매자 전환 신청 |
| GET | `/api/members/health` | `MemberController#health` | health |
| POST | `/login` | `AuthController#login` | 일반 로그인 |
| POST | `/logout` | `AuthController#logout` | 로그아웃 |
| DELETE | `/me` | `UserController#withdraw` | 회원 탈퇴 |
| GET | `/me` | `SellerApplicationController#getMyApplication` | 신청 상태 조회 |
| GET | `/me` | `UserController#getProfile` | 프로필 조회 |
| PATCH | `/me` | `UserController#updateProfile` | 프로필 수정 |
| PATCH | `/me/password` | `UserController#changePassword` | 비밀번호 변경 |
| POST | `/profile` | `UserController#createProfile` | 프로필 생성 |
| POST | `/reissue` | `AuthController#reissue` | 토큰 재발급 |
| PATCH | `/seller-applications/{applicationId}` | `InternalMemberController#decideSellerApplication` | 판매자 신청 승인/반려 |
| POST | `/signup` | `AuthController#signup` | 회원가입 Step 1 |
| POST | `/social/google` | `AuthController#socialLogin` | 구글 소셜 로그인 |
| GET | `/{userId}` | `InternalMemberController#getMemberInfo` | 유저 기본 정보 조회 |
| GET | `/{userId}/role` | `InternalMemberController#getMemberRole` | 권한 확인 |
| GET | `/{userId}/seller-info` | `InternalMemberController#getSellerInfo` | 정산 계좌 조회 |
| GET | `/{userId}/status` | `InternalMemberController#getMemberStatus` | 회원 상태 확인 |
| PATCH | `/{userId}/status` | `InternalMemberController#getSellerApplications` | 회원 상태 변경 |

## payment

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/` | `RefundController#getRefundList` | 환불 내역 목록 조회 |
| GET | `/` | `WalletController#getBalance` | 예치금 잔액 조회 |
| GET | `/by-order/{orderId}` | `PaymentInternalController#getPaymentByOrderId` | get payment by order id |
| POST | `/charge` | `WalletController#charge` | 예치금 충전 시작 |
| POST | `/charge/confirm` | `WalletController#confirmCharge` | 예치금 충전 승인 |
| POST | `/confirm` | `PaymentController#confirm` | PG 결제 승인 (WALLET_PG 분기 포함) |
| GET | `/events/{eventId}` | `SellerRefundController#getSellerRefundListByEventId` | 판매자 이벤트별 환불 내역 조회 |
| POST | `/fail` | `PaymentController#fail` | PG 결제 실패 처리 (WALLET_PG 예치금 복구 포함) |
| GET | `/info` | `RefundController#getRefundInfo` | 환불 정보 조회 |
| POST | `/pg/{ticketId}` | `RefundController#refundPgTicket` | refund pg ticket |
| POST | `/ready` | `PaymentController#readyPayment` | 결제 준비 (PG/WALLET/WALLET_PG 분기) |
| GET | `/transactions` | `WalletController#getTransactions` | 예치금 거래 내역 조회 |
| POST | `/withdraw` | `WalletController#withdraw` | 예치금 출금 요청 |
| GET | `/{refundId}` | `RefundController#getRefundDetail` | 환불 상세 조회 |

## settlement

| HTTP | Path | Controller#Method | 설명 |
|---|---|---|---|
| GET | `/internal/orders/settlement-data` | `MockCommerceController#getSettlementData` | get settlement data |
| GET | `/seller/settlements` | `SettlementController#getSellerSettlements` | get seller settlements |
| GET | `/seller/settlements/fetch` | `SettlementController#fetchSettlementData` | fetch settlement data |
| GET | `/seller/settlements/{settlementId}` | `SettlementController#getSellerSettlement` | get seller settlement |
| GET | `/test/batch` | `SettlementController#runBatch` | run batch |

