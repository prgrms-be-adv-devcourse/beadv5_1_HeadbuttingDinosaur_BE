package com.devticket.event.common.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.devticket.event.application.EventService;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.infrastructure.search.EventDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer {

    private static final List<EventStatus> ACTIVE_STATUSES =
        List.of(EventStatus.DRAFT, EventStatus.ON_SALE, EventStatus.SOLD_OUT);

    private static final List<EventStatus> TERMINATED_STATUSES =
        List.of(EventStatus.SALE_ENDED, EventStatus.CANCELLED, EventStatus.FORCE_CANCELLED);

    private static final int BATCH_SIZE = 50;

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient esClient;
    private final EventRepository eventRepository;
    private final EventService eventService;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndex() {
        try {
            var indexOps = elasticsearchOperations.indexOps(EventDocument.class);

            if (!indexOps.exists()) {
                indexOps.createWithMapping();
                log.info("[ES Index] 'event' 인덱스 생성 완료");
                reindexAllEvents();
            } else {
                syncMissingEvents();
            }

        } catch (Exception e) {
            log.error("[ES Index 초기화 실패]", e);
        }
    }

    private void syncMissingEvents() {
        Set<String> activeDbIds = eventRepository.findAllEventIdsByStatusIn(ACTIVE_STATUSES).stream()
            .map(UUID::toString)
            .collect(Collectors.toSet());

        Set<String> terminatedDbIds = eventRepository.findAllEventIdsByStatusIn(TERMINATED_STATUSES).stream()
            .map(UUID::toString)
            .collect(Collectors.toSet());

        Set<String> esIds = getAllEsDocumentIds();

        // 누락된 활성 이벤트 색인
        Set<UUID> missingIds = activeDbIds.stream()
            .filter(id -> !esIds.contains(id))
            .map(UUID::fromString)
            .collect(Collectors.toSet());

        if (!missingIds.isEmpty()) {
            log.info("[ES] 누락 이벤트 {}건 색인 시작", missingIds.size());
            int count = indexInBatches(new ArrayList<>(missingIds), "[ES 색인 실패]");
            log.info("[ES] 누락 색인 완료. {}건", count);
        } else {
            log.info("[ES] 활성 이벤트 모두 색인됨 ({}건)", activeDbIds.size());
        }

        // ES에 남아있는 종료 이벤트 삭제
        Set<String> staleEsIds = terminatedDbIds.stream()
            .filter(esIds::contains)
            .collect(Collectors.toSet());

        if (!staleEsIds.isEmpty()) {
            log.info("[ES] 종료 이벤트 {}건 인덱스에서 삭제 시작", staleEsIds.size());
            int deleted = 0;
            for (String id : staleEsIds) {
                try {
                    esClient.delete(d -> d.index("event").id(id));
                    deleted++;
                } catch (Exception e) {
                    log.warn("[ES 삭제 실패] eventId: {}", id, e);
                }
            }
            log.info("[ES] 종료 이벤트 삭제 완료. {}건", deleted);
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> getAllEsDocumentIds() {
        try {
            var response = esClient.search(s -> s
                    .index("event")
                    .size(1000)
                    .source(src -> src.fetch(false))
                    .query(q -> q.matchAll(m -> m)),
                java.util.Map.class);

            return response.hits().hits().stream()
                .map(hit -> hit.id())
                .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("[ES] 전체 ID 조회 실패 - 동기화 스킵", e);
            return Set.of();
        }
    }

    private void reindexAllEvents() {
        List<UUID> allEventIds = eventRepository.findAllEventIdsByStatusIn(ACTIVE_STATUSES);

        if (allEventIds.isEmpty()) {
            log.info("[ES 재색인] 재색인할 이벤트 없음");
            return;
        }

        int count = indexInBatches(allEventIds, "[ES 재색인 실패]");
        log.info("[ES 재색인] 완료. 총 {}건", count);
    }

    private int indexInBatches(List<UUID> eventIds, String errorLogPrefix) {
        int count = 0;
        for (int i = 0; i < eventIds.size(); i += BATCH_SIZE) {
            List<UUID> batch = eventIds.subList(i, Math.min(i + BATCH_SIZE, eventIds.size()));
            List<Event> events = eventRepository.findAllWithDetailsByEventIdIn(batch);
            for (Event event : events) {
                try {
                    eventService.syncToElasticsearch(event);
                    count++;
                } catch (Exception e) {
                    log.warn("{} eventId: {}", errorLogPrefix, event.getEventId(), e);
                }
            }
        }
        return count;
    }
}
