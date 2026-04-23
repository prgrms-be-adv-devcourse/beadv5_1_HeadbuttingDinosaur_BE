# DTO 문서 요약

자동 생성 기준: `presentation/dto` 하위 Java `record/class`를 기준으로 정리했습니다.

## admin

### AdminDashboardResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/AdminDashboardResponse.java`
| 필드명 | 타입 |
|---|---|
| `totalUsers` | `Long` |
| `totalSellers` | `Long` |
| `activeEvents` | `Long` |
| `pendingApplications` | `Long` |

### AdminDecideSellerApplicationRequest (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/req/AdminDecideSellerApplicationRequest.java`
| 필드명 | 타입 |
|---|---|
| `decision` | `String` |

### AdminEventListResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/AdminEventListResponse.java`
| 필드명 | 타입 |
|---|---|
| `content` | `List<AdminEventResponse>` |
| `page` | `Integer` |
| `size` | `Integer` |
| `totalElements` | `Long` |
| `totalPages` | `Integer` |

### AdminEventResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/AdminEventResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `String` |
| `title` | `String` |
| `sellerNickname` | `String` |
| `status` | `String` |
| `eventDateTime` | `String` |
| `totalQuantity` | `Integer` |
| `remainingQuantity` | `Integer` |
| `createdAt` | `String` |

### AdminEventSearchRequest (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/req/AdminEventSearchRequest.java`
| 필드명 | 타입 |
|---|---|
| `keyword` | `String` |
| `status` | `String` |
| `sellerId` | `String` |
| `page` | `Integer` |
| `size` | `Integer` |

### AdminSettelmentListResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/AdminSettelmentListResponse.java`
| 필드명 | 타입 |
|---|---|
| `content` | `List<SettlementResponse>` |
| `page` | `Integer` |
| `size` | `Integer` |
| `totalElements` | `Long` |
| `totalPage` | `Integer` |

### AdminSettlementSearchRequest (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/req/AdminSettlementSearchRequest.java`
| 필드명 | 타입 |
|---|---|
| `status` | `String` |
| `sellerId` | `String` |
| `stardDate` | `String` |
| `endDate` | `String` |
| `page` | `Integer` |
| `size` | `Integer` |

### EventCancelResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/EventCancelResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `String` |
| `previousStatus` | `String` |
| `currentStatus` | `String` |
| `reason` | `String` |
| `affectedPaidOrderCount` | `Integer` |
| `cancelledAt` | `String` |

### SellerApplicationListResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/SellerApplicationListResponse.java`
| 필드명 | 타입 |
|---|---|
| `applicationId` | `String` |
| `userId` | `String` |
| `bankName` | `String` |
| `accountNumber` | `String` |
| `accountHolder` | `String` |
| `status` | `String` |
| `createdAt` | `String` |

### SettlementResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/SettlementResponse.java`
| 필드명 | 타입 |
|---|---|
| `settlementId` | `String` |
| `periodStart` | `String` |
| `periodEnd` | `String` |
| `totalSalesAmount` | `Integer` |
| `totalRefundAmount` | `Integer` |
| `totalFeeAmount` | `Integer` |
| `finalSettlementAmount` | `Integer` |
| `status` | `String` |
| `settledAt` | `String` |

### UserListResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/UserListResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `String` |
| `email` | `String` |
| `nickname` | `String` |
| `role` | `String` |
| `status` | `String` |
| `providerType` | `String` |
| `createdAt` | `String` |
| `withdrawnAt` | `String` |

### UserRoleRequest (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/req/UserRoleRequest.java`
| 필드명 | 타입 |
|---|---|
| `role` | `String` |

### UserSearchCondition (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/req/UserSearchCondition.java`
| 필드명 | 타입 |
|---|---|
| `role` | `String` |
| `status` | `String` |
| `keyword` | `String` |

### UserStatusRequest (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/req/UserStatusRequest.java`
| 필드명 | 타입 |
|---|---|
| `status` | `String` |

## event

### EventDetailResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/EventDetailResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `sellerId` | `UUID` |
| `sellerNickname` | `String` |
| `title` | `String` |
| `description` | `String` |
| `location` | `String` |
| `eventDateTime` | `LocalDateTime` |
| `saleStartAt` | `LocalDateTime` |
| `saleEndAt` | `LocalDateTime` |
| `price` | `Integer` |
| `totalQuantity` | `Integer` |
| `remainingQuantity` | `Integer` |
| `maxQuantity` | `Integer` |
| `status` | `EventStatus` |
| `category` | `EventCategory` |
| `techStacks` | `List<TechStackInfo>` |
| `imageUrls` | `List<String>` |

### EventListContentResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/EventListContentResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `title` | `String` |
| `thumbnailUrl` | `String` |
| `location` | `String` |
| `eventDateTime` | `LocalDateTime` |
| `price` | `Integer` |
| `status` | `EventStatus` |
| `techStacks` | `List<String>` |
| `saleEndAt` | `LocalDateTime` |

### EventListRequest (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/EventListRequest.java`
| 필드명 | 타입 |
|---|---|
| `keyword` | `String` |
| `category` | `EventCategory` |
| `techStacks` | `List<Long>` |
| `sellerId` | `UUID` |
| `status` | `EventStatus` |

### EventListResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/EventListResponse.java`
| 필드명 | 타입 |
|---|---|
| `content` | `List<EventListContentResponse>` |
| `page` | `int` |
| `size` | `int` |
| `totalElements` | `long` |
| `totalPages` | `int` |

### InternalBulkEventInfoRequest (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalBulkEventInfoRequest.java`
| 필드명 | 타입 |
|---|---|
| `eventIds` | `List<UUID>` |

### InternalBulkEventInfoResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalBulkEventInfoResponse.java`
| 필드명 | 타입 |
|---|---|
| `events` | `List<InternalEventInfoResponse>` |

### InternalBulkStockAdjustmentRequest (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalBulkStockAdjustmentRequest.java`
| 필드명 | 타입 |
|---|---|
| `items` | `List<StockAdjustmentItem>` |

### InternalEventInfoResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalEventInfoResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `sellerId` | `UUID` |
| `title` | `String` |
| `price` | `Integer` |
| `status` | `EventStatus` |
| `category` | `EventCategory` |
| `totalQuantity` | `Integer` |
| `maxQuantity` | `Integer` |
| `remainingQuantity` | `Integer` |
| `eventDateTime` | `LocalDateTime` |
| `saleStartAt` | `LocalDateTime` |
| `saleEndAt` | `LocalDateTime` |

### InternalPurchaseValidationResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalPurchaseValidationResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `purchasable` | `// Long id → UUID eventId boolean` |
| `reason` | `PurchaseUnavailableReason` |
| `maxQuantity` | `Integer` |
| `title` | `String` |
| `price` | `Integer` |

### InternalSellerEventsResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalSellerEventsResponse.java`
| 필드명 | 타입 |
|---|---|
| `sellerId` | `UUID` |
| `events` | `List<SellerEventSummary>` |

### InternalStockAdjustmentResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalStockAdjustmentResponse.java`
| 필드명 | 타입 |
|---|---|
| `results` | `List<StockAdjustmentResult>` |

### InternalStockDeductRequest (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalStockDeductRequest.java`
| 필드명 | 타입 |
|---|---|
| `quantity` | `Integer` |

### InternalStockOperationResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalStockOperationResponse.java`
| 필드명 | 타입 |
|---|---|
| `id` | `UUID` |
| `success` | `boolean` |
| `remainingQuantity` | `Integer` |
| `eventTitle` | `String` |

### InternalStockRestoreRequest (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalStockRestoreRequest.java`
| 필드명 | 타입 |
|---|---|
| `quantity` | `Integer` |

### SellerEventCreateRequest (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/SellerEventCreateRequest.java`
| 필드명 | 타입 |
|---|---|
| `title` | `String` |
| `description` | `String` |
| `location` | `String` |
| `eventDateTime` | `LocalDateTime` |
| `saleStartAt` | `LocalDateTime` |
| `saleEndAt` | `LocalDateTime` |
| `price` | `Integer` |
| `totalQuantity` | `Integer` |
| `maxQuantity` | `Integer` |
| `category` | `EventCategory` |
| `techStackIds` | `List<Long>` |
| `imageUrls` | `List<String>` |

### SellerEventCreateResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/SellerEventCreateResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `sellerId` | `UUID` |
| `status` | `EventStatus` |
| `createdAt` | `LocalDateTime` |

### SellerEventDetailResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/SellerEventDetailResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `title` | `String` |
| `description` | `String` |
| `location` | `String` |
| `eventDateTime` | `LocalDateTime` |
| `saleStartAt` | `LocalDateTime` |
| `saleEndAt` | `LocalDateTime` |
| `price` | `Integer` |
| `totalQuantity` | `Integer` |
| `remainingQuantity` | `Integer` |
| `maxQuantity` | `Integer` |
| `status` | `EventStatus` |
| `category` | `EventCategory` |
| `techStacks` | `List<TechStackInfo>` |
| `imageUrls` | `List<String>` |
| `createdAt` | `LocalDateTime` |
| `updatedAt` | `LocalDateTime` |

### SellerEventSummaryResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/SellerEventSummaryResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `title` | `String` |
| `status` | `EventStatus` |
| `saleEndAt` | `LocalDateTime` |
| `totalQuantity` | `Integer` |
| `remainingQuantity` | `Integer` |
| `soldQuantity` | `Integer` |
| `cancelledQuantity` | `Integer` |
| `price` | `Integer` |
| `totalSalesAmount` | `Long` |

### SellerEventUpdateRequest (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/SellerEventUpdateRequest.java`
| 필드명 | 타입 |
|---|---|
| `title` | `String` |
| `description` | `String` |
| `location` | `String` |
| `eventDateTime` | `LocalDateTime` |
| `saleStartAt` | `LocalDateTime` |
| `saleEndAt` | `LocalDateTime` |
| `price` | `Integer` |
| `totalQuantity` | `Integer` |
| `maxQuantity` | `Integer` |
| `category` | `EventCategory` |
| `techStackIds` | `List<Long>` |
| `imageUrls` | `List<String>` |
| `status` | `EventStatus` |

### SellerEventUpdateResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/SellerEventUpdateResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `status` | `EventStatus` |
| `updatedAt` | `LocalDateTime` |

## member

### ChangePasswordRequest (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/request/ChangePasswordRequest.java`
| 필드명 | 타입 |
|---|---|
| `currentPassword` | `String` |
| `newPassword` | `String` |
| `newPasswordConfirm` | `String` |

### ChangePasswordResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/ChangePasswordResponse.java`
| 필드명 | 타입 |
|---|---|
| `success` | `boolean` |

### GetProfileResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/GetProfileResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `UUID` |
| `email` | `String` |
| `nickname` | `String` |
| `position` | `String` |
| `techStacks` | `List<TechStackInfo>` |
| `profileImageUrl` | `String` |
| `bio` | `String` |
| `role` | `String` |
| `providerType` | `String` |

### InternalDecideSellerApplicationRequest (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/request/InternalDecideSellerApplicationRequest.java`
| 필드명 | 타입 |
|---|---|
| `decision` | `SellerApplicationDecision` |

### InternalDecideSellerApplicationResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/response/InternalDecideSellerApplicationResponse.java`
| 필드명 | 타입 |
|---|---|
| `sellerApplicationId` | `String` |
| `status` | `String` |

### InternalMemberInfoResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/response/InternalMemberInfoResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `UUID` |
| `email` | `String` |
| `nickname` | `String` |
| `role` | `String` |
| `status` | `String` |
| `providerType` | `String` |

### InternalMemberRoleResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/response/InternalMemberRoleResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `java.util.UUID` |
| `role` | `String` |

### InternalMemberStatusResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/response/InternalMemberStatusResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `java.util.UUID` |
| `status` | `String` |

### InternalSellerApplicationResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/response/InternalSellerApplicationResponse.java`
| 필드명 | 타입 |
|---|---|
| `sellerApplicationId` | `String` |
| `userId` | `String` |
| `bankName` | `String` |
| `accountNumber` | `String` |
| `accountHolder` | `String` |
| `status` | `String` |
| `createdAt` | `String` |

### InternalSellerInfoResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/response/InternalSellerInfoResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `UUID` |
| `bankName` | `String` |
| `accountNumber` | `String` |
| `accountHolder` | `String` |

### InternalUpdateRoleResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/response/InternalUpdateRoleResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `String` |
| `role` | `String` |

### InternalUpdateStatusResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/response/InternalUpdateStatusResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `String` |
| `status` | `String` |

### LoginRequest (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/request/LoginRequest.java`
| 필드명 | 타입 |
|---|---|
| `email` | `String` |
| `password` | `String` |

### LoginResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/LoginResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `UUID` |
| `accessToken` | `String` |
| `refreshToken` | `String` |
| `tokenType` | `String` |
| `expiresIn` | `Long` |
| `isProfileCompleted` | `boolean` |

### LogoutResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/LogoutResponse.java`
| 필드명 | 타입 |
|---|---|
| `success` | `boolean` |

### MemberInternalResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/MemberInternalResponse.java`
| 필드명 | 타입 |
|---|---|
| `id` | `Long` |
| `email` | `String` |
| `role` | `String` |
| `status` | `String` |
| `providerType` | `String` |

### MemberRoleResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/MemberRoleResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `Long` |
| `role` | `String` |

### MemberStatusResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/MemberStatusResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `Long` |
| `status` | `String` |

### SellerApplicationRequest (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/request/SellerApplicationRequest.java`
| 필드명 | 타입 |
|---|---|
| `bankName` | `String` |
| `accountNumber` | `String` |
| `accountHolder` | `String` |

### SellerApplicationResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/SellerApplicationResponse.java`
| 필드명 | 타입 |
|---|---|
| `applicationId` | `UUID` |

### SellerApplicationStatusResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/SellerApplicationStatusResponse.java`
| 필드명 | 타입 |
|---|---|
| `status` | `String` |
| `createdAt` | `LocalDateTime` |

### SellerInfoResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/SellerInfoResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `Long` |
| `bankName` | `String` |
| `accountNumber` | `String` |
| `accountHolder` | `String` |

### SignUpProfileRequest (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/request/SignUpProfileRequest.java`
| 필드명 | 타입 |
|---|---|
| `nickname` | `String` |
| `position` | `String` |
| `techStackIds` | `List<Long>` |
| `profileImageUrl` | `String` |
| `bio` | `String` |

### SignUpProfileResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/SignUpProfileResponse.java`
| 필드명 | 타입 |
|---|---|
| `profileId` | `UUID` |

### SignUpRequest (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/request/SignUpRequest.java`
| 필드명 | 타입 |
|---|---|
| `email` | `String` |
| `password` | `String` |
| `passwordConfirm` | `String` |

### SignUpResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/SignUpResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `UUID` |
| `accessToken` | `String` |
| `refreshToken` | `String` |
| `tokenType` | `String` |
| `expiresIn` | `Long` |
| `isProfileCompleted` | `boolean` |

### SocialSignUpOrLoginRequest (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/request/SocialSignUpOrLoginRequest.java`
| 필드명 | 타입 |
|---|---|
| `providerType` | `String` |
| `idToken` | `String` |

### SocialSignUpOrLoginResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/SocialSignUpOrLoginResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `UUID` |
| `accessToken` | `String` |
| `refreshToken` | `String` |
| `tokenType` | `String` |
| `expiresIn` | `Long` |
| `isNewUser` | `boolean` |
| `isProfileCompleted` | `boolean` |

### TechStackListResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/TechStackListResponse.java`
| 필드명 | 타입 |
|---|---|
| `techStacks` | `List<TechStackItem>` |

### TokenRefreshRequest (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/request/TokenRefreshRequest.java`
| 필드명 | 타입 |
|---|---|
| `refreshToken` | `String` |

### TokenRefreshResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/TokenRefreshResponse.java`
| 필드명 | 타입 |
|---|---|
| `accessToken` | `String` |
| `refreshToken` | `String` |

### UpdateProfileRequest (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/request/UpdateProfileRequest.java`
| 필드명 | 타입 |
|---|---|
| `nickname` | `String` |
| `position` | `String` |
| `profileImageUrl` | `String` |
| `techStackIds` | `List<Long>` |
| `bio` | `String` |

### UpdateProfileResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/UpdateProfileResponse.java`
| 필드명 | 타입 |
|---|---|
| `nickname` | `String` |
| `position` | `String` |
| `profileImageUrl` | `String` |
| `techStackIds` | `List<Long>` |

### WithdrawResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/response/WithdrawResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `UUID` |
| `status` | `String` |
| `withdrawnAt` | `LocalDateTime` |

## payment

### InternalPaymentInfoResponse (record)
- source: `payment/src/main/java/com/devticket/payment/payment/presentation/dto/InternalPaymentInfoResponse.java`
| 필드명 | 타입 |
|---|---|
| `id` | `Long` |
| `orderId` | `UUID` |
| `paymentKey` | `String` |
| `paymentMethod` | `String` |
| `amount` | `Integer` |
| `status` | `String` |
| `approvedAt` | `LocalDateTime` |
| `failureReason` | `String` |

### PaymentConfirmRequest (record)
- source: `payment/src/main/java/com/devticket/payment/payment/presentation/dto/PaymentConfirmRequest.java`
| 필드명 | 타입 |
|---|---|
| `paymentKey` | `String` |
| `paymentId` | `UUID` |
| `orderId` | `UUID` |
| `amount` | `Integer` |

