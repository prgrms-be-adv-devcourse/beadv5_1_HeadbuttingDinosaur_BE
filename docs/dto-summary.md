# DTO 문서 요약

자동 생성 기준: `presentation/dto` 하위 Java `record/class`를 기준으로 정리했습니다.

## admin

### AdminActionHistorySummary (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/AdminActionHistorySummary.java`
| 필드명 | 타입 |
|---|---|
| `actionType` | `String` |
| `adminId` | `UUID` |
| `createdAt` | `LocalDateTime` |

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

### AdminSettlementListResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/AdminSettlementListResponse.java`
| 필드명 | 타입 |
|---|---|
| `content` | `List<SettlementResponse>` |
| `page` | `Integer` |
| `size` | `Integer` |
| `totalElements` | `Long` |
| `totalPages` | `Integer` |

### AdminSettlementSearchRequest (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/req/AdminSettlementSearchRequest.java`
| 필드명 | 타입 |
|---|---|
| `status` | `String` |
| `sellerId` | `String` |
| `startDate` | `String` |
| `endDate` | `String` |
| `page` | `Integer` |
| `size` | `Integer` |

### AdminUserListResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/AdminUserListResponse.java`
| 필드명 | 타입 |
|---|---|
| `content` | `List<UserListItem>` |
| `page` | `int` |
| `size` | `int` |
| `totalElements` | `long` |
| `totalPages` | `int` |

### CreateTechStackRequest (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/req/CreateTechStackRequest.java`
| 필드명 | 타입 |
|---|---|
| `name` | `String` |

### CreateTechStackResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/CreateTechStackResponse.java`
| 필드명 | 타입 |
|---|---|
| `id` | `Long` |
| `name` | `String` |

### DeleteTechStackRequest (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/req/DeleteTechStackRequest.java`
| 필드명 | 타입 |
|---|---|
| `id` | `Long` |

### DeleteTechStackResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/DeleteTechStackResponse.java`
| 필드명 | 타입 |
|---|---|
| `id` | `Long` |

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

### GetTechStackResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/GetTechStackResponse.java`
| 필드명 | 타입 |
|---|---|
| `id` | `Long` |
| `name` | `String` |

### InternalMemberDetailResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/InternalMemberDetailResponse.java`
| 필드명 | 타입 |
|---|---|
| `id` | `String` |
| `email` | `String` |
| `nickname` | `String` |
| `role` | `String` |
| `status` | `String` |
| `providerType` | `String` |
| `createdAt` | `String` |
| `withdrawnAt` | `String` |

### InternalMemberPageResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/InternalMemberPageResponse.java`
| 필드명 | 타입 |
|---|---|
| `content` | `List<InternalMemberInfoResponse>` |
| `page` | `int` |
| `size` | `int` |
| `totalElements` | `long` |
| `totalPages` | `int` |

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
| `settlementId` | `Long` |
| `periodStart` | `LocalDateTime` |
| `periodEnd` | `LocalDateTime` |
| `totalSalesAmount` | `Long` |
| `totalRefundAmount` | `Long` |
| `totalFeeAmount` | `Long` |
| `finalSettlementAmount` | `Long` |
| `status` | `String` |
| `settledAt` | `LocalDateTime` |

### UpdateTechStackRequest (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/req/UpdateTechStackRequest.java`
| 필드명 | 타입 |
|---|---|
| `id` | `Long` |
| `name` | `String` |

### UpdateTechStackResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/UpdateTechStackResponse.java`
| 필드명 | 타입 |
|---|---|
| `id` | `Long` |
| `name` | `String` |

### UserDetailResponse (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/UserDetailResponse.java`
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
| `penaltyHistory` | `List<AdminActionHistorySummary>` |

### UserListItem (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/res/UserListItem.java`
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
| `page` | `Integer` |
| `size` | `Integer` |

### UserStatusRequest (record)
- source: `admin/src/main/java/com/devticket/admin/presentation/dto/req/UserStatusRequest.java`
| 필드명 | 타입 |
|---|---|
| `status` | `String` |

## ai

### ActionLogMessage (record)
- source: `ai/src/main/java/org/example/ai/presentation/dto/req/ActionLogMessage.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `String` |
| `eventId` | `String` |
| `eventIds` | `List<String>` |
| `actionType` | `String` |
| `searchKeyword` | `String` |
| `stackFilter` | `String` |
| `dwellTimeSeconds` | `Integer` |
| `quantity` | `Integer` |
| `totalAmount` | `Long` |
| `timestamp` | `String` |

### RecommendationRequest (record)
- source: `ai/src/main/java/org/example/ai/presentation/dto/req/RecommendationRequest.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `String` |

