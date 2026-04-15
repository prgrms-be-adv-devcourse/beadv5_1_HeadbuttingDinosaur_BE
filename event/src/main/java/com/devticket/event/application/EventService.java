package com.devticket.event.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.exception.EventErrorCode;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventImage;
import com.devticket.event.domain.model.EventTechStack;
import com.devticket.event.infrastructure.client.MemberClient;
import com.devticket.event.infrastructure.client.OpenAiEmbeddingClient;
import com.devticket.event.infrastructure.client.dto.TechStackItem;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.infrastructure.search.EventDocument;
import com.devticket.event.infrastructure.search.EventSearchRepository;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final EventSearchRepository eventSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient esClient;
    private final OpenAiEmbeddingClient openAiEmbeddingClient;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "price", "eventDateTime");

    @Transactional
    public SellerEventCreateResponse createEvent(UUID sellerId, SellerEventCreateRequest request) {

        // 1. 비즈니스 정책 검증
        if (request.saleStartAt().isAfter(request.saleEndAt()) || request.saleStartAt().isEqual(request.saleEndAt())) {
            throw new BusinessException(EventErrorCode.INVALID_SALE_PERIOD);
        }
        if (request.saleEndAt().isAfter(request.eventDateTime())) {
            throw new BusinessException(EventErrorCode.INVALID_EVENT_DATE);
        }

        LocalDateTime deadline = request.saleStartAt().minusDays(3);
        if (LocalDateTime.now().isAfter(deadline)) {
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
        return memberClient.getTechStacks().stream()
            .collect(Collectors.toMap(TechStackItem::techStackId, TechStackItem::name));
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEvent(UUID eventId) {

        Event event = eventRepository.findWithDetailsByEventId(eventId)
            .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));
        String nickname = memberClient.getNickname(event.getSellerId());

        return EventDetailResponse.from(event, nickname);
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

        // ES 결과를 String ID 기준으로 순서 유지
        List<String> rawIds = searchHits.stream()
            .map(hit -> hit.getContent().getId())
            .toList();

        if (rawIds.isEmpty()) {
            return new EventListResponse(
                List.of(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                0L,
                0
            );
        }

        // UUID로 변환 가능한 ID만 DB 조회에 사용
        List<UUID> uuidIds = rawIds.stream()
            .map(id -> {
                try { return UUID.fromString(id); }
                catch (IllegalArgumentException e) { return null; }
            })
            .filter(id -> id != null)
            .toList();

        Map<UUID, Event> hydratedById = Collections.emptyMap();
        Map<UUID, Event> imagesById = Collections.emptyMap();
        if (!uuidIds.isEmpty()) {
            hydratedById = eventRepository.findAllWithDetailsByEventIdIn(uuidIds).stream()
                .collect(Collectors.toMap(Event::getEventId, e -> e));
            imagesById = eventRepository.findEventImagesByEventIdIn(uuidIds).stream()
                .collect(Collectors.toMap(Event::getEventId, e -> e));
        }

        // ES 문서 Map (DB fallback용)
        Map<String, EventDocument> esDocById = searchHits.stream()
            .collect(Collectors.toMap(hit -> hit.getContent().getId(), hit -> hit.getContent()));

        final Map<UUID, Event> finalHydratedById = hydratedById;
        final Map<UUID, Event> finalImagesById = imagesById;

        // ES 결과 순서 유지, DB에 있으면 DB 우선 / 없으면 ES fallback
        List<EventListContentResponse> content = rawIds.stream()
            .map(rawId -> {
                try {
                    UUID uuid = UUID.fromString(rawId);
                    Event hydrated = finalHydratedById.get(uuid);
                    if (hydrated != null) {
                        Event withImages = finalImagesById.getOrDefault(uuid, hydrated);
                        return EventListContentResponse.from(withImages);
                    }
                } catch (IllegalArgumentException ignored) {}
                EventDocument doc = esDocById.get(rawId);
                return doc != null ? EventListContentResponse.from(doc) : null;
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
        Map<Long, String> techStackMap = buildTechStackMap();  // 추가
        for (Long techStackId : request.techStackIds()) {
            event.getEventTechStacks().add(
                EventTechStack.of(event, techStackId, techStackMap.getOrDefault(techStackId, "Unknown")));  // 변경
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
     * Spring Data ES 컨버터를 완전히 우회하고 esClient.index()로 직접 인덱싱.
     * dense_vector(embedding)은 Spring Data ES 컨버터가 직렬화하지 못하므로
     * Map<String, Object>로 전체 문서를 구성해서 ES REST API에 직접 전송.
     */
    private void syncToElasticsearch(Event event) {
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
            // mapping의 date_hour_minute_second 형식에 맞춤 (나노초 제외)
            doc.put("indexedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));

            float[] vector = openAiEmbeddingClient.embed(embeddingText);
            if (vector != null) {
                log.debug("[1] Vector 생성 완료: 차원={}, 첫 5개 값={}", vector.length,
                    java.util.Arrays.toString(java.util.Arrays.copyOf(vector, Math.min(5, vector.length))));

                List<Float> vectorList = new ArrayList<>(vector.length);
                for (float v : vector) vectorList.add(v);

                log.debug("[2] vectorList 생성 완료: 크기={}, 첫 5개 값={}", vectorList.size(),
                    vectorList.subList(0, Math.min(5, vectorList.size())));

                doc.put("embedding", vectorList);
                log.debug("[3] doc에 embedding 추가 완료. doc 키들: {}", doc.keySet());
            } else {
                log.warn("[Embedding 생략] eventId: {}", event.getEventId());
            }

            // Jackson으로 직렬화 후 withJson()으로 전송
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(doc);

            log.debug("[4] JSON 직렬화 완료: 길이={}", json.length());
            log.debug("[4-1] JSON (처음 500자): {}", json.substring(0, Math.min(500, json.length())));

            // embedding이 JSON에 포함되어 있는지 확인
            if (json.contains("\"embedding\"")) {
                log.debug("[5] ✓ JSON에 embedding 필드 포함됨");
                int embeddingStart = json.indexOf("\"embedding\"");
                int embeddingEnd = json.indexOf("]", embeddingStart) + 1;
                log.debug("[5-1] embedding 값: {}", json.substring(embeddingStart, Math.min(embeddingEnd + 100, json.length())));
            } else {
                log.warn("[5] ✗ JSON에 embedding 필드가 없음!");
            }

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
            }
        }

        // 필터 (category, status)
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

        queryBuilder
            .withFilter(Query.of(q -> q.bool(filterQuery.build())))
            .withPageable(pageable);

        return queryBuilder.build();
    }



}