### PaymentConfirmResponse (record)
- source: `payment/src/main/java/com/devticket/payment/payment/presentation/dto/PaymentConfirmResponse.java`
| 필드명 | 타입 |
|---|---|
| `paymentId` | `String` |
| `orderId` | `String` |
| `paymentMethod` | `PaymentMethod` |
| `status` | `PaymentStatus` |
| `amount` | `Integer` |
| `approvedAt` | `LocalDateTime` |

### PaymentFailRequest (record)
- source: `payment/src/main/java/com/devticket/payment/payment/presentation/dto/PaymentFailRequest.java`
| 필드명 | 타입 |
|---|---|
| `orderId` | `UUID` |
| `code` | `String` |
| `message` | `String` |

### PaymentFailResponse (record)
- source: `payment/src/main/java/com/devticket/payment/payment/presentation/dto/PaymentFailResponse.java`
| 필드명 | 타입 |
|---|---|
| `paymentId` | `String` |
| `orderId` | `String` |
| `status` | `PaymentStatus` |
| `failureReason` | `String` |

### PaymentReadyRequest (record)
- source: `payment/src/main/java/com/devticket/payment/payment/presentation/dto/PaymentReadyRequest.java`
| 필드명 | 타입 |
|---|---|
| `orderId` | `UUID` |
| `paymentMethod` | `PaymentMethod` |
| `walletAmount` | `Integer` |

### PaymentReadyResponse (record)
- source: `payment/src/main/java/com/devticket/payment/payment/presentation/dto/PaymentReadyResponse.java`
| 필드명 | 타입 |
|---|---|
| `orderId` | `UUID` |
| `orderNumber` | `String` |
| `paymentId` | `String` |
| `paymentMethod` | `String` |
| `orderStatus` | `String` |
| `paymentStatus` | `PaymentStatus` |
| `amount` | `Integer` |
| `walletAmount` | `Integer` |
| `pgAmount` | `Integer` |
| `approvedAt` | `String` |

### PgRefundRequest (record)
- source: `payment/src/main/java/com/devticket/payment/refund/presentation/dto/PgRefundRequest.java`
| 필드명 | 타입 |
|---|---|
| `reason` | `String` |

### PgRefundResponse (record)
- source: `payment/src/main/java/com/devticket/payment/refund/presentation/dto/PgRefundResponse.java`
| 필드명 | 타입 |
|---|---|
| `ticketId` | `String` |
| `orderId` | `UUID` |
| `paymentAmount` | `int` |
| `refundAmount` | `int` |
| `refundRate` | `int` |
| `paymentMethod` | `String` |
| `refundStatus` | `String` |
| `refundedAt` | `LocalDateTime` |

### RefundDetailResponse (record)
- source: `payment/src/main/java/com/devticket/payment/refund/presentation/dto/RefundDetailResponse.java`
| 필드명 | 타입 |
|---|---|
| `refundId` | `String` |
| `orderId` | `UUID` |
| `paymentId` | `UUID` |
| `paymentMethod` | `String` |
| `refundAmount` | `Integer` |
| `refundRate` | `Integer` |
| `status` | `String` |
| `requestedAt` | `LocalDateTime` |
| `completedAt` | `LocalDateTime` |

### RefundInfoResponse (record)
- source: `payment/src/main/java/com/devticket/payment/refund/presentation/dto/RefundInfoResponse.java`
| 필드명 | 타입 |
|---|---|
| `ticketId` | `String` |
| `eventTitle` | `String` |
| `eventDate` | `LocalDateTime` |
| `originalAmount` | `Integer` |
| `refundAmount` | `Integer` |
| `refundRate` | `Integer` |
| `dDay` | `long` |
| `refundable` | `boolean` |
| `"WALLET"` | `String paymentMethod // "PG" or` |

### RefundListItemResponse (record)
- source: `payment/src/main/java/com/devticket/payment/refund/presentation/dto/RefundListItemResponse.java`
| 필드명 | 타입 |
|---|---|
| `refundId` | `String` |
| `orderId` | `UUID` |
| `paymentId` | `UUID` |
| `refundAmount` | `Integer` |
| `refundRate` | `Integer` |
| `status` | `String` |
| `requestedAt` | `LocalDateTime` |
| `completedAt` | `LocalDateTime` |

