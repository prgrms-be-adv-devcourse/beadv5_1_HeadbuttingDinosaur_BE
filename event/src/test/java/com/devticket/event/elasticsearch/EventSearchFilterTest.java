package com.devticket.event.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.devticket.event.application.EventService;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.elasticsearch.support.ElasticsearchIntegrationTestBase;
import com.devticket.event.infrastructure.client.MemberClient;
import com.devticket.event.infrastructure.client.OpenAiEmbeddingClient;
import com.devticket.event.infrastructure.search.EventDocument;
import com.devticket.event.infrastructure.search.EventSearchRepository;
import com.devticket.event.presentation.dto.EventListRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * buildSearchQuery() 기반 실제 ES 검색 필터 통합 테스트.
 *
 * ES 문서는 EventSearchRepository.save()로 직접 시드하여
 * OpenAI·JPA 의존성 없이 상태·카테고리·techStacks·sellerId·키워드·kNN·페이지네이션 필터 동작을 검증한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.kafka.listener.auto-startup=false"}
)
@ActiveProfiles("test")
class EventSearchFilterTest extends ElasticsearchIntegrationTestBase {

    @Autowired
    private EventSearchRepository eventSearchRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private EventService eventService;

    @MockitoBean
    private OpenAiEmbeddingClient openAiEmbeddingClient;

