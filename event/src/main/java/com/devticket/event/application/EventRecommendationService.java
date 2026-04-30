package com.devticket.event.application;

import static java.util.stream.Collectors.toList;

import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventView;
import com.devticket.event.infrastructure.client.AiClient;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.infrastructure.persistence.EventViewRepository;
import com.devticket.event.presentation.dto.EventListContentResponse;
import com.devticket.event.presentation.dto.RecommendationResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventRecommendationService {

    private static final Set<EventStatus> EXCLUDED_STATUSES = Set.of(
        EventStatus.SALE_ENDED,
        EventStatus.ENDED,
        EventStatus.CANCELLED,
        EventStatus.FORCE_CANCELLED,
        EventStatus.DRAFT
    );

    private final AiClient aiClient;
    private final EventRepository eventRepository;
    private final EventViewRepository eventViewRepository;

    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendations(UUID userId) {
        List<String> rankedIdStrings = aiClient.getRecommendedEventIds(userId);
        if (rankedIdStrings.isEmpty()) {
            return RecommendationResponse.empty();
        }

        // AI 응답 ID 파싱 실패 항목 필터링 (graceful degradation)
        List<UUID> rankedIds = rankedIdStrings.stream()
            .flatMap(id -> {
                try {
                    return Stream.of(UUID.fromString(id));
                } catch (IllegalArgumentException | NullPointerException e) {
                    log.warn("[AI 추천 ID 파싱 실패] id: {}", id);
                    return Stream.empty();
                }
            })
            .toList();

        if (rankedIds.isEmpty()) {
            return RecommendationResponse.empty();
        }

        // N+1 방지: techStacks, images 각각 배치 로드
        Map<UUID, Event> hydratedById = eventRepository.findAllWithDetailsByEventIdIn(rankedIds).stream()
            .collect(Collectors.toMap(Event::getEventId, e -> e));
        Map<UUID, Event> imagesById = eventRepository.findEventImagesByEventIdIn(rankedIds).stream()
            .collect(Collectors.toMap(Event::getEventId, e -> e));

        Map<UUID, Long> viewCountById = eventViewRepository.findAllByEventIdIn(rankedIds).stream()
            .collect(Collectors.toMap(
                ev -> ev.getEvent().getEventId(),
                EventView::getViewCount
            ));

        // AI 랭킹 순서 유지 + 필터링 (deleted_at IS NULL은 @SQLRestriction 처리)
        List<EventListContentResponse> results = rankedIds.stream()
            .map(id -> {
                Event hydrated = hydratedById.get(id);
                if (hydrated == null) return null;
                return imagesById.getOrDefault(id, hydrated);
            })
            .filter(Objects::nonNull)
            .filter(e -> !EXCLUDED_STATUSES.contains(e.getStatus()))
            .map(event -> EventListContentResponse.from(event, viewCountById.getOrDefault(event.getEventId(), 0L)))
            .toList();

        return new RecommendationResponse(results);
    }
}
