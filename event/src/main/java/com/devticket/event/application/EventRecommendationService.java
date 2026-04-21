package com.devticket.event.application;

import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.infrastructure.client.AiClient;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.presentation.dto.EventListContentResponse;
import com.devticket.event.presentation.dto.internal.InternalRecommendationResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
        EventStatus.CANCELLED,
        EventStatus.FORCE_CANCELLED,
        EventStatus.DRAFT
    );

    private final AiClient aiClient;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public InternalRecommendationResponse getRecommendations(UUID userId) {
        List<String> rankedIdStrings = aiClient.getRecommendedEventIds(userId);
        if (rankedIdStrings.isEmpty()) {
            return InternalRecommendationResponse.empty();
        }

        List<UUID> rankedIds = rankedIdStrings.stream()
            .map(UUID::fromString)
            .toList();

        // N+1 방지: techStacks, images 각각 배치 로드
        Map<UUID, Event> hydratedById = eventRepository.findAllWithDetailsByEventIdIn(rankedIds).stream()
            .collect(Collectors.toMap(Event::getEventId, e -> e));
        Map<UUID, Event> imagesById = eventRepository.findEventImagesByEventIdIn(rankedIds).stream()
            .collect(Collectors.toMap(Event::getEventId, e -> e));

        // AI 랭킹 순서 유지 + 필터링 (deleted_at IS NULL은 @SQLRestriction 처리)
        List<EventListContentResponse> results = rankedIds.stream()
            .map(id -> {
                Event hydrated = hydratedById.get(id);
                if (hydrated == null) return null;
                return imagesById.getOrDefault(id, hydrated);
            })
            .filter(Objects::nonNull)
            .filter(e -> !EXCLUDED_STATUSES.contains(e.getStatus()))
            .map(EventListContentResponse::from)
            .toList();

        return new InternalRecommendationResponse(results);
    }
}
