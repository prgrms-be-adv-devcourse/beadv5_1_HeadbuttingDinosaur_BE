package com.devticket.event.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.devticket.event.application.event.ActionLogDomainEvent;
import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.common.messaging.KafkaTopics;
import com.devticket.event.common.messaging.event.ActionType;
import com.devticket.event.common.messaging.event.OrderCreatedEvent;
import com.devticket.event.common.messaging.event.StockDeductedEvent;
import com.devticket.event.common.messaging.event.StockFailedEvent;
import com.devticket.event.common.outbox.OutboxService;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.exception.EventErrorCode;
import com.devticket.event.domain.exception.StockDeductionException;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventImage;
import com.devticket.event.domain.model.EventTechStack;
import com.devticket.event.infrastructure.client.AdminClient;
import com.devticket.event.infrastructure.client.MemberClient;
import com.devticket.event.infrastructure.client.OpenAiEmbeddingClient;
import com.devticket.event.infrastructure.client.dto.TechStackItem;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.infrastructure.search.EventDocument;
import com.devticket.event.presentation.dto.EventDetailResponse;
import com.devticket.event.presentation.dto.EventListContentResponse;
import com.devticket.event.presentation.dto.EventListRequest;
import com.devticket.event.presentation.dto.EventListResponse;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import com.devticket.event.presentation.dto.SellerEventCreateResponse;
import com.devticket.event.presentation.dto.SellerEventDetailResponse;
import com.devticket.event.presentation.dto.SellerEventSummaryResponse;
import com.devticket.event.presentation.dto.SellerEventUpdateRequest;
import com.devticket.event.presentation.dto.SellerEventUpdateResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final MemberClient memberClient;
    private final AdminClient adminClient;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient esClient;
    private final OpenAiEmbeddingClient openAiEmbeddingClient;
    private final OutboxService outboxService;
    private final MessageDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SellerEventCreateResponse createEvent(UUID sellerId, SellerEventCreateRequest request) {

        // 1. 비즈니스 정책 검증
        if (request.saleStartAt().isAfter(request.saleEndAt()) || request.saleStartAt().isEqual(request.saleEndAt())) {
            throw new BusinessException(EventErrorCode.INVALID_SALE_PERIOD);
        }
        if (request.saleEndAt().isAfter(request.eventDateTime())) {
            throw new BusinessException(EventErrorCode.INVALID_EVENT_DATE);
        }

        if (request.saleStartAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(EventErrorCode.REGISTRATION_TIME_EXCEEDED);
        }

        if (request.maxQuantity() > request.totalQuantity()) {
            throw new BusinessException(EventErrorCode.MAX_QUANTITY_EXCEEDED);
        }

        // 2. 정적 팩토리 메서드 사용
        Event event = Event.create(
            sellerId,
            request.title(),
            request.description(),
            request.location(),
            request.eventDateTime(),
            request.saleStartAt(),
            request.saleEndAt(),
            request.price(),
            request.totalQuantity(),
            request.maxQuantity(),
            request.category()
        );

        // 3. 이벤트 저장
        Event savedEvent = eventRepository.save(event);

        // 4. techStackIds 저장 로직
        if (request.techStackIds() != null && !request.techStackIds().isEmpty()) {
            Map<Long, String> techStackMap = buildTechStackMap();  // 추가
            for (Long techStackId : request.techStackIds()) {
                String techStackName = techStackMap.getOrDefault(techStackId, "Unknown");  // 변경
                EventTechStack techStack = EventTechStack.of(savedEvent, techStackId, techStackName);
                savedEvent.getEventTechStacks().add(techStack);
            }
            eventRepository.save(savedEvent);
        }

        syncToElasticsearch(savedEvent);

        return SellerEventCreateResponse.from(savedEvent);
    }

    private Map<Long, String> buildTechStackMap() {
        return adminClient.getTechStacks().stream()
            .collect(Collectors.toMap(TechStackItem::id, TechStackItem::name));
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEvent(UUID eventId) {

        Event event = eventRepository.findWithDetailsByEventId(eventId)
            .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));
        String nickname = memberClient.getNickname(event.getSellerId());

        return EventDetailResponse.from(event, nickname);
    }

    public void logDetailView(UUID userId, UUID eventId) {
        if (userId == null) {
            return;
        }
        eventPublisher.publishEvent(new ActionLogDomainEvent(
            userId, eventId, ActionType.DETAIL_VIEW,
            null, null, null, null, null, Instant.now()));
    }

    public void logEventListView(UUID userId, EventListRequest request) {
        if (userId == null) {
            return;
        }
        String stackFilter = (request.techStacks() == null || request.techStacks().isEmpty())
            ? null
            : request.techStacks().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        eventPublisher.publishEvent(new ActionLogDomainEvent(
            userId, null, ActionType.VIEW,
            request.keyword(), stackFilter,
            null, null, null, Instant.now()));
    }

    @Transactional(readOnly = true)
    public EventListResponse getEventList(EventListRequest request, UUID currentUserId, Pageable pageable) {

        boolean isOwnEventRequest = request.sellerId() != null && request.sellerId().equals(currentUserId);

        if (request.status() != null && !isPublicStatus(request.status()) && !isOwnEventRequest) {
            throw new BusinessException(EventErrorCode.UNAUTHORIZED_SELLER);
        }

        List<EventStatus> allowedStatuses = null;
        if (request.status() != null) {
            allowedStatuses = List.of(request.status());
        } else if (!isOwnEventRequest) {
            allowedStatuses = List.of(EventStatus.ON_SALE, EventStatus.SOLD_OUT, EventStatus.SALE_ENDED);
        }

        // ES 검색
        NativeQuery query = buildSearchQuery(request, allowedStatuses, pageable);
        SearchHits<EventDocument> searchHits = elasticsearchOperations.search(query, EventDocument.class);

        List<UUID> pageEventIds = searchHits.stream()
            .map(hit -> UUID.fromString(hit.getContent().getId()))
            .toList();

        if (pageEventIds.isEmpty()) {
            return new EventListResponse(
                List.of(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                0L,
                0
            );
        }

        // N+1 방지: JPA로 상세 데이터 일괄 로드
        Map<UUID, Event> hydratedById = eventRepository.findAllWithDetailsByEventIdIn(pageEventIds).stream()
            .collect(Collectors.toMap(Event::getEventId, e -> e));
        Map<UUID, Event> imagesById = eventRepository.findEventImagesByEventIdIn(pageEventIds).stream()
            .collect(Collectors.toMap(Event::getEventId, e -> e));

        // ES 결과 순서 유지
        List<EventListContentResponse> content = pageEventIds.stream()
            .map(id -> {
                Event hydrated = hydratedById.get(id);
                if (hydrated == null) return null;
                Event withImages = imagesById.getOrDefault(id, hydrated);
                return EventListContentResponse.from(withImages);
            })
            .filter(Objects::nonNull)
            .toList();

        long totalHits = searchHits.getTotalHits();
        int totalPages = (int) Math.ceil((double) totalHits / pageable.getPageSize());

        return new EventListResponse(
            content,
            pageable.getPageNumber(),
            pageable.getPageSize(),
            totalHits,
            totalPages
        );
    }

    @Transactional(readOnly = true)
    public SellerEventDetailResponse getSellerEventDetail(UUID sellerId, UUID eventId) {

        Event event = eventRepository.findWithDetailsByEventId(eventId)
            .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));

        if (!event.getSellerId().equals(sellerId)) {
            throw new BusinessException(EventErrorCode.UNAUTHORIZED_SELLER);
        }

        return SellerEventDetailResponse.from(event);
    }

    @Transactional(readOnly = true)
    public SellerEventSummaryResponse getEventSummary(UUID sellerId, UUID eventId) {

        Event event = eventRepository.findByEventId(eventId)
            .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));

        if (!event.getSellerId().equals(sellerId)) {
            throw new BusinessException(EventErrorCode.UNAUTHORIZED_SELLER);
        }

        return SellerEventSummaryResponse.from(event);
    }

    private boolean isPublicStatus(EventStatus status) {
        return status == EventStatus.ON_SALE ||
            status == EventStatus.SOLD_OUT ||
            status == EventStatus.SALE_ENDED;
    }

    @Transactional
    public SellerEventUpdateResponse updateEvent(UUID sellerId, UUID eventId,
        SellerEventUpdateRequest request) {

        Event event = eventRepository.findWithDetailsByEventId(eventId)
            .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));

        if (!event.getSellerId().equals(sellerId)) {
            throw new BusinessException(EventErrorCode.UNAUTHORIZED_SELLER);
        }

        // 판매중지 분기
        if (EventStatus.CANCELLED.equals(request.status())) {
            if (!event.canBeCancelled()) {
                throw new BusinessException(EventErrorCode.CANNOT_CHANGE_STATUS);
            }
            event.cancel();
            syncToElasticsearch(event);
            return SellerEventUpdateResponse.from(event);
        }

        // 내용 수정 분기
        if (!event.canBeUpdated()) {
            throw new BusinessException(EventErrorCode.CANNOT_CHANGE_STATUS);
        }

        if (request.title() == null || request.title().isBlank()
            || request.description() == null || request.location() == null
            || request.eventDateTime() == null || request.saleStartAt() == null
            || request.saleEndAt() == null || request.price() == null
            || request.totalQuantity() == null || request.maxQuantity() == null
            || request.category() == null || request.techStackIds() == null) {
            throw new BusinessException(EventErrorCode.INVALID_REQUEST);
        }

        // 가격 정책 검증
        if (request.price() < 0 || request.price() > 9_999_999) {
            throw new BusinessException(EventErrorCode.INVALID_PRICE);
        }

        // 수량 정책 검증
        if (request.totalQuantity() < 5 || request.totalQuantity() > 9_999) {
            throw new BusinessException(EventErrorCode.INVALID_QUANTITY);
        }

        // 총 수량 축소 시 기판매 수량 역전 방지
        int soldQuantity = event.getTotalQuantity() - event.getRemainingQuantity();
        if (request.totalQuantity() < soldQuantity) {
            throw new BusinessException(EventErrorCode.TOTAL_QUANTITY_BELOW_SOLD);
        }

        if (request.saleStartAt().isAfter(request.saleEndAt())
            || request.saleStartAt().isEqual(request.saleEndAt())) {
            throw new BusinessException(EventErrorCode.INVALID_SALE_PERIOD);
        }
        if (request.saleEndAt().isAfter(request.eventDateTime())) {
            throw new BusinessException(EventErrorCode.INVALID_EVENT_DATE);
        }
        if (request.maxQuantity() > request.totalQuantity()) {
            throw new BusinessException(EventErrorCode.MAX_QUANTITY_EXCEEDED);
        }

        event.update(
            request.title(), request.description(), request.location(),
            request.eventDateTime(), request.saleStartAt(), request.saleEndAt(),
            request.price(), request.totalQuantity(), request.maxQuantity(), request.category()
        );

        // TechStack 교체
        event.getEventTechStacks().clear();
        Map<Long, String> techStackMap = buildTechStackMap();
        for (Long techStackId : request.techStackIds()) {
            event.getEventTechStacks().add(
                EventTechStack.of(event, techStackId, techStackMap.getOrDefault(techStackId, "Unknown")));
        }

        // Image 교체
        event.getEventImages().clear();
        if (request.imageUrls() != null) {
            for (int i = 0; i < request.imageUrls().size(); i++) {
                event.getEventImages().add(EventImage.of(event, request.imageUrls().get(i), i));
            }
        }

        syncToElasticsearch(event);

        return SellerEventUpdateResponse.from(event);
    }

    /**
     * order.created Consumer 처리 — 재고 차감 + stock.deducted Outbox 저장
     *
     * <p>처리 순서: isDuplicate → 재고 차감(전체) → stock.deducted Outbox 저장 → markProcessed
     * <p>재고 부족(BusinessException) 발생 시 StockDeductionException throw → @Transactional 전체 롤백
     * Consumer가 이를 캐치하여 saveStockFailed()를 별도 트랜잭션으로 호출한다.
     */
    @Transactional
    public void processOrderCreated(UUID messageId, String topic, String payload) {
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }

        OrderCreatedEvent event = deserialize(payload, OrderCreatedEvent.class);

        // Phase 1: 모든 항목 재고 차감 — 실패 시 StockDeductionException throw (전체 롤백)
        List<OrderCreatedEvent.OrderItem> sortedItems = event.orderItems().stream()
            .sorted(Comparator.comparing(OrderCreatedEvent.OrderItem::eventId))
            .toList();

        List<StockDeductedEvent> deductedEvents = new ArrayList<>();
        for (OrderCreatedEvent.OrderItem item : sortedItems) {
            Event e = eventRepository.findByEventIdWithLock(item.eventId())
                .orElseThrow(() -> new StockDeductionException(event.orderId(), item.eventId(), "이벤트를 찾을 수 없습니다"));
            try {
                e.deductStock(item.quantity());
            } catch (BusinessException ex) {
                throw new StockDeductionException(event.orderId(), item.eventId(), ex.getMessage());
            }
            deductedEvents.add(new StockDeductedEvent(event.orderId(), item.eventId(), item.quantity(), Instant.now()));
        }

        // Phase 2: 모두 성공 → Outbox 저장
        for (StockDeductedEvent deducted : deductedEvents) {
            outboxService.save(
                event.orderId().toString(),
                event.orderId().toString(),
                "STOCK_DEDUCTED",
                KafkaTopics.STOCK_DEDUCTED,
                deducted
            );
        }

        deduplicationService.markProcessed(messageId, topic);
    }

    /**
     * 재고 차감 실패 시 stock.failed Outbox 저장 — Consumer가 별도 트랜잭션으로 호출
     */
    @Transactional
    public void saveStockFailed(UUID messageId, String topic, UUID orderId, UUID eventId, String reason) {
        if (deduplicationService.isDuplicate(messageId)) {
            return;
        }

        outboxService.save(
            orderId.toString(),
            orderId.toString(),
            "STOCK_FAILED",
            KafkaTopics.STOCK_FAILED,
            new StockFailedEvent(orderId, eventId, reason, Instant.now())
        );

        deduplicationService.markProcessed(messageId, topic);
    }

    private <T> T deserialize(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Kafka 페이로드 역직렬화 실패: " + type.getSimpleName(), e);
        }
    }

    /**
     * Spring Data ES 컨버터를 완전히 우회하고 esClient.index()로 직접 인덱싱.
     * dense_vector(embedding)은 Spring Data ES 컨버터가 직렬화하지 못하므로
     * Map<String, Object>로 전체 문서를 구성해서 ES REST API에 직접 전송.
     */
    public void syncToElasticsearch(Event event) {
        try {
            List<String> techStackNames = event.getEventTechStacks().stream()
                .map(EventTechStack::getTechStackName)
                .toList();

            String embeddingText = techStackNames.isEmpty()
                ? event.getTitle() + ". Category: " + event.getCategory().name()
                : event.getTitle() + ". Category: " + event.getCategory().name()
                    + ". Tech Stacks: " + String.join(", ", techStackNames);

            Map<String, Object> doc = new HashMap<>();
            doc.put("id", event.getEventId().toString());
            doc.put("title", event.getTitle());
            doc.put("category", event.getCategory().name());
            doc.put("techStacks", techStackNames);
            doc.put("status", event.getStatus().name());
            doc.put("sellerId", event.getSellerId().toString());
            // mapping의 date_hour_minute_second 형식에 맞춤 (나노초 제외)
            doc.put("indexedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));

            float[] vector = openAiEmbeddingClient.embed(embeddingText);
            if (vector != null) {
                List<Float> vectorList = new ArrayList<>(vector.length);
                for (float v : vector) vectorList.add(v);
                doc.put("embedding", vectorList);
            } else {
                log.warn("[ES 동기화] embedding 생성 실패 - eventId: {}", event.getEventId());
            }

            String json = new ObjectMapper().writeValueAsString(doc);

            esClient.index(i -> i
                .index("event")
                .id(event.getEventId().toString())
                .withJson(new StringReader(json)));

            log.debug("[ES 동기화 완료] eventId: {}", event.getEventId());
        } catch (Exception e) {
            log.warn("[ES 동기화 실패] eventId: {}", event.getEventId(), e);
        }
    }

    private NativeQuery buildSearchQuery(
        EventListRequest request, List<EventStatus> allowedStatuses, Pageable pageable) {

        var queryBuilder = NativeQuery.builder();

        // kNN 벡터 검색 (키워드가 있을 때)
        if (request.keyword() != null && !request.keyword().isBlank()) {
            float[] queryVector = openAiEmbeddingClient.embed(request.keyword());
            if (queryVector != null) {
                List<Float> vectorList = new ArrayList<>();
                for (float v : queryVector) vectorList.add(v);
                queryBuilder.withQuery(Query.of(q -> q
                    .knn(k -> k
                        .field("embedding")
                        .queryVector(vectorList)
                        .k(30)
                        .numCandidates(50))));
            } else {
                // embedding 생성 실패 시 title 기반 multi_match 폴백
                log.warn("[검색 폴백] embedding 실패 → multi_match 사용. keyword: {}", request.keyword());
                queryBuilder.withQuery(Query.of(q -> q
                    .multiMatch(m -> m
                        .query(request.keyword())
                        .fields("title"))));
            }
        }

        // 필터 (status, category, techStacks)
        var filterQuery = new BoolQuery.Builder();

        if (allowedStatuses != null && !allowedStatuses.isEmpty()) {
            List<FieldValue> statusValues = allowedStatuses.stream()
                .map(s -> FieldValue.of(s.name()))
                .toList();
            filterQuery.filter(Query.of(q -> q
                .terms(t -> t.field("status").terms(tv -> tv.value(statusValues)))));
        }

        if (request.category() != null) {
            filterQuery.filter(Query.of(q -> q
                .term(t -> t.field("category").value(request.category().name()))));
        }

        if (request.techStacks() != null && !request.techStacks().isEmpty()) {
            Map<Long, String> techStackMap = buildTechStackMap();
            List<FieldValue> techStackNames = request.techStacks().stream()
                .map(id -> techStackMap.get(id))
                .filter(name -> name != null)
                .map(FieldValue::of)
                .toList();
            if (!techStackNames.isEmpty()) {
                filterQuery.filter(Query.of(q -> q
                    .terms(t -> t.field("techStacks").terms(tv -> tv.value(techStackNames)))));
            }
        }

        if (request.sellerId() != null) {
            filterQuery.filter(Query.of(q -> q
                .term(t -> t.field("sellerId").value(request.sellerId().toString()))));
        }

        queryBuilder
            .withFilter(Query.of(q -> q.bool(filterQuery.build())))
            .withPageable(pageable);

        return queryBuilder.build();
    }

}
