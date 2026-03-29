package com.devticket.commerce.ticket.application.service;

import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.order.domain.exception.OrderErrorCode;
import com.devticket.commerce.order.domain.exception.OrderItemErrorCode;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.domain.repository.OrderItemRepository;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.devticket.commerce.ticket.application.usecase.TicketUsecase;
import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
import com.devticket.commerce.ticket.presentation.dto.req.TicketRequest;
import com.devticket.commerce.ticket.presentation.dto.res.TicketResponse;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService implements TicketUsecase {

    private final TicketRepository ticketRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    @Transactional
    public TicketResponse createTicket(UUID userId, TicketRequest request) {
        // Order 주문 조회
        Order order = orderRepository.findByOrderId(request.orderId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        // order 상태값 PAID로 변경
        order.completePayment();

        //order.id -> orderItem목록 조회
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getOrderId());
        if (orderItems.isEmpty()) {
            throw new BusinessException(OrderItemErrorCode.ORDER_ITEM_NOT_FOUND);
        }

        // OrderItem 수량별로 Ticket 생성
        List<Ticket> tickets = orderItems.stream()
            .flatMap(orderItem ->
                IntStream.range(0, orderItem.getQuantity())
                    .mapToObj(i -> Ticket.create(orderItem.getOrderItemId(), orderItem.getUserId(),
                        orderItem.getEventId()))
            )
            .collect(Collectors.toList());

        // 티켓 일괄 저장
        List<Ticket> savedTickets = ticketRepository.saveAll(tickets);

        //응답데이터 구성
        return TicketResponse.of(order.getOrderId(), savedTickets);
    }
}
