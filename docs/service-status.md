# Commerce 서비스 구현 현황 (메서드 단위)

## CartService
- `findByUserId`: 사용자 장바구니 존재 여부를 조회합니다.
- `save`: 이벤트 구매 가능 여부 검증 후 장바구니 아이템을 생성/증가시킵니다.
- `getCart`: 장바구니와 아이템 목록을 조회해 응답 DTO로 반환합니다.
- `clearCart`: 장바구니 아이템을 전체 삭제합니다.
- `updateTicket`: 장바구니 아이템 수량 변경 및 재검증을 수행합니다.
- `deleteTicket`: 특정 장바구니 아이템을 삭제합니다.

## OrderService
- `createOrderByCart`: 장바구니 아이템으로 주문 생성, 재고 선차감 및 실패 시 원복을 처리합니다.
- `getOrderList`: 사용자 주문 목록을 상태/페이지 조건으로 조회합니다.
- `getOrderDetail`: 주문 상세와 이벤트 제목 정보를 결합해 반환합니다.
- `cancelOrder`: 결제 전 주문을 취소하고 재고를 복구합니다.
- `getOrderInfo`: 내부용 주문 단건 정보를 반환합니다.
- `getOrderListForSettlement`: 정산용 주문 항목 목록을 반환합니다.
- `completeOrder`: 결제 완료 상태 전이 후 티켓 발급을 수행합니다.
- `failOrder`: 결제 실패 상태로 주문 상태를 전이합니다.
- `getSettelmentData`: 판매자/기간 기준 정산 집계 데이터를 계산합니다.
- `getOrderItemByTicketId`: 티켓 기준 주문 항목 상세를 조회합니다.
- `completeRefund`: 티켓 환불 완료 처리와 주문 금액/재고 보정을 수행합니다.

## TicketService
- `getTicketList`: 사용자 티켓 목록을 이벤트 정보와 결합해 페이징 반환합니다.
- `getTicketDetail`: 티켓 단건과 이벤트 정보를 결합해 상세를 반환합니다.
- `createTicket`: 주문 항목 수량만큼 티켓을 생성/저장합니다.
- `getParticipantList`: 판매자 이벤트 참가자 목록을 주문/회원 정보와 조합해 반환합니다.
