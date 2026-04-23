# Commerce API 문서 (구현 기준)

## 1) External API

| 영역 | 메서드 | 경로 | Controller 메서드 | 요청 DTO | 응답 DTO | 설명 |
|---|---|---|---|---|---|---|
| Cart | POST | `/api/cart/items` | `addToCart` | `CartItemRequest` | `CartItemResponse` | 장바구니 아이템 추가 |
| Cart | GET | `/api/cart` | `getCart` | - | `CartResponse` | 내 장바구니 조회 |
| Cart | PATCH | `/api/cart/items/{cartItemId}` | `updateCartItemQuantity` | `CartItemQuantityRequest` | `CartItemQuantityResponse` | 장바구니 수량 증감 |
| Cart | DELETE | `/api/cart/items/{cartItemId}` | `deleteCartItem` | - | `CartItemDeleteResponse` | 장바구니 단건 삭제 |
| Cart | DELETE | `/api/cart` | `deleteCartItemAll` | - | `CartClearResponse` | 장바구니 전체 삭제 |
| Order | POST | `/api/orders` | `createOrderByCart` | `CartOrderRequest` | `OrderResponse` | 장바구니 기반 주문 생성 |
| Order | GET | `/api/orders` | `getOrderList` | `OrderListRequest`(query) | `OrderListResponse` | 주문 목록 조회 |
| Order | GET | `/api/orders/{orderId}/status` | `getOrderStatus` | - | `OrderStatusResponse` | 주문 상태 폴링 (`CREATED → PAYMENT_PENDING` 확인) |
| Order | GET | `/api/orders/{orderId}` | `getOrderDetail` | - | `OrderDetailResponse` | 주문 상세 조회 |
| Order | PATCH | `/api/orders/{orderId}/cancel` | `cancelOrder` | - | `OrderCancelResponse` | 결제 전 주문 취소 |
| Ticket | GET | `/api/tickets` | `getTicketList` | `TicketListRequest`(query) | `TicketListResponse` | 내 티켓 목록 조회 |
| Ticket | GET | `/api/tickets/{ticketId}` | `getTicketDetail` | - | `TicketDetailResponse` | 티켓 상세 조회 |
| Ticket | POST | `/api/tickets` | `createTickets` | `TicketRequest` | `TicketResponse` | 티켓 발급(내부성격 API) |
| Seller Ticket | GET | `/seller/events/{eventId}/participants` | `getParticipantList` | `SellerEventParticipantListRequest`(query) | `SellerEventParticipantListResponse` | 이벤트 참여자 목록 조회 |

## 2) Internal API

| 영역 | 메서드 | 경로 | Controller 메서드 | 응답 DTO | 설명 |
|---|---|---|---|---|---|
| Order Internal | GET | `/internal/orders/{orderId}` | `getOrderInfo` | `InternalOrderInfoResponse` | 결제 전 주문 정보 조회 |
| Order Internal | GET | `/internal/orders/{id}/items` | `getOrderListForSettlement` | `InternalOrderItemsResponse` | 정산용 주문 항목 조회 |
| Order Internal | GET | `/internal/orders/settlement-data` | `getSettlementData` | `InternalSettlementDataResponse` | 판매자 기간 정산 데이터 조회 |
| Order Internal | POST | `/internal/orders/{orderId}/payment-completed` | `completeOrder` | `Void` | 결제 성공 처리 + 티켓 발급 |
| Order Internal | PATCH | `/internal/orders/{orderId}/payment-failed` | `failOrder` | `Void` | 결제 실패 처리 |
| Order Internal | GET | `/internal/order-items/by-ticket/{ticketId}` | `getOrderItemByTicketId` | `InternalOrderItemResponse` | 티켓 기준 주문항목 조회 |
| Order Internal | PATCH | `/internal/tickets/{ticketId}/refund-completed` | `completeRefund` | `Void` | 환불 완료 처리 |

> 참고: `/internal/orders/by-event/{eventId}`는 주석 처리되어 현재 미구현 상태입니다.
