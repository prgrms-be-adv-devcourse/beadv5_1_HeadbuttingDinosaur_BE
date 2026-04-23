package com.devticket.event.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.devticket.event.application.EventService;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventTechStack;
import com.devticket.event.elasticsearch.support.ElasticsearchIntegrationTestBase;
import com.devticket.event.infrastructure.client.MemberClient;
import com.devticket.event.infrastructure.client.OpenAiEmbeddingClient;
import com.devticket.event.infrastructure.search.EventDocument;
import com.devticket.event.infrastructure.search.EventSearchRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * EventService.syncToElasticsearch() 통합 테스트.
 *
 * esClient.index() 직접 인덱싱 방식이 실제 ES에서 올바르게 동작하는지,
 * dense_vector(embedding) 포함·미포함 두 경우를 검증한다.
 */
@Tag("elasticsearch")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.kafka.listener.auto-startup=false"}
)
@ActiveProfiles("test")
class EventSyncElasticsearchTest extends ElasticsearchIntegrationTestBase {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventSearchRepository eventSearchRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ElasticsearchClient esClient;

    @MockitoBean
    private OpenAiEmbeddingClient openAiEmbeddingClient;

    @MockitoBean
    private MemberClient memberClient;

    @BeforeEach
    void setUp() {
        eventSearchRepository.deleteAll();
        elasticsearchOperations.indexOps(EventDocument.class).refresh();
    }

    // ── embedding 없는 경우 (OpenAI 실패) ────────────────────────────────

    @Test
    void embedding_생성_실패시_나머지_필드가_정확하게_저장된다() {
        // given
        when(openAiEmbeddingClient.embed(anyString())).thenReturn(null);
        UUID sellerId = UUID.randomUUID();
        Event event = createEvent(sellerId, "Spring Boot 심화 밋업", EventStatus.ON_SALE);

        // when
        eventService.syncToElasticsearch(event);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // then
        Optional<EventDocument> found = eventSearchRepository.findById(event.getEventId().toString());
        assertThat(found).isPresent();
        EventDocument doc = found.get();
        assertThat(doc.getTitle()).isEqualTo("Spring Boot 심화 밋업");
        assertThat(doc.getCategory()).isEqualTo(EventCategory.MEETUP.name());
        assertThat(doc.getStatus()).isEqualTo(EventStatus.ON_SALE.name());
        assertThat(doc.getSellerId()).isEqualTo(sellerId.toString());
    }

    @Test
    void embedding_생성_실패시_ES에_저장된_문서에_embedding_필드가_없다() throws Exception {
        // given
        when(openAiEmbeddingClient.embed(anyString())).thenReturn(null);
        Event event = createEvent(UUID.randomUUID(), "임베딩 없는 이벤트", EventStatus.ON_SALE);

        // when
        eventService.syncToElasticsearch(event);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // then
        GetResponse<Map> response = esClient.get(g -> g
            .index("event").id(event.getEventId().toString()), Map.class);
        assertThat(response.found()).isTrue();
        assertThat(response.source()).doesNotContainKey("embedding");
    }


    // ── upsert (덮어쓰기) ─────────────────────────────────────────────────

    @Test
    void 동일_이벤트_두_번_sync하면_제목이_최신_값으로_갱신된다() {
        // given
        when(openAiEmbeddingClient.embed(anyString())).thenReturn(null);
        Event event = createEvent(UUID.randomUUID(), "원본 제목", EventStatus.ON_SALE);

        eventService.syncToElasticsearch(event);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        ReflectionTestUtils.setField(event, "title", "수정된 제목");
        eventService.syncToElasticsearch(event);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // then
        EventDocument found = eventSearchRepository.findById(event.getEventId().toString()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("수정된 제목");
        assertThat(eventSearchRepository.count()).isEqualTo(1);
    }

    // ── 상태 변경 반영 ────────────────────────────────────────────────────

    @Test
    void 이벤트_취소_후_sync하면_status가_CANCELLED로_갱신된다() {
        // given
        when(openAiEmbeddingClient.embed(anyString())).thenReturn(null);
        Event event = createEvent(UUID.randomUUID(), "취소 예정 이벤트", EventStatus.ON_SALE);

        eventService.syncToElasticsearch(event);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        ReflectionTestUtils.setField(event, "status", EventStatus.CANCELLED);
        eventService.syncToElasticsearch(event);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // then
        EventDocument found = eventSearchRepository.findById(event.getEventId().toString()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(EventStatus.CANCELLED.name());
    }

    // ── techStacks 동기화 ──────────────────────────────────────────────────

    @Test
    void techStacks가_포함된_이벤트를_sync하면_techStacks_배열이_정확히_저장된다() throws Exception {
        // given
        when(openAiEmbeddingClient.embed(anyString())).thenReturn(null);
        Event event = createEvent(UUID.randomUUID(), "기술 스택 포함 밋업", EventStatus.ON_SALE);
        event.getEventTechStacks().add(EventTechStack.of(event, 1L, "Spring"));
        event.getEventTechStacks().add(EventTechStack.of(event, 2L, "Kafka"));
        event.getEventTechStacks().add(EventTechStack.of(event, 3L, "Redis"));

        // when
        eventService.syncToElasticsearch(event);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // then
        GetResponse<Map> response = esClient.get(g -> g
            .index("event").id(event.getEventId().toString()), Map.class);
        assertThat(response.found()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> techStacks = (List<String>) response.source().get("techStacks");
        assertThat(techStacks).containsExactlyInAnyOrder("Spring", "Kafka", "Redis");
    }

    @Test
    void techStacks가_비어있는_이벤트를_sync하면_빈_배열로_저장된다() throws Exception {
        // given
        when(openAiEmbeddingClient.embed(anyString())).thenReturn(null);
        Event event = createEvent(UUID.randomUUID(), "기술 스택 없음", EventStatus.ON_SALE);

        // when
        eventService.syncToElasticsearch(event);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // then
        GetResponse<Map> response = esClient.get(g -> g
            .index("event").id(event.getEventId().toString()), Map.class);
        @SuppressWarnings("unchecked")
        List<String> techStacks = (List<String>) response.source().get("techStacks");
        assertThat(techStacks).isEmpty();
    }

    // ── indexedAt 형식 ────────────────────────────────────────────────────

    @Test
    void sync하면_indexedAt이_yyyy_MM_dd_T_HH_mm_ss_형식으로_저장된다() throws Exception {
        // given
        when(openAiEmbeddingClient.embed(anyString())).thenReturn(null);
        Event event = createEvent(UUID.randomUUID(), "날짜 형식 테스트", EventStatus.ON_SALE);

        // when
        eventService.syncToElasticsearch(event);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // then
        GetResponse<Map> response = esClient.get(g -> g
            .index("event").id(event.getEventId().toString()), Map.class);
        String indexedAt = (String) response.source().get("indexedAt");
        assertThat(indexedAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    private Event createEvent(UUID sellerId, String title, EventStatus status) {
        Event event = Event.create(
            sellerId, title, "설명", "강남역",
            LocalDateTime.now().plusDays(15),
            LocalDateTime.now().plusDays(4),
            LocalDateTime.now().plusDays(10),
            50000, 100, 4, EventCategory.MEETUP
        );
        ReflectionTestUtils.setField(event, "eventId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "status", status);
        return event;
    }
}
