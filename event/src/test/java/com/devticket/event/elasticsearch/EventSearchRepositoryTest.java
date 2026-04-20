package com.devticket.event.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.devticket.event.elasticsearch.support.ElasticsearchIntegrationTestBase;
import com.devticket.event.infrastructure.client.MemberClient;
import com.devticket.event.infrastructure.client.OpenAiEmbeddingClient;
import com.devticket.event.infrastructure.search.EventDocument;
import com.devticket.event.infrastructure.search.EventSearchRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

/**
 * EventSearchRepository CRUD 통합 테스트.
 *
 * @SpringBootTest(webEnvironment=NONE) — 전체 컨텍스트 로드, HTTP 서버 불필요.
 * spring.kafka.listener.auto-startup=false — Kafka 리스너 자동 기동 억제.
 * Testcontainers ES 9.x 컨테이너로 실제 인덱스 동작 검증.
 */
@Tag("elasticsearch")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.kafka.listener.auto-startup=false"}
)
@ActiveProfiles("test")
class EventSearchRepositoryTest extends ElasticsearchIntegrationTestBase {

    @Autowired
    private EventSearchRepository eventSearchRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @MockitoBean
    private OpenAiEmbeddingClient openAiEmbeddingClient;

    @MockitoBean
    private MemberClient memberClient;

    @BeforeEach
    void setUp() {
        eventSearchRepository.deleteAll();
        elasticsearchOperations.indexOps(EventDocument.class).refresh();
    }

    // ── 저장 및 조회 ──────────────────────────────────────────────────────

    @Test
    void save_후_findById로_저장된_document를_조회할_수_있다() {
        // given
        EventDocument doc = sampleDocument("Spring 심화 밋업", "ON_SALE", "MEETUP");

        // when
        eventSearchRepository.save(doc);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // then
        Optional<EventDocument> found = eventSearchRepository.findById(doc.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(doc.getId());
    }

    @Test
    void save_후_모든_필드가_정확하게_저장된다() {
        // given
        String id = UUID.randomUUID().toString();
        String sellerId = UUID.randomUUID().toString();
        EventDocument doc = EventDocument.builder()
            .id(id)
            .title("전체 필드 검증 밋업")
            .category("CONFERENCE")
            .status("DRAFT")
            .sellerId(sellerId)
            .techStacks(List.of("Java", "Spring", "Kotlin"))
            .indexedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS))
            .build();

        // when
        eventSearchRepository.save(doc);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // then
        EventDocument found = eventSearchRepository.findById(id).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("전체 필드 검증 밋업");
        assertThat(found.getCategory()).isEqualTo("CONFERENCE");
        assertThat(found.getStatus()).isEqualTo("DRAFT");
        assertThat(found.getSellerId()).isEqualTo(sellerId);
        assertThat(found.getTechStacks()).containsExactlyInAnyOrder("Java", "Spring", "Kotlin");
    }

    // ── 삭제 ─────────────────────────────────────────────────────────────

    @Test
    void deleteById_후_existsById는_false를_반환한다() {
        // given
        EventDocument doc = sampleDocument("삭제 테스트 이벤트", "ON_SALE", "MEETUP");
        eventSearchRepository.save(doc);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // when
        eventSearchRepository.deleteById(doc.getId());
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // then
        assertThat(eventSearchRepository.existsById(doc.getId())).isFalse();
    }

    @Test
    void deleteAll_후_count는_0을_반환한다() {
        // given
        eventSearchRepository.save(sampleDocument("이벤트1", "ON_SALE", "MEETUP"));
        eventSearchRepository.save(sampleDocument("이벤트2", "SOLD_OUT", "CONFERENCE"));
        elasticsearchOperations.indexOps(EventDocument.class).refresh();
        assertThat(eventSearchRepository.count()).isEqualTo(2);

        // when
        eventSearchRepository.deleteAll();
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // then
        assertThat(eventSearchRepository.count()).isZero();
    }

    // ── 복수 저장 ─────────────────────────────────────────────────────────

    @Test
    void 여러_document를_save하면_count가_정확히_일치한다() {
        // given
        eventSearchRepository.save(sampleDocument("이벤트A", "ON_SALE", "MEETUP"));
        eventSearchRepository.save(sampleDocument("이벤트B", "DRAFT", "CONFERENCE"));
        eventSearchRepository.save(sampleDocument("이벤트C", "SOLD_OUT", "HACKATHON"));
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // then
        assertThat(eventSearchRepository.count()).isEqualTo(3);
    }

    // ── upsert (덮어쓰기) ─────────────────────────────────────────────────

    @Test
    void 동일한_id로_save를_두_번_호출하면_최신_내용으로_덮어쓴다() {
        // given
        String id = UUID.randomUUID().toString();
        EventDocument original = EventDocument.builder()
            .id(id).title("원본 제목").category("MEETUP").status("DRAFT")
            .sellerId(UUID.randomUUID().toString()).techStacks(List.of())
            .indexedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS))
            .build();
        EventDocument updated = EventDocument.builder()
            .id(id).title("수정된 제목").category("CONFERENCE").status("ON_SALE")
            .sellerId(original.getSellerId()).techStacks(List.of("Java"))
            .indexedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS))
            .build();

        // when
        eventSearchRepository.save(original);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();
        eventSearchRepository.save(updated);
        elasticsearchOperations.indexOps(EventDocument.class).refresh();

        // then
        EventDocument found = eventSearchRepository.findById(id).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("수정된 제목");
        assertThat(found.getCategory()).isEqualTo("CONFERENCE");
        assertThat(found.getStatus()).isEqualTo("ON_SALE");
        assertThat(eventSearchRepository.count()).isEqualTo(1);
    }

    // ── 존재하지 않는 ID ──────────────────────────────────────────────────

    @Test
    void 존재하지_않는_id로_findById를_호출하면_empty를_반환한다() {
        assertThat(eventSearchRepository.findById(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void 존재하지_않는_id로_existsById를_호출하면_false를_반환한다() {
        assertThat(eventSearchRepository.existsById(UUID.randomUUID().toString())).isFalse();
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    private EventDocument sampleDocument(String title, String status, String category) {
        return EventDocument.builder()
            .id(UUID.randomUUID().toString())
            .title(title)
            .category(category)
            .status(status)
            .sellerId(UUID.randomUUID().toString())
            .techStacks(List.of())
            .indexedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS))
            .build();
    }
}