    @MockitoBean
    private MemberClient memberClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        eventSearchRepository.deleteAll();
        elasticsearchOperations.indexOps(EventDocument.class).refresh();
    }

    // ── 상태 필터 ─────────────────────────────────────────────────────────

    @Test
    void ON_SALE_필터_적용시_ON_SALE_문서만_반환된다() {
        // given
        seed("이벤트A", "ON_SALE", "MEETUP", List.of(), UUID.randomUUID());
        seed("이벤트B", "CANCELLED", "MEETUP", List.of(), UUID.randomUUID());
        refresh();

        // when
        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(List.of(EventStatus.ON_SALE), null, null, null, PageRequest.of(0, 20)),
            EventDocument.class);

        // then
        assertThat(hits.getTotalHits()).isEqualTo(1);
        assertThat(hits.getSearchHit(0).getContent().getStatus()).isEqualTo("ON_SALE");
    }

    @Test
    void 공개_상태_3종_필터_적용시_3건_모두_반환된다() {
        // given
        seed("이벤트1", "ON_SALE", "MEETUP", List.of(), UUID.randomUUID());
        seed("이벤트2", "SOLD_OUT", "CONFERENCE", List.of(), UUID.randomUUID());
        seed("이벤트3", "SALE_ENDED", "HACKATHON", List.of(), UUID.randomUUID());
        seed("이벤트4", "DRAFT", "MEETUP", List.of(), UUID.randomUUID());
        refresh();

        // when
        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(
                List.of(EventStatus.ON_SALE, EventStatus.SOLD_OUT, EventStatus.SALE_ENDED),
                null, null, null, PageRequest.of(0, 20)),
            EventDocument.class);

        // then
        assertThat(hits.getTotalHits()).isEqualTo(3);
    }

    @Test
    void DRAFT_상태는_공개_상태_필터에_포함되지_않는다() {
        // given
        seed("DRAFT 이벤트", "DRAFT", "MEETUP", List.of(), UUID.randomUUID());
        refresh();

        // when
        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(
                List.of(EventStatus.ON_SALE, EventStatus.SOLD_OUT, EventStatus.SALE_ENDED),
                null, null, null, PageRequest.of(0, 20)),
            EventDocument.class);

        // then
        assertThat(hits.getTotalHits()).isZero();
    }

    // ── 카테고리 필터 ─────────────────────────────────────────────────────

    @Test
    void 카테고리_필터_적용시_해당_카테고리만_반환된다() {
        // given
        seed("MEETUP 이벤트1", "ON_SALE", "MEETUP", List.of(), UUID.randomUUID());
        seed("MEETUP 이벤트2", "ON_SALE", "MEETUP", List.of(), UUID.randomUUID());
        seed("CONFERENCE 이벤트", "ON_SALE", "CONFERENCE", List.of(), UUID.randomUUID());
        refresh();

        // when
        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(null, EventCategory.MEETUP, null, null, PageRequest.of(0, 20)),
            EventDocument.class);

        // then
        assertThat(hits.getTotalHits()).isEqualTo(2);
        hits.forEach(h -> assertThat(h.getContent().getCategory()).isEqualTo("MEETUP"));
    }

    // ── techStacks 필터 ───────────────────────────────────────────────────

    @Test
    void techStacks_필터_적용시_해당_기술_스택을_가진_문서만_반환된다() {
        // given
        seed("Spring 이벤트1", "ON_SALE", "MEETUP", List.of("Spring", "JPA"), UUID.randomUUID());
        seed("Spring 이벤트2", "ON_SALE", "CONFERENCE", List.of("Spring", "Kafka"), UUID.randomUUID());
        seed("React 이벤트", "ON_SALE", "MEETUP", List.of("React", "TypeScript"), UUID.randomUUID());
        refresh();

        // when
        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(null, null, List.of("Spring"), null, PageRequest.of(0, 20)),
            EventDocument.class);

        // then
        assertThat(hits.getTotalHits()).isEqualTo(2);
        hits.forEach(h -> assertThat(h.getContent().getTechStacks()).contains("Spring"));
    }

    @Test
    void techStacks_필터는_OR_조건으로_동작한다() {
        // given
        seed("Spring만", "ON_SALE", "MEETUP", List.of("Spring"), UUID.randomUUID());
        seed("Kafka만", "ON_SALE", "MEETUP", List.of("Kafka"), UUID.randomUUID());
        seed("React만", "ON_SALE", "MEETUP", List.of("React"), UUID.randomUUID());
        refresh();

        // when — Spring OR Kafka
        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(null, null, List.of("Spring", "Kafka"), null, PageRequest.of(0, 20)),
            EventDocument.class);

        // then
        assertThat(hits.getTotalHits()).isEqualTo(2);
    }

    // ── sellerId 필터 ─────────────────────────────────────────────────────

    @Test
    void sellerId_필터_적용시_해당_판매자의_이벤트만_반환된다() {
        // given
        UUID sellerA = UUID.randomUUID();
        UUID sellerB = UUID.randomUUID();
        seed("판매자A 이벤트1", "ON_SALE", "MEETUP", List.of(), sellerA);
        seed("판매자A 이벤트2", "DRAFT", "CONFERENCE", List.of(), sellerA);
        seed("판매자B 이벤트", "ON_SALE", "MEETUP", List.of(), sellerB);
        refresh();

        // when
        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(null, null, null, sellerA, PageRequest.of(0, 20)),
            EventDocument.class);

        // then
        assertThat(hits.getTotalHits()).isEqualTo(2);
        hits.forEach(h -> assertThat(h.getContent().getSellerId()).isEqualTo(sellerA.toString()));
    }

    // ── 키워드 검색 (multi_match fallback) ───────────────────────────────

    @Test
    void 키워드_검색시_OpenAI_실패하면_title_기반_multiMatch로_폴백된다() {
        // given
        when(openAiEmbeddingClient.embed(anyString())).thenReturn(null);
        seed("스프링 부트 심화 밋업", "ON_SALE", "MEETUP", List.of(), UUID.randomUUID());
        seed("스프링 클라우드 컨퍼런스", "ON_SALE", "CONFERENCE", List.of(), UUID.randomUUID());
        seed("리액트 프런트엔드 워크샵", "ON_SALE", "MEETUP", List.of(), UUID.randomUUID());
        refresh();

        EventListRequest request = new EventListRequest("스프링", null, null, null, null);

        // when — EventService.buildSearchQuery()를 reflection으로 호출
        NativeQuery query = (NativeQuery) ReflectionTestUtils.invokeMethod(
            eventService, "buildSearchQuery",
            request, (List<EventStatus>) null, PageRequest.of(0, 20));
        SearchHits<EventDocument> hits = elasticsearchOperations.search(query, EventDocument.class);

        // then
        assertThat(hits.getTotalHits()).isGreaterThanOrEqualTo(2);
        hits.forEach(h -> assertThat(h.getContent().getTitle()).contains("스프링"));
    }

    // ── kNN 벡터 검색 ─────────────────────────────────────────────────────

    @Test
    void kNN_검색시_embedding이_저장된_문서를_반환한다() throws Exception {
        // given — embedding 포함 문서를 raw JSON으로 직접 인덱싱
        String embeddedId = UUID.randomUUID().toString();
        float[] vector = new float[1536];
        for (int i = 0; i < 1536; i++) vector[i] = 0.1f;

        Map<String, Object> doc = new HashMap<>();
        doc.put("id", embeddedId);
        doc.put("title", "벡터 검색 이벤트");
        doc.put("category", "MEETUP");
        doc.put("status", "ON_SALE");
        doc.put("sellerId", UUID.randomUUID().toString());
        doc.put("techStacks", List.of());
        doc.put("indexedAt", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        List<Float> vectorList = new ArrayList<>();
        for (float v : vector) vectorList.add(v);
        doc.put("embedding", vectorList);

        String json = objectMapper.writeValueAsString(doc);
        esClient.index(i -> i.index("event").id(embeddedId).withJson(new StringReader(json)));
        refresh();

        when(openAiEmbeddingClient.embed(anyString())).thenReturn(vector);
        EventListRequest request = new EventListRequest("스프링", null, null, null, null);

        // when
        NativeQuery query = (NativeQuery) ReflectionTestUtils.invokeMethod(
            eventService, "buildSearchQuery",
            request, (List<EventStatus>) null, PageRequest.of(0, 20));
        SearchHits<EventDocument> hits = elasticsearchOperations.search(query, EventDocument.class);

        // then
        List<String> ids = hits.stream().map(h -> h.getContent().getId()).toList();
        assertThat(ids).contains(embeddedId);
    }

    // ── 복합 필터 ─────────────────────────────────────────────────────────

    @Test
    void 상태_카테고리_sellerId_복합_필터가_정확히_동작한다() {
        // given
        UUID targetSeller = UUID.randomUUID();
        UUID otherSeller = UUID.randomUUID();
        seed("조건 충족 이벤트", "ON_SALE", "MEETUP", List.of(), targetSeller);
        seed("다른 판매자", "ON_SALE", "MEETUP", List.of(), otherSeller);
        seed("다른 상태", "DRAFT", "MEETUP", List.of(), targetSeller);
        seed("다른 카테고리", "ON_SALE", "CONFERENCE", List.of(), targetSeller);
        refresh();

        // when
        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(List.of(EventStatus.ON_SALE), EventCategory.MEETUP, null, targetSeller, PageRequest.of(0, 20)),
            EventDocument.class);

        // then
        assertThat(hits.getTotalHits()).isEqualTo(1);
        assertThat(hits.getSearchHit(0).getContent().getTitle()).isEqualTo("조건 충족 이벤트");
    }

    // ── 페이지네이션 ──────────────────────────────────────────────────────

    @Test
    void 첫_페이지는_pageSize만큼만_반환하고_totalHits는_전체_건수다() {
        for (int i = 1; i <= 5; i++) seed("이벤트" + i, "ON_SALE", "MEETUP", List.of(), UUID.randomUUID());
        refresh();

        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(null, null, null, null, PageRequest.of(0, 2)), EventDocument.class);

        assertThat(hits.getTotalHits()).isEqualTo(5);
        assertThat(hits.getSearchHits()).hasSize(2);
    }

    @Test
    void 두번째_페이지도_pageSize만큼_반환된다() {
        for (int i = 1; i <= 5; i++) seed("이벤트" + i, "ON_SALE", "MEETUP", List.of(), UUID.randomUUID());
        refresh();

        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(null, null, null, null, PageRequest.of(1, 2)), EventDocument.class);

        assertThat(hits.getTotalHits()).isEqualTo(5);
        assertThat(hits.getSearchHits()).hasSize(2);
    }

    @Test
    void 마지막_페이지는_나머지_건수만_반환된다() {
        for (int i = 1; i <= 5; i++) seed("이벤트" + i, "ON_SALE", "MEETUP", List.of(), UUID.randomUUID());
        refresh();

        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(null, null, null, null, PageRequest.of(2, 2)), EventDocument.class);

        assertThat(hits.getTotalHits()).isEqualTo(5);
        assertThat(hits.getSearchHits()).hasSize(1);
    }

    // ── 빈 결과 ───────────────────────────────────────────────────────────

    @Test
    void 문서가_없을때_검색_결과는_0건이다() {
        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(null, null, null, null, PageRequest.of(0, 20)), EventDocument.class);

        assertThat(hits.getTotalHits()).isZero();
        assertThat(hits.getSearchHits()).isEmpty();
    }

    @Test
    void 조건에_일치하는_문서가_없으면_빈_결과를_반환한다() {
        // given — MEETUP만 존재
        seed("MEETUP 이벤트", "ON_SALE", "MEETUP", List.of(), UUID.randomUUID());
        refresh();

        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(null, EventCategory.CONFERENCE, null, null, PageRequest.of(0, 20)),
            EventDocument.class);

        assertThat(hits.getTotalHits()).isZero();
    }

    // ── 필터 없을 때 전체 조회 ────────────────────────────────────────────

    @Test
    void 필터가_모두_null이면_전체_문서를_반환한다() {
        seed("이벤트1", "ON_SALE", "MEETUP", List.of(), UUID.randomUUID());
        seed("이벤트2", "DRAFT", "CONFERENCE", List.of(), UUID.randomUUID());
        seed("이벤트3", "CANCELLED", "HACKATHON", List.of(), UUID.randomUUID());
        refresh();

        SearchHits<EventDocument> hits = elasticsearchOperations.search(
            buildFilterQuery(null, null, null, null, PageRequest.of(0, 20)), EventDocument.class);

        assertThat(hits.getTotalHits()).isEqualTo(3);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    private void seed(String title, String status, String category,
        List<String> techStackNames, UUID sellerId) {
        eventSearchRepository.save(EventDocument.builder()
            .id(UUID.randomUUID().toString())
            .title(title).category(category).status(status)
            .sellerId(sellerId.toString()).techStacks(techStackNames)
            .indexedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS))
            .build());
    }

    private void refresh() {
        elasticsearchOperations.indexOps(EventDocument.class).refresh();
    }

    private NativeQuery buildFilterQuery(
        List<EventStatus> allowedStatuses, EventCategory category,
        List<String> techStackNames, UUID sellerId, Pageable pageable) {

        var filterQuery = new BoolQuery.Builder();

        if (allowedStatuses != null && !allowedStatuses.isEmpty()) {
            List<FieldValue> vals = allowedStatuses.stream().map(s -> FieldValue.of(s.name())).toList();
            filterQuery.filter(Query.of(q -> q.terms(t -> t.field("status").terms(tv -> tv.value(vals)))));
        }
        if (category != null) {
            filterQuery.filter(Query.of(q -> q.term(t -> t.field("category").value(category.name()))));
        }
        if (techStackNames != null && !techStackNames.isEmpty()) {
            List<FieldValue> vals = techStackNames.stream().map(FieldValue::of).toList();
            filterQuery.filter(Query.of(q -> q.terms(t -> t.field("techStacks").terms(tv -> tv.value(vals)))));
        }
        if (sellerId != null) {
            filterQuery.filter(Query.of(q -> q.term(t -> t.field("sellerId").value(sellerId.toString()))));
        }

        return NativeQuery.builder()
            .withFilter(Query.of(q -> q.bool(filterQuery.build())))
            .withPageable(pageable)
            .build();
    }
}