### RecommendationResponse (record)
- source: `ai/src/main/java/org/example/ai/presentation/dto/res/RecommendationResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `String` |
| `eventIdList` | `List<String>` |

## commerce

### CartClearResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/res/CartClearResponse.java`
| 필드명 | 타입 |
|---|---|
| `message` | `String` |

### CartItemDeleteResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/res/CartItemDeleteResponse.java`
| 필드명 | 타입 |
|---|---|
| `message` | `String` |

### CartItemDetail (record)
- source: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/res/CartItemDetail.java`
| 필드명 | 타입 |
|---|---|
| `cartItemId` | `UUID` |
| `eventId` | `UUID` |
| `eventTitle` | `String` |
| `price` | `int` |
| `quantity` | `int` |

### CartItemQuantityRequest (record)
- source: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/req/CartItemQuantityRequest.java`
| 필드명 | 타입 |
|---|---|
| `quantity` | `int` |

### CartItemQuantityResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/res/CartItemQuantityResponse.java`
| 필드명 | 타입 |
|---|---|
| `cartItemId` | `String` |
| `quantity` | `int` |

### CartItemRequest (record)
- source: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/req/CartItemRequest.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `quantity` | `int` |

### CartItemResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/res/CartItemResponse.java`
| 필드명 | 타입 |
|---|---|
| `cartId` | `String` |
| `items` | `List<CartItemDetail>` |
| `totalAmount` | `long` |

### CartOrderRequest (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/req/CartOrderRequest.java`
| 필드명 | 타입 |
|---|---|
| `cartItemIds` | `List<UUID>` |

### CartResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/res/CartResponse.java`
| 필드명 | 타입 |
|---|---|
| `cartId` | `String` |
| `items` | `List<CartItemDetail>` |
| `totalAmount` | `int` |

### InternalOrderInfoResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/InternalOrderInfoResponse.java`
| 필드명 | 타입 |
|---|---|
| `id` | `UUID` |
| `userId` | `UUID` |
| `orderNumber` | `String` |
| `paymentMethod` | `String` |
| `totalAmount` | `Integer` |
| `status` | `String` |
| `orderedAt` | `String` |
| `orderItems` | `List<OrderItem>` |

### InternalOrderItemResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/InternalOrderItemResponse.java`
| 필드명 | 타입 |
|---|---|
| `orderItemId` | `UUID` |
| `orderId` | `UUID` |
| `userId` | `UUID` |
| `eventId` | `UUID` |
| `amount` | `Integer` |

### InternalOrderItemsResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/InternalOrderItemsResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `Long` |
| `orders` | `List<InternalOrderItemsResponse.OrderItems>` |

### InternalOrderTicketsResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/InternalOrderTicketsResponse.java`
| 필드명 | 타입 |
|---|---|
| `orderId` | `UUID` |
| `userId` | `UUID` |
| `paymentId` | `UUID` |
| `totalAmount` | `int` |
| `remainingAmount` | `int` |
| `tickets` | `List<TicketItem>` |

### InternalSettlementDataResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/InternalSettlementDataResponse.java`
| 필드명 | 타입 |
|---|---|
| `sellerId` | `UUID` |
| `periodStart` | `String` |
| `periodEnd` | `String` |
| `eventSettlements` | `List<EventSettlements>` |

### InternalTicketSettlementDataResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/res/InternalTicketSettlementDataResponse.java`
| 필드명 | 타입 |
|---|---|
| `items` | `List<InternalTicketSettlementItemResponse>` |

### InternalTicketSettlementItemResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/res/InternalTicketSettlementItemResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `orderItemId` | `UUID` |
| `salesAmount` | `Long` |
| `refundAmount` | `Long` |

### OrderCancelResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderCancelResponse.java`
| 필드명 | 타입 |
|---|---|
| `orderId` | `String` |
| `status` | `String` |
| `cancelledAt` | `String` |

### OrderDetailItemResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderDetailItemResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `eventTitle` | `String` |
| `quantity` | `int` |
| `price` | `int` |

### OrderDetailResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderDetailResponse.java`
| 필드명 | 타입 |
|---|---|
| `orderId` | `UUID` |
| `status` | `OrderStatus` |
| `totalAmount` | `int` |
| `orderItems` | `List<OrderDetailItemResponse>` |
| `paymentMethod` | `PaymentMethod` |
| `createdAt` | `LocalDateTime` |

### OrderItemsResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderItemsResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `eventTitle` | `String` |
| `quantity` | `int` |
| `price` | `int` |

### OrderListRequest (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/req/OrderListRequest.java`
| 필드명 | 타입 |
|---|---|
| `page` | `int` |
| `size` | `int` |
| `status` | `String` |

### OrderListResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderListResponse.java`
| 필드명 | 타입 |
|---|---|
| `orders` | `List<OrderSummary>` |
| `totalPages` | `int` |
| `totalElements` | `long` |

### OrderRequest (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/req/OrderRequest.java`
| 필드명 | 타입 |
|---|---|
| `cartItemEventIds` | `List<String>` |

### OrderResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderResponse.java`
| 필드명 | 타입 |
|---|---|
| `orderId` | `UUID` |
| `totalAmount` | `Long` |
| `orderStatus` | `OrderStatus` |
| `orderItems` | `List<OrderItemsResponse>` |
| `createdAt` | `LocalDateTime` |

### OrderStatusResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderStatusResponse.java`
| 필드명 | 타입 |
|---|---|
| `orderId` | `UUID` |
| `status` | `OrderStatus` |
| `updatedAt` | `LocalDateTime` |

### OrderSummary (record)
- source: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderSummary.java`
| 필드명 | 타입 |
|---|---|
| `orderId` | `UUID` |
| `totalAmount` | `int` |
| `status` | `OrderStatus` |
| `createdAt` | `LocalDateTime` |

### SellerEventParticipantListRequest (record)
- source: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/req/SellerEventParticipantListRequest.java`
| 필드명 | 타입 |
|---|---|
| `page` | `Integer` |
| `size` | `Integer` |
| `keyword` | `String` |

### SellerEventParticipantListResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/res/SellerEventParticipantListResponse.java`
| 필드명 | 타입 |
|---|---|
| `content` | `List<SellerEventParticipantResponse>` |
| `page` | `int` |
| `size` | `int` |
| `totalElements` | `long` |
| `totalPages` | `int` |

### SellerEventParticipantResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/res/SellerEventParticipantResponse.java`
| 필드명 | 타입 |
|---|---|
| `ticketId` | `String` |
| `orderId` | `String` |
| `userId` | `String` |
| `userName` | `String` |
| `email` | `String` |
| `quantity` | `int` |
| `purchasedAt` | `String` |
| `orderNumber` | `String` |

### TicketDetailResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/res/TicketDetailResponse.java`
| 필드명 | 타입 |
|---|---|
| `ticketId` | `UUID` |
| `eventId` | `UUID` |
| `eventTitle` | `String` |
| `eventDateTime` | `String` |
| `status` | `String` |
| `issuedAt` | `String` |

### TicketListRequest (record)
- source: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/req/TicketListRequest.java`
| 필드명 | 타입 |
|---|---|
| `page` | `int` |
| `size` | `int` |

### TicketListResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/res/TicketListResponse.java`
| 필드명 | 타입 |
|---|---|
| `totalPages` | `int` |
| `totalElements` | `Long` |
| `tickets` | `List<TicketDetailResponse>` |

### TicketRequest (record)
- source: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/req/TicketRequest.java`
| 필드명 | 타입 |
|---|---|
| `orderId` | `Long` |

### TicketResponse (record)
- source: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/res/TicketResponse.java`
| 필드명 | 타입 |
|---|---|
| `orderItemId` | `Long` |
| `totalCount` | `Integer` |
| `tickets` | `List<TicketInfo>` |

## event

### DwellRequest (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/DwellRequest.java`
| 필드명 | 타입 |
|---|---|
| `dwellTimeSeconds` | `Integer` |

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
| `totalQuantity` | `Integer` |
| `remainingQuantity` | `Integer` |

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

### ImageUploadResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/ImageUploadResponse.java`
| 필드명 | 타입 |
|---|---|
| `imageUrl` | `String` |

### InternalAdminEventResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalAdminEventResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `title` | `String` |
| `sellerNickname` | `String` |
| `status` | `String` |
| `eventDateTime` | `LocalDateTime` |
| `totalQuantity` | `Integer` |
| `remainingQuantity` | `Integer` |

### InternalBulkEventInfoRequest (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalBulkEventInfoRequest.java`
| 필드명 | 타입 |
|---|---|
| `eventIds` | `List< UUID>` |

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

### InternalEndedEventsResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalEndedEventsResponse.java`
| 필드명 | 타입 |
|---|---|
| `events` | `List<EndedEventItem>` |

### InternalEventForceCancelRequest (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalEventForceCancelRequest.java`
| 필드명 | 타입 |
|---|---|
| `reason` | `String` |

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

### InternalPagedEventResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalPagedEventResponse.java`
| 필드명 | 타입 |
|---|---|
| `content` | `List<InternalAdminEventResponse>` |
| `page` | `int` |
| `size` | `int` |
| `totalElements` | `long` |
| `totalPages` | `int` |

### InternalPopularEventRequest (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalPopularEventRequest.java`
| 필드명 | 타입 |
|---|---|
| `needed` | `int` |

### InternalPopularEventResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalPopularEventResponse.java`
| 필드명 | 타입 |
|---|---|
| `id` | `String` |

### InternalPurchaseValidationResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/internal/InternalPurchaseValidationResponse.java`
| 필드명 | 타입 |
|---|---|
| `eventId` | `UUID` |
| `purchasable` | `boolean` |
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

### RecommendationResponse (record)
- source: `event/src/main/java/com/devticket/event/presentation/dto/RecommendationResponse.java`
| 필드명 | 타입 |
|---|---|
| `events` | `List<EventListContentResponse>` |

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

### InternalAdminTechStackResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/response/InternalAdminTechStackResponse.java`
| 필드명 | 타입 |
|---|---|
| `techStacks` | `List<TechStackInfo>` |

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
| `createdAt` | `LocalDateTime` |
| `withdrawnAt` | `LocalDateTime` |

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

### InternalOAuthRequest (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/request/InternalOAuthRequest.java`
| 필드명 | 타입 |
|---|---|
| `provider` | `String` |
| `providerId` | `String` |
| `email` | `String` |
| `name` | `String` |

### InternalOAuthResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/response/InternalOAuthResponse.java`
| 필드명 | 타입 |
|---|---|
| `accessToken` | `String` |
| `refreshToken` | `String` |

### InternalPagedMemberResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/response/InternalPagedMemberResponse.java`
| 필드명 | 타입 |
|---|---|
| `content` | `List<InternalMemberInfoResponse>` |
| `page` | `int` |
| `size` | `int` |
| `totalElements` | `long` |
| `totalPages` | `int` |

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

### InternalUpdateUserRoleRequest (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/request/InternalUpdateUserRoleRequest.java`
| 필드명 | 타입 |
|---|---|
| `role` | `UserRole` |

### InternalUpdateUserStatusRequest (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/request/InternalUpdateUserStatusRequest.java`
| 필드명 | 타입 |
|---|---|
| `status` | `UserStatus` |

### InternalUserTechStackResponse (record)
- source: `member/src/main/java/com/devticket/member/presentation/dto/internal/response/InternalUserTechStackResponse.java`
| 필드명 | 타입 |
|---|---|
| `userId` | `String` |
| `techStacks` | `List<TechStackInfo>` |

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
| `accessToken` | `String` |
| `refreshToken` | `String` |

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

### AdminEventCancelRequest (record)
- source: `payment/src/main/java/com/devticket/payment/refund/presentation/dto/AdminEventCancelRequest.java`
| 필드명 | 타입 |
|---|---|
| `reason` | `String` |

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

### OrderRefundResponse (record)
- source: `payment/src/main/java/com/devticket/payment/refund/presentation/dto/OrderRefundResponse.java`
| 필드명 | 타입 |
|---|---|
| `refundId` | `UUID` |
| `orderRefundId` | `UUID` |
| `orderId` | `UUID` |
| `refundAmount` | `int` |
| `refundRate` | `int` |
| `paymentMethod` | `String` |
| `refundStatus` | `String` |

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
| `paymentMethod` | `String` |

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

### SellerEventCancelRequest (record)
- source: `payment/src/main/java/com/devticket/payment/refund/presentation/dto/SellerEventCancelRequest.java`
| 필드명 | 타입 |
|---|---|
| `reason` | `String` |

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
| `completedAt` | `LocalDateTime` |

### SettlementDepositRequest (record)
- source: `payment/src/main/java/com/devticket/payment/wallet/presentation/dto/SettlementDepositRequest.java`
| 필드명 | 타입 |
|---|---|
| `settlementId` | `UUID` |
| `userId` | `UUID` |
| `amount` | `int` |

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
| `salesAmount` | `Long` |
| `refundAmount` | `Long` |
| `feeAmount` | `Long` |
| `settlementAmount` | `Long` |

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

### SettlementPeriodResponse (record)
- source: `settlement/src/main/java/com/devticket/settlement/presentation/dto/SettlementPeriodResponse.java`
| 필드명 | 타입 |
|---|---|
| `finalSettlementAmount` | `Integer` |
| `totalFeeAmount` | `Integer` |
| `totalSalesAmount` | `Integer` |
| `carriedInAmount` | `Integer` |
| `settlementItems` | `List<EventItemResponse>` |

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

### SettlementTargetPreviewResponse (record)
- source: `settlement/src/main/java/com/devticket/settlement/presentation/dto/SettlementTargetPreviewResponse.java`
| 필드명 | 타입 |
|---|---|
| `targetDate` | `String` |
| `totalEventCount` | `int` |
| `savedCount` | `int` |
| `skippedCount` | `int` |
| `feePolicyName` | `String` |
| `feeValue` | `String` |
| `items` | `List<EventSettlementPreview>` |

