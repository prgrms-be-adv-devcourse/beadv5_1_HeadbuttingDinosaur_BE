package com.devticket.commerce.ticket.application.service;

import com.devticket.commerce.common.exception.BusinessException;
import com.devticket.commerce.order.domain.exception.OrderErrorCode;
import com.devticket.commerce.order.domain.exception.OrderItemErrorCode;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.domain.repository.OrderItemRepository;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import com.devticket.commerce.ticket.application.usecase.TicketUsecase;
import com.devticket.commerce.ticket.domain.enums.TicketStatus;
import com.devticket.commerce.ticket.domain.exception.TicketErrorCode;
import com.devticket.commerce.ticket.domain.model.Ticket;
import com.devticket.commerce.ticket.domain.repository.TicketRepository;
import com.devticket.commerce.ticket.infrastructure.external.client.TicketToEventClient;
import com.devticket.commerce.ticket.infrastructure.external.client.TicketToMemberClient;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalBulkEventInfoRequest;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalEventInfoResponse;
import com.devticket.commerce.ticket.infrastructure.external.client.dto.InternalMemberInfoResponse;
import com.devticket.commerce.ticket.presentation.dto.req.SellerEventParticipantListRequest;
import com.devticket.commerce.ticket.presentation.dto.req.TicketListRequest;
import com.devticket.commerce.ticket.presentation.dto.req.TicketRequest;
import com.devticket.commerce.ticket.presentation.dto.res.InternalTicketSettlementDataResponse;
import com.devticket.commerce.ticket.presentation.dto.res.InternalTicketSettlementItemResponse;
import com.devticket.commerce.ticket.presentation.dto.res.SellerEventParticipantListResponse;
import com.devticket.commerce.ticket.presentation.dto.res.SellerEventParticipantResponse;
import com.devticket.commerce.ticket.presentation.dto.res.TicketDetailResponse;
import com.devticket.commerce.ticket.presentation.dto.res.TicketListResponse;
import com.devticket.commerce.ticket.presentation.dto.res.TicketResponse;
import java.util.AbstractMap;
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
    private final TicketToMemberClient ticketToMemberClient;

    @Override
    public TicketListResponse getTicketList(UUID userId, TicketListRequest request) {
        log.debug("[getTicketList] 시작 - userId={}, request={}", userId, request);
        //티켓조회
        Page<Ticket> ticketPage = ticketRepository.findAllByUserId(userId, request);
        log.debug("[getTicketList] 티켓 조회 완료 - totalElements={}, page={}", ticketPage.getTotalElements(),
            ticketPage.getNumber());

        //각 티켓의 eventId 가져오기
        List<Ticket> ticketList = ticketPage.getContent();
        List<UUID> eventIds = ticketList.stream()
            .map(Ticket::getEventId)
            .distinct()
            .toList();
        log.debug("[getTicketList] eventIds 추출 완료 - count={}, eventIds={}", eventIds.size(), eventIds);

        // eventIds가 비어있으면 외부 호출 스킵
        if (eventIds.isEmpty()) {
            return TicketListResponse.of(ticketPage, List.of());
        }

        //외부 API호출 : Event정보 조회
        InternalBulkEventInfoRequest bulkRequest = new InternalBulkEventInfoRequest(eventIds);
        log.debug("[getTicketList] Event 외부 API 호출 시작 - bulkRequest={}", bulkRequest);
        List<InternalEventInfoResponse> eventInfos = ticketToEventClient.getBulkEventInfo(bulkRequest);
        log.debug("[getTicketList] Event 외부 API 호출 완료 - eventInfos count={}",
            eventInfos != null ? eventInfos.size() : "null");

        Map<UUID, InternalEventInfoResponse> eventMap = (eventInfos != null ? eventInfos
            : List.<InternalEventInfoResponse>of()).stream()
            .collect(Collectors.toMap(InternalEventInfoResponse::eventId, info -> info));

        // Ticket, Event정보 조합
        List<TicketDetailResponse> tickets = ticketList.stream()
            .map(ticket -> {
                InternalEventInfoResponse event = eventMap.get(ticket.getEventId());
                if (event == null) {
                    log.warn("[getTicketList] eventMap에서 이벤트 정보 없음 - ticketId={}, eventId={}", ticket.getId(),
                        ticket.getEventId());
                }
                return new TicketDetailResponse(
                    ticket.getTicketId(),
                    ticket.getEventId(),
                    event != null ? event.title() : "정보 없음",
                    event != null ? String.valueOf(event.eventDateTime()) : null,
                    ticket.getStatus().name(),
                    ticket.getIssuedAt().toString()
                );
            })
            .toList();

        log.debug("[getTicketList] 완료 - 응답 티켓 count={}", tickets.size());
        return TicketListResponse.of(ticketPage, tickets);
    }

    @Override
    public Optional<TicketDetailResponse> getTicketDetail(UUID ticketId) {

        return ticketRepository.findByTicketId(ticketId)
            .map(ticket -> {
                // 단건 이벤트 정보 조회
                InternalEventInfoResponse event = ticketToEventClient.getSingleEventInfo(ticket.getEventId());
                // 응답DTO 구성(ticket + event)
                return TicketDetailResponse.of(ticket, event.title(), String.valueOf(event.eventDateTime()));
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

    /**
     * 호출 Settlement -> Commerce
     */
    @Override
    public InternalTicketSettlementDataResponse getSettlementData(List<UUID> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return new InternalTicketSettlementDataResponse(List.of());
        }

        // eventIds로 티켓 전체 조회
        List<Ticket> tickets = ticketRepository.findAllByEventIdIn(eventIds);
        if (tickets.isEmpty()) {
            return new InternalTicketSettlementDataResponse(List.of());
        }

        // eventIds로 OrderItem 조회 → orderItemId별 단가 맵
        Map<UUID, Integer> priceByOrderItemId = orderItemRepository.findSettlementItems(eventIds).stream()
            .collect(Collectors.toMap(
                OrderItem::getOrderItemId,
                OrderItem::getPrice,
                (existing, duplicate) -> existing
            ));

        // (eventId, orderItemId) 기준으로 티켓 그룹핑 후 정산 집계
        List<InternalTicketSettlementItemResponse> items = tickets.stream()
            .collect(Collectors.groupingBy(
                t -> new AbstractMap.SimpleEntry<>(t.getEventId(), t.getOrderItemId())
            ))
            .entrySet().stream()
            .map(entry -> {
                UUID eventId = entry.getKey().getKey();
                UUID orderItemId = entry.getKey().getValue();
                List<Ticket> group = entry.getValue();

                Long price = Long.valueOf(priceByOrderItemId.getOrDefault(orderItemId, 0));
                Long salesAmount = price * group.size();
                Long refundAmount = price * (int) group.stream()
                    .filter(t -> t.getStatus() == TicketStatus.REFUNDED)
                    .count();

                return new InternalTicketSettlementItemResponse(eventId, orderItemId, salesAmount, refundAmount);
            })
            .toList();

        return new InternalTicketSettlementDataResponse(items);
    }

    public SellerEventParticipantListResponse getParticipantList(UUID userId, UUID eventId,
        SellerEventParticipantListRequest request) {

        InternalEventInfoResponse eventInfo = ticketToEventClient.getSingleEventInfo(eventId);
        if (!eventInfo.sellerId().equals(userId)) {
            throw new BusinessException(TicketErrorCode.UNAUTHORIZED_EVENT_ACCESS);
        }

        Page<Ticket> ticketPage = ticketRepository.findAllByEventIdAndStatus(eventId, TicketStatus.ISSUED, request);

        // 유저별 티켓 그룹핑
        Map<UUID, List<Ticket>> ticketsByUser = ticketPage.getContent().stream()
            .collect(Collectors.groupingBy(Ticket::getUserId));

        List<SellerEventParticipantResponse> participants = ticketsByUser.entrySet().stream()
            .map(entry -> {
                UUID ticketUserId = entry.getKey();
                List<Ticket> userTickets = entry.getValue();
                Ticket firstTicket = userTickets.get(0);

                OrderItem orderItem = orderItemRepository.findByOrderItemId(firstTicket.getOrderItemId())
                    .orElseThrow(() -> new BusinessException(OrderItemErrorCode.ORDER_ITEM_NOT_FOUND));

                Order order = orderRepository.findById(orderItem.getOrderId())
                    .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

                InternalMemberInfoResponse memberInfo = ticketToMemberClient.getMemberInfo(ticketUserId);

                return SellerEventParticipantResponse.of(
                    firstTicket.getTicketId().toString(),
                    order.getOrderId().toString(),
                    ticketUserId.toString(),
                    memberInfo.nickname(),
                    memberInfo.email(),
                    userTickets.size(),
                    firstTicket.getIssuedAt().toString(),
                    order.getOrderNumber()
                );
            })
            .toList();

        return SellerEventParticipantListResponse.of(ticketPage, participants);
    }
}
