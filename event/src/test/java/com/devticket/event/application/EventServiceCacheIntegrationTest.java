package com.devticket.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devticket.event.common.config.CachingConfig;
import com.devticket.event.common.outbox.OutboxService;
import com.devticket.event.domain.model.Event;
import com.devticket.event.fixture.EventTestFixture;
import com.devticket.event.infrastructure.client.AdminClient;
import com.devticket.event.infrastructure.client.MemberClient;
import com.devticket.event.infrastructure.client.OpenAiEmbeddingClient;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.infrastructure.persistence.EventViewRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * EventService의 @Cacheable / @CacheEvict 동작을 검증한다.
 * 캐시 hit 시 메서드 본문이 실행되지 않아 repository 호출이 생략되어야 한다.
 * mutation 메서드(forceCancel 등) 호출 후에는 evict로 인해 다음 read에서 repo가 재호출되어야 한다.
 */
@SpringBootTest(
    classes = {EventService.class, CachingConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class EventServiceCacheIntegrationTest {

    @MockitoBean private EventRepository eventRepository;
    @MockitoBean private MemberClient memberClient;
    @MockitoBean private AdminClient adminClient;
    @MockitoBean private ElasticsearchOperations elasticsearchOperations;
    @MockitoBean private ElasticsearchSyncService elasticsearchSyncService;
    @MockitoBean private OpenAiEmbeddingClient openAiEmbeddingClient;
    @MockitoBean private OutboxService outboxService;
    @MockitoBean private MessageDeduplicationService deduplicationService;
    @MockitoBean private EventViewRepository eventViewRepository;

    @Autowired private EventService eventService;
    @Autowired private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    @Test
    @DisplayName("getEvent — 동일 eventId 2회 호출 시 repository는 1회만 호출된다 (cache hit)")
    void getEventCachesByEventId() {
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEvent(sellerId);
        UUID eventId = event.getEventId();
        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));
        when(memberClient.getNickname(sellerId)).thenReturn("tester");

        var first = eventService.getEvent(eventId);
        var second = eventService.getEvent(eventId);

        assertThat(first.eventId()).isEqualTo(eventId);
        assertThat(second.eventId()).isEqualTo(eventId);
        verify(eventRepository, times(1)).findWithDetailsByEventId(eventId);
        verify(memberClient, times(1)).getNickname(sellerId);
    }

    @Test
    @DisplayName("event:detail 캐시 evict 후 동일 eventId 호출 시 repository가 재호출된다")
    void evictTriggersRepoReload() {
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEvent(sellerId);
        UUID eventId = event.getEventId();
        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));
        when(memberClient.getNickname(any())).thenReturn("tester");

        eventService.getEvent(eventId);  // 캐시 적재
        cacheManager.getCache(CachingConfig.EVENT_DETAIL).evict(eventId);
        eventService.getEvent(eventId);  // 캐시 miss → repo 재호출

        verify(eventRepository, times(2)).findWithDetailsByEventId(eventId);
    }
}
