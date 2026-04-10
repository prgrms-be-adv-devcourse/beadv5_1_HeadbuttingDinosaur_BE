package com.devticket.event.application;

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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
            for (Long techStackId : request.techStackIds()) {
                String techStackName = getTechStackName(techStackId);
                EventTechStack techStack = EventTechStack.of(savedEvent, techStackId, techStackName);
                savedEvent.getEventTechStacks().add(techStack);
            }
            eventRepository.save(savedEvent);  // EventTechStack 반영을 위해 다시 저장
        }

        syncToElasticsearch(savedEvent);

        return SellerEventCreateResponse.from(savedEvent);
    }

    /**
     * TechStack ID를 실제 name으로 매핑 (더미 데이터)
     * 향후 Member 서비스 API 호출로 교체 예정
     */
    private String getTechStackName(Long techStackId) {
        Map<Long, String> TECH_STACK_NAMES = new HashMap<>();
        TECH_STACK_NAMES.put(1L, "Spring");
        TECH_STACK_NAMES.put(2L, "React");
        TECH_STACK_NAMES.put(3L, "Vue");
        TECH_STACK_NAMES.put(4L, "Django");
        TECH_STACK_NAMES.put(5L, "FastAPI");
        TECH_STACK_NAMES.put(6L, "Node.js");
        TECH_STACK_NAMES.put(7L, "Python");
        TECH_STACK_NAMES.put(8L, "Kotlin");

        return TECH_STACK_NAMES.getOrDefault(techStackId, "Unknown");
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
        List<Event> hydratedEvents = eventRepository.findAllWithDetailsByEventIdIn(pageEventIds);
        List<Event> imageEvents = eventRepository.findEventImagesByEventIdIn(pageEventIds);

        Map<UUID, Event> hydratedById = hydratedEvents.stream()
            .collect(Collectors.toMap(Event::getEventId, e -> e));
        Map<UUID, Event> imagesById = imageEvents.stream()
            .collect(Collectors.toMap(Event::getEventId, e -> e));

        // ES 결과 순서 유지
        List<EventListContentResponse> content = pageEventIds.stream()
            .map(id -> {
                Event hydrated = hydratedById.get(id);
                if (hydrated == null) {
                    return null;
                }
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
        for (Long techStackId : request.techStackIds()) {
            event.getEventTechStacks().add(
                EventTechStack.of(event, techStackId, getTechStackName(techStackId)));
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

    // ES 동기화 실패가 핵심 비즈니스 흐름에 영향을 주지 않도록 예외 격리
    private void syncToElasticsearch(Event event) {
        try {
            eventSearchRepository.save(EventDocument.from(event));
        } catch (Exception e) {
            log.warn("[ES 동기화 실패] eventId: {}", event.getEventId(), e);
        }
    }

    private NativeQuery buildSearchQuery(
        EventListRequest request, List<EventStatus> allowedStatuses, Pageable pageable) {

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // 키워드: title, description 대상 multi_match
        if (request.keyword() != null && !request.keyword().isBlank()) {
            boolQuery.must(Query.of(q -> q
                .multiMatch(m -> m
                    .fields("title", "description")
                    .query(request.keyword()))));
        }

        // 카테고리 필터
        if (request.category() != null) {
            boolQuery.filter(Query.of(q -> q
                .term(t -> t.field("category").value(request.category().name()))));
        }

        // 상태 필터
        if (allowedStatuses != null && !allowedStatuses.isEmpty()) {
            List<FieldValue> statusValues = allowedStatuses.stream()
                .map(s -> FieldValue.of(s.name()))
                .toList();
            boolQuery.filter(Query.of(q -> q
                .terms(t -> t.field("status").terms(tv -> tv.value(statusValues)))));
        }

        // 판매자 필터
        if (request.sellerId() != null) {
            boolQuery.filter(Query.of(q -> q
                .term(t -> t.field("sellerId").value(request.sellerId().toString()))));
        }

        // 기술스택 필터 (Nested)
        if (request.techStacks() != null && !request.techStacks().isEmpty()) {
            for (Long techStackId : request.techStacks()) {
                boolQuery.filter(Query.of(q -> q
                    .nested(n -> n
                        .path("techStacks")
                        .query(nq -> nq
                            .term(t -> t.field("techStacks.techStackId")
                                .value(techStackId))))));
            }
        }

        // 정렬: 지정 없으면 최신순 기본 적용
        Sort sort = pageable.getSort().isSorted()
            ? pageable.getSort()
            : Sort.by(Sort.Direction.DESC, "createdAt");

        return NativeQuery.builder()
            .withQuery(Query.of(q -> q.bool(boolQuery.build())))
            .withPageable(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort))
            .build();
    }

}
