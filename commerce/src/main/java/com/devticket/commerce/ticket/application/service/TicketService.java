package com.devticket.commerce.ticket.application.service;

import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.order.domain.exception.OrderItemErrorCode;
import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.domain.repository.OrderItemRepository;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.devticket.commerce.ticket.application.usecase.TicketUsecase;
import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
import com.devticket.commerce.ticket.infrastructure.external.client.TicketToEventClient;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalBulkEventInfoRequest;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalEventInfoResponse;
import com.devticket.commerce.ticket.presentation.dto.req.TicketListRequest;
import com.devticket.commerce.ticket.presentation.dto.req.TicketRequest;
import com.devticket.commerce.ticket.presentation.dto.res.TicketDetailResponse;
import com.devticket.commerce.ticket.presentation.dto.res.TicketListResponse;
import com.devticket.commerce.ticket.presentation.dto.res.TicketResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService implements TicketUsecase {

    private final TicketRepository ticketRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TicketToEventClient ticketToEventClient;

    @Override
    public TicketListResponse getTicketList(UUID userId, TicketListRequest request) {
        //티켓조회
        Page<Ticket> ticketPage = ticketRepository.findAllByUserId(userId, request);

        //각 티켓의 eventId 가져오기
        List<Ticket> ticketList = ticketPage.getContent();
        List<Long> eventIds = ticketList.stream()
            .map(Ticket::getEventId)
            .distinct()
            .toList();

        //외부 API호출 : Event정보 조회
        InternalBulkEventInfoRequest bulkRequest = new InternalBulkEventInfoRequest(eventIds);
        List<InternalEventInfoResponse> eventInfos = ticketToEventClient.getBulkEventInfo(bulkRequest);

        Map<Long, InternalEventInfoResponse> eventMap = eventInfos.stream()
            .collect(Collectors.toMap(InternalEventInfoResponse::id, info -> info));

        // Ticket, Event정보 조합
        List<TicketDetailResponse> tickets = ticketList.stream()
            .map(ticket -> {
                InternalEventInfoResponse event = eventMap.get(ticket.getEventId());
                return new TicketDetailResponse(
                    ticket.getId(),
                    ticket.getEventId(),
                    event != null ? event.eventTitle() : "정보 없음",
                    event != null ? event.eventDateTime() : null,
                    ticket.getStatus().name()
                );
            })
            .toList();

        return TicketListResponse.of(ticketPage, tickets);
    }

    @Override
    public Optional<TicketDetailResponse> getTicketDetail(UUID ticketId) {

        return ticketRepository.findByTicketId(ticketId)
            .map(ticket -> {
                // 단건 이벤트 정보 조회
                InternalEventInfoResponse event = ticketToEventClient.getSingleEventInfo(ticket.getEventId());
                // 응답DTO 구성(ticket + event)
                return TicketDetailResponse.of(ticket, event.eventTitle(), event.eventDateTime());
            });
    }

    @Override
    @Transactional
    public TicketResponse createTicket(TicketRequest request) {
        //order.id -> orderItem목록 조회
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(request.orderId());
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
        return TicketResponse.of(request.orderId(), savedTickets);
    }
}