### SellerRefundListItemResponse (record)
- source: `payment/src/main/java/com/devticket/payment/refund/presentation/dto/SellerRefundListItemResponse.java`
| 필드명 | 타입 |
|---|---|
| `refundId` | `String` |
| `orderId` | `UUID` |
| `paymentId` | `UUID` |
| `refundAmount` | `Integer` |
| `refundRate` | `Integer` |
| `status` | `String` |
| `paymentMethod` | `String` |
| `requestedAt` | `LocalDateTime` |
| `추가` | `LocalDateTime completedAt //TODO: 환불자 이름` |

### WalletBalanceResponse (record)
- source: `payment/src/main/java/com/devticket/payment/wallet/presentation/dto/WalletBalanceResponse.java`
| 필드명 | 타입 |
|---|---|
| `walletId` | `String` |
| `balance` | `Integer` |

### WalletChargeConfirmRequest (record)
- source: `payment/src/main/java/com/devticket/payment/wallet/presentation/dto/WalletChargeConfirmRequest.java`
| 필드명 | 타입 |
|---|---|
| `paymentKey` | `String` |
| `chargeId` | `String` |
| `amount` | `Integer` |

### WalletChargeConfirmResponse (record)
- source: `payment/src/main/java/com/devticket/payment/wallet/presentation/dto/WalletChargeConfirmResponse.java`
| 필드명 | 타입 |
|---|---|
| `transactionId` | `String` |
| `amount` | `Integer` |
| `balance` | `Integer` |
| `status` | `String` |
| `approvedAt` | `LocalDateTime` |

### WalletChargeRequest (record)
- source: `payment/src/main/java/com/devticket/payment/wallet/presentation/dto/WalletChargeRequest.java`
| 필드명 | 타입 |
|---|---|
| `amount` | `Integer` |

### WalletChargeResponse (record)
- source: `payment/src/main/java/com/devticket/payment/wallet/presentation/dto/WalletChargeResponse.java`
| 필드명 | 타입 |
|---|---|
| `chargeId` | `String` |
| `userId` | `String` |
| `amount` | `Integer` |
| `status` | `String` |
| `createdAt` | `String` |

### WalletTransactionListResponse (record)
- source: `payment/src/main/java/com/devticket/payment/wallet/presentation/dto/WalletTransactionListResponse.java`
| 필드명 | 타입 |
|---|---|
| `items` | `List<Item>` |
| `currentPage` | `int` |
| `totalPages` | `int` |
| `totalElements` | `long` |

### WalletWithdrawRequest (record)
- source: `payment/src/main/java/com/devticket/payment/wallet/presentation/dto/WalletWithdrawRequest.java`
| 필드명 | 타입 |
|---|---|
| `amount` | `Integer` |

### WalletWithdrawResponse (record)
- source: `payment/src/main/java/com/devticket/payment/wallet/presentation/dto/WalletWithdrawResponse.java`
| 필드명 | 타입 |
|---|---|
| `walletId` | `String` |
| `transactionId` | `String` |
| `withdrawnAmount` | `Integer` |
| `balance` | `Integer` |
| `status` | `String` |
| `requestedAt` | `LocalDateTime` |

## settlement

### EventItemResponse (record)
- source: `settlement/src/main/java/com/devticket/settlement/presentation/dto/EventItemResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `String` |
| `eventTitle` | `String` |
| `salesAmount` | `Integer` |
| `refundAmount` | `Integer` |
| `feeAmount` | `Integer` |
| `settlementAmount` | `Integer` |

### SellerSettlementDetailResponse (record)
- source: `settlement/src/main/java/com/devticket/settlement/presentation/dto/SellerSettlementDetailResponse.java`
| 필드명 | 타입 |
|---|---|
| `settlementId` | `String` |
| `periodStartAt` | `String` |
| `periodEnd` | `String` |
| `totalSalesAmount` | `Integer` |
| `totalRefundAmount` | `Integer` |
| `totalFeeAmount` | `Integer` |
| `finalSettlementAmount` | `Integer` |
| `status` | `String` |
| `settledAt` | `String` |
| `eventItems` | `List<EventItemResponse>` |

### SettlementResponse (record)
- source: `settlement/src/main/java/com/devticket/settlement/presentation/dto/SettlementResponse.java`
| 필드명 | 타입 |
|---|---|
| `settlementId` | `UUID` |
| `periodStart` | `String` |
| `periodEnd` | `String` |
| `totalSalesAmount` | `Integer` |
| `totalRefundAmount` | `Integer` |
| `totalFeeAmount` | `Integer` |
| `finalSettlementAmount` | `Integer` |
| `status` | `SettlementStatus` |
| `settledAt` | `String` |

