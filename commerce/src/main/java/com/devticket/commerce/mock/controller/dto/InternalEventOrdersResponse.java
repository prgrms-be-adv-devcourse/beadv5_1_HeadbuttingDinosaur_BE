//package com.devticket.commerce.mock.controller.dto;
//
//import com.devticket.commerce.order.domain.model.Order;
//import java.util.List;
//import java.util.UUID;
//
//public record InternalEventOrdersResponse(
//    Long eventId,
//    List<InternalEventOrdersResponse.Orders> orders
//) {
//
//    public record Orders(
//        Long id,
//        UUID userId,
//        String orderNumber,
//        String paymentMethod,
//        Integer totalAmount,
//        String status,
//        String orderedAt
//    ) {
//
//        public static Orders from(Order order) {
//            return new Orders(
//                order.getId(),
//                order.getUserId(),
//                order.getOrderNumber(),
//                order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null,
//                order.getTotalAmount(),
//                order.getStatus().name(),
//                order.getOrderedAt().toString()
//            );
//        }
//    }
//
//    public static InternalEventOrdersResponse from(Long eventId, List<Order> orderList) {
//        return new InternalEventOrdersResponse(
//            eventId,
//            orderList.stream()
//                .map(Orders::from)
//                .toList()
//        );
//    }
//}
