package com.devticket.event.common.config;

import com.devticket.event.application.EventService;
import com.devticket.event.domain.model.Event;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.infrastructure.search.EventDocument;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer {

    private final ElasticsearchOperations elasticsearchOperations;
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
                long count = elasticsearchOperations.count(
                    org.springframework.data.elasticsearch.client.elc.NativeQuery.builder().build(),
                    EventDocument.class
                );
                if (count == 0) {
                    log.info("[ES Index] 인덱스는 있지만 비어있음 → 재색인 시작");
                    reindexAllEvents();
                } else {
                    log.info("[ES Index] 기존 'event' 인덱스 유지 ({}건)", count);
                }
            }

        } catch (Exception e) {
            log.error("[ES Index 초기화 실패]", e);
            throw new RuntimeException("ElasticSearch 인덱스 초기화 실패", e);
        }
    }

    /**
     * DB의 모든 이벤트를 ES에 재색인.
     * embedding 포함 저장 (syncToElasticsearch 재사용)
     */
    private void reindexAllEvents() {
        List<UUID> allEventIds = eventRepository.findAll().stream()
            .map(Event::getEventId)
            .toList();

        if (allEventIds.isEmpty()) {
            log.info("[ES 재색인] 재색인할 이벤트 없음");
            return;
        }

        // techStacks Fetch Join (LazyInitializationException 방지)
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
