//package com.devticket.commerce.mock.controller.dto;
//
//import com.devticket.commerce.order.domain.model.OrderItem;
//import java.util.List;
//
//public record InternalOrderItemsResponse(
//    Long eventId,
//    List<InternalOrderItemsResponse.OrderItems> orders
//) {
//
//    public record OrderItems(
//        Long orderId,
//        Long eventId,
//        Integer price,
//        Integer quantity,
//        Integer subtotalAmount
//    ) {
//
//        public static OrderItems from(OrderItem orderItem) {
//            return new OrderItems(
//                orderItem.getOrderId(),
//                orderItem.getEventId(),
//                orderItem.getPrice(),
//                orderItem.getQuantity(),
//                orderItem.getSubtotalAmount()
//            );
//        }
//
//    }
//
//    public static InternalOrderItemsResponse from(Long eventId, List<OrderItem> orderItems) {
//        return new InternalOrderItemsResponse(
//            eventId,
//            orderItems.stream()
//                .map(OrderItems::from)
//                .toList()
//        );
//    }
//
//
//}
