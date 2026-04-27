package com.devticket.event.common.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.devticket.event.application.EventService;
import com.devticket.event.domain.model.Event;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.infrastructure.search.EventDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient esClient;
    private final EventRepository eventRepository;
    private final EventService eventService;

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
            throw new RuntimeException("ElasticSearch 인덱스 초기화 실패", e);
        }
    }

    private void syncMissingEvents() {
        Set<String> dbEventIds = eventRepository.findAllEventIds().stream()
            .map(UUID::toString)
            .collect(Collectors.toSet());

        if (dbEventIds.isEmpty()) {
            log.info("[ES] DB에 이벤트 없음, 동기화 스킵");
            return;
        }

        Set<String> esEventIds = getAllEsDocumentIds();

        Set<UUID> missingIds = dbEventIds.stream()
            .filter(id -> !esEventIds.contains(id))
            .map(UUID::fromString)
            .collect(Collectors.toSet());

        if (missingIds.isEmpty()) {
            log.info("[ES] 모든 이벤트 색인 완료 ({}/{}건)", dbEventIds.size(), dbEventIds.size());
            return;
        }

        log.info("[ES] 누락 이벤트 {}건 색인 시작", missingIds.size());
        List<Event> events = eventRepository.findAllWithDetailsByEventIdIn(new ArrayList<>(missingIds));

        int count = 0;
        for (Event event : events) {
            try {
                eventService.syncToElasticsearch(event);
                count++;
            } catch (Exception e) {
                log.warn("[ES 색인 실패] eventId: {}", event.getEventId(), e);
            }
        }
        log.info("[ES] 누락 색인 완료. {}건", count);
    }

    @SuppressWarnings("unchecked")
    private Set<String> getAllEsDocumentIds() {
        try {
            var response = esClient.search(s -> s
                    .index("event")
                    .size(10000)
                    .source(src -> src.fetch(false))
                    .query(q -> q.matchAll(m -> m)),
                Map.class);

            return response.hits().hits().stream()
                .map(hit -> hit.id())
                .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("[ES] 전체 ID 조회 실패 - 전체 재색인으로 폴백", e);
            return Set.of();
        }
    }

    private void reindexAllEvents() {
        List<UUID> allEventIds = eventRepository.findAllEventIds();

        if (allEventIds.isEmpty()) {
            log.info("[ES 재색인] 재색인할 이벤트 없음");
            return;
        }

        List<Event> events = eventRepository.findAllWithDetailsByEventIdIn(allEventIds);

        int count = 0;
        for (Event event : events) {
            try {
                eventService.syncToElasticsearch(event);
                count++;
            } catch (Exception e) {
                log.warn("[ES 재색인 실패] eventId: {}", event.getEventId(), e);
            }
        }

        log.info("[ES 재색인] 완료. 총 {}건", count);
    }
}
