# Commerce DTO 문서 (presentation/dto 기준)

## Request DTO

### CartItemQuantityRequest
- 파일: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/req/CartItemQuantityRequest.java`
- 타입: `record`
- 필드:
  - `quantity`: `int`

### CartItemRequest
- 파일: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/req/CartItemRequest.java`
- 타입: `record`
- 필드:
  - `eventId`: `UUID`
  - `quantity`: `int`

### CartOrderRequest
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/req/CartOrderRequest.java`
- 타입: `record`
- 필드:
  - `cartItemIds`: `List<UUID>`

### OrderListRequest
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/req/OrderListRequest.java`
- 타입: `record`
- 필드:
  - `page`: `int`
  - `size`: `int`
  - `status`: `String`

### OrderRequest
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/req/OrderRequest.java`
- 타입: `record`
- 필드:
  - `cartItemEventIds`: `List<String>`

### SellerEventParticipantListRequest
- 파일: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/req/SellerEventParticipantListRequest.java`
- 타입: `record`
- 필드:
  - `page`: `Integer`
  - `size`: `Integer`
  - `keyword`: `String`

### TicketListRequest
- 파일: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/req/TicketListRequest.java`
- 타입: `record`
- 필드:
  - `page`: `int`
  - `size`: `int`

### TicketRequest
- 파일: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/req/TicketRequest.java`
- 타입: `record`
- 필드:
  - `orderId`: `Long`

## Response DTO

### CartClearResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/res/CartClearResponse.java`
- 타입: `record`
- 필드:
  - `message`: `String`

### CartItemDeleteResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/res/CartItemDeleteResponse.java`
- 타입: `record`
- 필드:
  - `message`: `String`

### CartItemDetail
- 파일: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/res/CartItemDetail.java`
- 타입: `record`
- 필드:
  - `cartItemId`: `UUID`
  - `eventId`: `UUID`
  - `eventTitle`: `String`
  - `price`: `int`
  - `quantity`: `int`

### CartItemQuantityResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/res/CartItemQuantityResponse.java`
- 타입: `record`
- 필드:
  - `cartItemId`: `String`
  - `quantity`: `int`

### CartItemResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/res/CartItemResponse.java`
- 타입: `record`
- 필드:
  - `cartId`: `String`
  - `items`: `List<CartItemDetail>`
  - `totalAmount`: `long`

### CartResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/cart/presentation/dto/res/CartResponse.java`
- 타입: `record`
- 필드:
  - `cartId`: `String`
  - `items`: `List<CartItemDetail>`
  - `totalAmount`: `int`

### InternalOrderInfoResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/InternalOrderInfoResponse.java`
- 타입: `record`
- 필드:
  - `id`: `UUID`
  - `userId`: `UUID`
  - `orderNumber`: `String`
  - `paymentMethod`: `String`
  - `totalAmount`: `Integer`
  - `status`: `String`
  - `orderedAt`: `String`

### InternalOrderItemResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/InternalOrderItemResponse.java`
- 타입: `record`
- 필드:
  - `id`: `Long`
  - `orderItemId`: `UUID`
  - `orderId`: `Long`
  - `userId`: `UUID`
  - `eventId`: `UUID`
  - `price`: `int`
  - `quantity`: `int`
  - `subtotalAmount`: `int`
  - `createdAt`: `LocalDateTime`
  - `updatedAt`: `LocalDateTime`

### InternalOrderItemsResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/InternalOrderItemsResponse.java`
- 타입: `record`
- 필드:
  - `eventId`: `Long`
  - `orders`: `List<InternalOrderItemsResponse.OrderItems>`

### InternalSettlementDataResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/InternalSettlementDataResponse.java`
- 타입: `record`
- 필드:
  - `sellerId`: `UUID`
  - `periodStart`: `String`
  - `periodEnd`: `String`
  - `eventSettlements`: `List<EventSettlements>`

### OrderCancelResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderCancelResponse.java`
- 타입: `record`
- 필드:
  - `orderId`: `String`
  - `status`: `String`
  - `cancelledAt`: `String`

### OrderDetailItemResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderDetailItemResponse.java`
- 타입: `record`
- 필드:
  - `eventId`: `UUID`
  - `eventTitle`: `String`
  - `quantity`: `int`
  - `price`: `int`

### OrderDetailResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderDetailResponse.java`
- 타입: `record`
- 필드:
  - `orderId`: `UUID`
  - `status`: `OrderStatus`
  - `totalAmount`: `int`
  - `orderItems`: `List<OrderDetailItemResponse>`
  - `paymentMethod`: `PaymentMethod`
  - `createdAt`: `LocalDateTime`

### OrderItemsResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderItemsResponse.java`
- 타입: `record`
- 필드:
  - `eventId`: `UUID`
  - `eventTitle`: `String`
  - `quantity`: `int`
  - `price`: `int`

### OrderListResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderListResponse.java`
- 타입: `record`
- 필드:
  - `orders`: `List<OrderSummary>`
  - `totalPages`: `int`
  - `totalElements`: `long`

### OrderResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderResponse.java`
- 타입: `record`
- 필드:
  - `orderId`: `UUID`
  - `totalAmount`: `Long`
  - `orderStatus`: `OrderStatus`
  - `orderItems`: `List<OrderItemsResponse>`
  - `createdAt`: `LocalDateTime`

### OrderStatusResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderStatusResponse.java`
- 타입: `record`
- 필드:
  - `orderId`: `UUID`
  - `status`: `OrderStatus`
  - `updatedAt`: `LocalDateTime`

### OrderSummary
- 파일: `commerce/src/main/java/com/devticket/commerce/order/presentation/dto/res/OrderSummary.java`
- 타입: `record`
- 필드:
  - `orderId`: `UUID`
  - `totalAmount`: `int`
  - `status`: `OrderStatus`
  - `createdAt`: `LocalDateTime`

### SellerEventParticipantListResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/res/SellerEventParticipantListResponse.java`
- 타입: `record`
- 필드:
  - `sellerEventParticipantListResponse`: `List<SellerEventParticipantResponse>`
  - `page`: `int`
  - `size`: `int`
  - `totalElements`: `long`
  - `totalPages`: `int`

### SellerEventParticipantResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/res/SellerEventParticipantResponse.java`
- 타입: `record`
- 필드:
  - `ticketId`: `String`
  - `orderId`: `String`
  - `userId`: `String`
  - `email`: `String`
  - `purchasedAt`: `String`
  - `orderNumber`: `String`

### TicketDetailResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/res/TicketDetailResponse.java`
- 타입: `record`
- 필드:
  - `ticketId`: `UUID`
  - `eventId`: `UUID`
  - `eventTitle`: `String`
  - `eventDateTime`: `String`
  - `status`: `String`
  - `issuedAt`: `String`

### TicketListResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/res/TicketListResponse.java`
- 타입: `record`
- 필드:
  - `totalPages`: `int`
  - `totalElements`: `Long`
  - `tickets`: `List<TicketDetailResponse>`

### TicketResponse
- 파일: `commerce/src/main/java/com/devticket/commerce/ticket/presentation/dto/res/TicketResponse.java`
- 타입: `record`
- 필드:
  - `orderItemId`: `Long`
  - `totalCount`: `Integer`
  - `tickets`: `List<TicketInfo>`
