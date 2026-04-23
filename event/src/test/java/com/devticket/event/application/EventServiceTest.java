package com.devticket.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.devticket.event.common.exception.BusinessException;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.exception.EventErrorCode;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.model.EventView;
import com.devticket.event.fixture.EventTestFixture;
import com.devticket.event.infrastructure.client.AdminClient;
import com.devticket.event.infrastructure.client.MemberClient;
import com.devticket.event.infrastructure.client.OpenAiEmbeddingClient;
import com.devticket.event.infrastructure.client.dto.TechStackItem;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.infrastructure.persistence.EventViewRepository;
import com.devticket.event.infrastructure.search.EventDocument;
import com.devticket.event.presentation.dto.EventDetailResponse;
import com.devticket.event.presentation.dto.EventListRequest;
import com.devticket.event.presentation.dto.EventListResponse;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import com.devticket.event.presentation.dto.SellerEventCreateResponse;
import com.devticket.event.presentation.dto.SellerEventDetailResponse;
import com.devticket.event.presentation.dto.SellerEventSummaryResponse;
import com.devticket.event.presentation.dto.SellerEventUpdateRequest;
import com.devticket.event.presentation.dto.SellerEventUpdateResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private MemberClient memberClient;

    @Mock
    private AdminClient adminClient;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private OpenAiEmbeddingClient openAiEmbeddingClient;

    @Mock
    private EventViewRepository eventViewRepository;

    @InjectMocks
    private EventService eventService;

    @BeforeEach
    void setUp() {
        lenient().when(adminClient.getTechStacks()).thenReturn(List.of(
            new TechStackItem(1L, "Java"),
            new TechStackItem(2L, "Spring"),
            new TechStackItem(3L, "Kotlin")
        ));
    }

    // ── 검색 히트 목 헬퍼 ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private SearchHits<EventDocument> mockSearchHitsWithEvent(UUID eventId) {
        EventDocument doc = EventDocument.builder()
            .id(eventId.toString())
            .build();
        SearchHit<EventDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);

        SearchHits<EventDocument> searchHits = mock(SearchHits.class);
        when(searchHits.stream()).thenReturn(Stream.of(hit));
        when(searchHits.getTotalHits()).thenReturn(1L);
        return searchHits;
    }

    @SuppressWarnings("unchecked")
    private SearchHits<EventDocument> mockEmptySearchHits() {
        SearchHits<EventDocument> searchHits = mock(SearchHits.class);
        when(searchHits.stream()).thenReturn(Stream.empty());
        // getTotalHits()는 빈 결과 경로에서 호출되지 않으므로 stub 불필요
        return searchHits;
    }

    // ── 이벤트 생성 테스트 ────────────────────────────────────────────────

    @Test
    void 정상적인_조건일_경우_이벤트가_성공적으로_생성되고_UUID를_반환한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        SellerEventCreateRequest request = EventTestFixture.createEventRequest(
            now.plusDays(4), now.plusDays(10), now.plusDays(15), 100, 4
        );

        UUID expectedUuid = UUID.randomUUID();
        Event savedEvent = EventTestFixture.createEvent(sellerId);
        ReflectionTestUtils.setField(savedEvent, "eventId", expectedUuid);

        when(eventRepository.save(argThat(event -> event != null))).thenReturn(savedEvent);

        // when
        SellerEventCreateResponse response = eventService.createEvent(sellerId, request);

        // then
        assertThat(response.eventId()).isEqualTo(expectedUuid);
        assertThat(response.status()).isEqualTo(EventStatus.ON_SALE);

        // save 3회 호출 — (1) 이벤트 본체 + (2) techStackIds + (3) imageUrls (fixture 에 "url1" 포함)
        verify(eventRepository, times(3)).save(argThat(event -> event != null));
    }

    @Test
    void 판매시작일이_등록시점_기준_3일_이내면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        // 판매 시작일을 과거로 설정 → saleStartAt.isBefore(now) 조건 충족
        SellerEventCreateRequest request = EventTestFixture.createEventRequest(
            now.minusDays(1), now.plusDays(10), now.plusDays(15), 100, 4
        );

        // when & then
        assertThatThrownBy(() -> eventService.createEvent(sellerId, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.REGISTRATION_TIME_EXCEEDED.getMessage());
    }

    @Test
    void 인당_최대구매수량이_총수량보다_크면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        SellerEventCreateRequest request = EventTestFixture.createEventRequest(
            now.plusDays(4), now.plusDays(10), now.plusDays(15), 10, 20
        );

        // when & then
        assertThatThrownBy(() -> eventService.createEvent(sellerId, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.MAX_QUANTITY_EXCEEDED.getMessage());
    }

    @Test
    void 행사일이_판매종료일보다_빠르면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        // 행사일(plusDays(5))이 판매종료일(plusDays(10))보다 빠른 모순된 상황
        SellerEventCreateRequest request = EventTestFixture.createEventRequest(
            now.plusDays(4), now.plusDays(10), now.plusDays(5), 100, 4
        );

        // when & then
        assertThatThrownBy(() -> eventService.createEvent(sellerId, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.INVALID_EVENT_DATE.getMessage());
    }

    // ── 이벤트 상세 조회 테스트 ───────────────────────────────────────────

    @Test
    void 존재하는_이벤트_조회시_상세정보를_반환한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEvent(sellerId);
        UUID eventId = event.getEventId();

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));
        // memberClient.getNickname() 은 기본 null 반환 — CI 환경에서 일부 응답 합성 경로가 null 을 허용하지 않을 수 있어
        // 명시적 stub 으로 NPE 재발 방지 (CI run 24766734663 재현 차단)
        when(memberClient.getNickname(sellerId)).thenReturn("tester");
        when(eventViewRepository.findByEvent(event)).thenReturn(Optional.of(EventView.of(event)));

        // when
        EventDetailResponse response = eventService.getEvent(eventId);

        // then
        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.title()).isEqualTo("상세 조회 테스트 밋업");
        verify(eventRepository).findWithDetailsByEventId(eventId);
    }

    @Test
    void 존재하지_않는_이벤트_조회시_예외가_발생한다() {
        // given
        UUID invalidEventId = UUID.randomUUID();
        when(eventRepository.findWithDetailsByEventId(invalidEventId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventService.getEvent(invalidEventId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.EVENT_NOT_FOUND.getMessage());
    }

    // ── 이벤트 목록 조회 및 검색 테스트 ──────────────────────────────────

    @Test
    void 검색_조건이_주어지면_파라미터를_분해하여_Repository를_호출한다() {
        // given
        EventListRequest request = new EventListRequest("스프링", EventCategory.MEETUP, List.of(1L, 2L), null, null);
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

        UUID eventId = UUID.randomUUID();
        Event mockEvent = EventTestFixture.createEvent(UUID.randomUUID());
        ReflectionTestUtils.setField(mockEvent, "eventId", eventId);

        doReturn(mockSearchHitsWithEvent(eventId))
            .when(elasticsearchOperations).search(any(Query.class), eq(EventDocument.class));
        when(eventRepository.findAllWithDetailsByEventIdIn(anyList())).thenReturn(List.of(mockEvent));

        // when (currentUserId는 일반 조회이므로 null로 전달)
        EventListResponse response = eventService.getEventList(request, null, pageable);

        // then
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.content()).isNotEmpty();
        verify(elasticsearchOperations).search(any(Query.class), any());
    }

    @Test
    void 검색_조건이_모두_null일_경우_전체_이벤트를_조회한다() {
        // given
        EventListRequest request = new EventListRequest(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        UUID eventId = UUID.randomUUID();
        Event mockEvent = EventTestFixture.createEvent(UUID.randomUUID());
        ReflectionTestUtils.setField(mockEvent, "eventId", eventId);

        doReturn(mockSearchHitsWithEvent(eventId))
            .when(elasticsearchOperations).search(any(Query.class), eq(EventDocument.class));
        when(eventRepository.findAllWithDetailsByEventIdIn(anyList())).thenReturn(List.of(mockEvent));

        // when
        EventListResponse response = eventService.getEventList(request, null, pageable);

        // then
        assertThat(response).isNotNull();
        verify(elasticsearchOperations).search(any(Query.class), any());
    }

    @Test
    void 검색_결과가_없을_경우_빈_리스트를_정상적으로_반환한다() {
        // given
        EventListRequest request = new EventListRequest("절대검색안될키워드", null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        doReturn(mockEmptySearchHits())
            .when(elasticsearchOperations).search(any(Query.class), eq(EventDocument.class));

        // when
        EventListResponse response = eventService.getEventList(request, null, pageable);

        // then
        assertThat(response.totalElements()).isZero();
        assertThat(response.content()).isEmpty();
    }

    // ── 판매자 등록 이벤트 목록 조회 테스트 ──────────────────────────────

    @Test
    void 일반_조회시_공개된_상태의_이벤트만_조회조건으로_전달된다() {
        // given
        EventListRequest request = new EventListRequest("스프링", EventCategory.MEETUP, List.of(1L, 2L), null, null);
        Pageable pageable = PageRequest.of(0, 20);

        UUID eventId = UUID.randomUUID();
        Event mockEvent = EventTestFixture.createEvent(UUID.randomUUID());
        ReflectionTestUtils.setField(mockEvent, "eventId", eventId);

        doReturn(mockSearchHitsWithEvent(eventId))
            .when(elasticsearchOperations).search(any(Query.class), eq(EventDocument.class));
        when(eventRepository.findAllWithDetailsByEventIdIn(anyList())).thenReturn(List.of(mockEvent));

        // when (currentUserId가 null → 내부적으로 공개 상태 필터 적용)
        EventListResponse response = eventService.getEventList(request, null, pageable);

        // then
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.content()).isNotEmpty();
        // ES 검색이 호출되었음을 확인 (공개 상태 필터는 NativeQuery 내부에 포함됨)
        verify(elasticsearchOperations).search(any(Query.class), any());
    }

    @Test
    void 판매자_본인_전체조회시_상태제한없이_전달된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        EventListRequest request = new EventListRequest(null, null, null, sellerId, null);
        Pageable pageable = PageRequest.of(0, 20);

        UUID eventId = UUID.randomUUID();
        Event mockEvent = EventTestFixture.createEvent(sellerId);
        ReflectionTestUtils.setField(mockEvent, "eventId", eventId);

        doReturn(mockSearchHitsWithEvent(eventId))
            .when(elasticsearchOperations).search(any(Query.class), eq(EventDocument.class));
        when(eventRepository.findAllWithDetailsByEventIdIn(anyList())).thenReturn(List.of(mockEvent));

        // when (currentUserId와 request의 sellerId가 일치 → 상태 제한 없음)
        EventListResponse response = eventService.getEventList(request, sellerId, pageable);

        // then
        assertThat(response).isNotNull();
        verify(elasticsearchOperations).search(any(Query.class), any());
    }

    @Test
    void 판매자_본인_특정상태_조회시_해당상태가_전달된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        EventListRequest request = new EventListRequest(null, null, null, sellerId, EventStatus.DRAFT);
        Pageable pageable = PageRequest.of(0, 20);

        UUID eventId = UUID.randomUUID();
        Event mockEvent = EventTestFixture.createEvent(sellerId);
        ReflectionTestUtils.setField(mockEvent, "eventId", eventId);

        doReturn(mockSearchHitsWithEvent(eventId))
            .when(elasticsearchOperations).search(any(Query.class), eq(EventDocument.class));
        when(eventRepository.findAllWithDetailsByEventIdIn(anyList())).thenReturn(List.of(mockEvent));

        // when (본인 이벤트 DRAFT 조회 허용)
        EventListResponse response = eventService.getEventList(request, sellerId, pageable);

        // then
        assertThat(response).isNotNull();
        verify(elasticsearchOperations).search(any(Query.class), any());
    }

    @Test
    void 타인_비공개_이벤트_조회시_권한예외_발생() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID(); // 다른 사용자 (타인)
        EventListRequest request = new EventListRequest(null, null, null, sellerId, EventStatus.DRAFT);
        Pageable pageable = PageRequest.of(0, 20);

        // when & then
        assertThatThrownBy(() -> eventService.getEventList(request, otherUserId, pageable))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.UNAUTHORIZED_SELLER.getMessage());
    }

    @Test
    void 비로그인_사용자_비공개_이벤트_조회시_권한예외_발생() {
        // given
        EventListRequest request = new EventListRequest(null, null, null, null, EventStatus.DRAFT);
        Pageable pageable = PageRequest.of(0, 20);

        // when & then (currentUserId에 null 전달)
        assertThatThrownBy(() -> eventService.getEventList(request, null, pageable))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.UNAUTHORIZED_SELLER.getMessage());
    }

    @Test
    void 검색결과가_없을경우_빈_리스트반환() {
        // given
        EventListRequest request = new EventListRequest("없는키워드", null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        doReturn(mockEmptySearchHits())
            .when(elasticsearchOperations).search(any(Query.class), eq(EventDocument.class));

        // when
        EventListResponse response = eventService.getEventList(request, null, pageable);

        // then
        assertThat(response.totalElements()).isZero();
        assertThat(response.content()).isEmpty();
    }

    // ── 판매자 이벤트 상세 조회 테스트 ───────────────────────────────────

    @Test
    void 판매자_본인_이벤트_상세조회_성공시_SellerEventDetailResponse를_반환한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEvent(sellerId);
        UUID eventId = event.getEventId();

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when
        SellerEventDetailResponse response = eventService.getSellerEventDetail(sellerId, eventId);

        // then
        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.title()).isEqualTo("상세 조회 테스트 밋업");
        verify(eventRepository).findWithDetailsByEventId(eventId);
    }

    @Test
    void 판매자_이벤트_상세조회시_이벤트가_없으면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID invalidEventId = UUID.randomUUID();

        when(eventRepository.findWithDetailsByEventId(invalidEventId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventService.getSellerEventDetail(sellerId, invalidEventId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.EVENT_NOT_FOUND.getMessage());
    }

    @Test
    void 판매자_이벤트_상세조회시_본인_이벤트가_아니면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID otherSellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEvent(otherSellerId);
        UUID eventId = event.getEventId();

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.getSellerEventDetail(sellerId, eventId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.UNAUTHORIZED_SELLER.getMessage());
    }

    // ── 판매자 이벤트 현황 조회 테스트 ───────────────────────────────────

    @Test
    void 판매자_본인_이벤트_현황조회_성공시_SellerEventSummaryResponse를_반환한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEvent(sellerId);
        UUID eventId = event.getEventId();

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.of(event));

        // when
        SellerEventSummaryResponse response = eventService.getEventSummary(sellerId, eventId);

        // then
        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.title()).isEqualTo("상세 조회 테스트 밋업");
        assertThat(response.totalQuantity()).isEqualTo(100);
        assertThat(response.soldQuantity()).isZero();            // 새로 생성된 이벤트는 판매량 0
        assertThat(response.remainingQuantity()).isEqualTo(100);
        verify(eventRepository).findByEventId(eventId);
    }

    @Test
    void 판매자_이벤트_현황조회시_이벤트가_없으면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID invalidEventId = UUID.randomUUID();

        when(eventRepository.findByEventId(invalidEventId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventService.getEventSummary(sellerId, invalidEventId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.EVENT_NOT_FOUND.getMessage());
    }

    @Test
    void 판매자_이벤트_현황조회시_본인_이벤트가_아니면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID otherSellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEvent(otherSellerId);
        UUID eventId = event.getEventId();

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.getEventSummary(sellerId, eventId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.UNAUTHORIZED_SELLER.getMessage());
    }

    // ── 이벤트 수정 및 판매 중지 테스트 ──────────────────────────────────

    @Test
    void 정상적인_판매_중지_성공() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEventWithStatus(sellerId, EventStatus.DRAFT);
        UUID eventId = event.getEventId();
        SellerEventUpdateRequest cancelRequest = EventTestFixture.createUpdateEventRequest_Cancel();

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when
        SellerEventUpdateResponse response = eventService.updateEvent(sellerId, eventId, cancelRequest);

        // then
        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void 취소_불가_상태_이벤트_취소시_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEventWithStatus(sellerId, EventStatus.CANCELLED);
        UUID eventId = event.getEventId();
        SellerEventUpdateRequest cancelRequest = EventTestFixture.createUpdateEventRequest_Cancel();

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.updateEvent(sellerId, eventId, cancelRequest))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.CANNOT_CHANGE_STATUS.getMessage());
    }

    @Test
    void 판매_중지시_다른_판매자의_이벤트이면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEventWithStatus(UUID.randomUUID(), EventStatus.DRAFT);
        UUID eventId = event.getEventId();
        SellerEventUpdateRequest cancelRequest = EventTestFixture.createUpdateEventRequest_Cancel();

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.updateEvent(sellerId, eventId, cancelRequest))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.UNAUTHORIZED_SELLER.getMessage());
    }

    @Test
    void 이벤트_수정에_성공한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEventWithStatus(sellerId, EventStatus.DRAFT);
        UUID eventId = event.getEventId();

        SellerEventUpdateRequest updateRequest = new SellerEventUpdateRequest(
            "수정된_제목", "수정된_설명", "수정된_위치",
            LocalDateTime.now().plusDays(20),
            LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(15),
            60000, 150, 5, EventCategory.CONFERENCE,
            List.of(3L, 4L), List.of("newUrl"), null
        );

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when
        SellerEventUpdateResponse response = eventService.updateEvent(sellerId, eventId, updateRequest);

        // then - 응답 검증
        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.status()).isEqualTo(EventStatus.DRAFT);

        // 실제 변경된 필드 검증 (Event 객체 상태 확인)
        assertThat(event.getTitle()).isEqualTo("수정된_제목");
        assertThat(event.getPrice()).isEqualTo(60000);
        assertThat(event.getTotalQuantity()).isEqualTo(150);
        assertThat(event.getMaxQuantity()).isEqualTo(5);
    }

    @Test
    void 필수_필드가_누락되면_수정에_실패한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEventWithStatus(sellerId, EventStatus.DRAFT);
        UUID eventId = event.getEventId();
        SellerEventUpdateRequest updateRequest = new SellerEventUpdateRequest(
            null, "설명", "위치", LocalDateTime.now().plusDays(15),
            LocalDateTime.now().plusDays(4), LocalDateTime.now().plusDays(10),
            50000, 100, 4, EventCategory.MEETUP, List.of(1L), List.of("url"), null
        );

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.updateEvent(sellerId, eventId, updateRequest))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.INVALID_REQUEST.getMessage());
    }

    @Test
    void 유효하지_않은_가격이면_수정에_실패한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEventWithStatus(sellerId, EventStatus.DRAFT);
        UUID eventId = event.getEventId();
        SellerEventUpdateRequest updateRequest = new SellerEventUpdateRequest(
            "제목", "설명", "위치", LocalDateTime.now().plusDays(15),
            LocalDateTime.now().plusDays(4), LocalDateTime.now().plusDays(10),
            -1000, 100, 4, EventCategory.MEETUP, List.of(1L), List.of("url"), null
        );

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.updateEvent(sellerId, eventId, updateRequest))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.INVALID_PRICE.getMessage());
    }

    @Test
    void 유효하지_않은_수량이면_수정에_실패한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEventWithStatus(sellerId, EventStatus.DRAFT);
        UUID eventId = event.getEventId();
        SellerEventUpdateRequest updateRequest = new SellerEventUpdateRequest(
            "제목", "설명", "위치", LocalDateTime.now().plusDays(15),
            LocalDateTime.now().plusDays(4), LocalDateTime.now().plusDays(10),
            50000, 4, 4, EventCategory.MEETUP, List.of(1L), List.of("url"), null
        );

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.updateEvent(sellerId, eventId, updateRequest))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.INVALID_QUANTITY.getMessage());
    }

    @Test
    void 판매된_수량보다_총수량을_줄이면_수정에_실패한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEventWithStatus(sellerId, EventStatus.ON_SALE);
        EventTestFixture.adjustQuantity(event, 80); // 판매된 수량: 80, 남은 수량: 20
        UUID eventId = event.getEventId();
        SellerEventUpdateRequest updateRequest = new SellerEventUpdateRequest(
            "제목", "설명", "위치", LocalDateTime.now().plusDays(15),
            LocalDateTime.now().plusDays(4), LocalDateTime.now().plusDays(10),
            50000, 50, 4, EventCategory.MEETUP, List.of(1L), List.of("url"), null
        );

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.updateEvent(sellerId, eventId, updateRequest))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.TOTAL_QUANTITY_BELOW_SOLD.getMessage());
    }

    @Test
    void 판매_시작일이_종료일_이후이면_수정에_실패한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEventWithStatus(sellerId, EventStatus.DRAFT);
        UUID eventId = event.getEventId();
        LocalDateTime saleEnd = LocalDateTime.now().plusDays(10);
        SellerEventUpdateRequest updateRequest = new SellerEventUpdateRequest(
            "제목", "설명", "위치", LocalDateTime.now().plusDays(15),
            saleEnd.plusDays(1), saleEnd, // saleStartAt > saleEndAt
            50000, 100, 4, EventCategory.MEETUP, List.of(1L), List.of("url"), null
        );

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.updateEvent(sellerId, eventId, updateRequest))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.INVALID_SALE_PERIOD.getMessage());
    }

    @Test
    void 판매_종료일이_행사일_이후이면_수정에_실패한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEventWithStatus(sellerId, EventStatus.DRAFT);
        UUID eventId = event.getEventId();
        LocalDateTime eventDate = LocalDateTime.now().plusDays(10);
        SellerEventUpdateRequest updateRequest = new SellerEventUpdateRequest(
            "제목", "설명", "위치", eventDate,
            LocalDateTime.now().plusDays(4), eventDate.plusDays(1), // saleEndAt > eventDateTime
            50000, 100, 4, EventCategory.MEETUP, List.of(1L), List.of("url"), null
        );

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.updateEvent(sellerId, eventId, updateRequest))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.INVALID_EVENT_DATE.getMessage());
    }

    @Test
    void 수정_불가_상태이면_수정에_실패한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEventWithStatus(sellerId, EventStatus.SOLD_OUT);
        UUID eventId = event.getEventId();
        SellerEventUpdateRequest updateRequest = EventTestFixture.createUpdateEventRequest();

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.updateEvent(sellerId, eventId, updateRequest))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.CANNOT_CHANGE_STATUS.getMessage());
    }

    @Test
    void 수정시_다른_판매자의_이벤트이면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Event event = EventTestFixture.createEventWithStatus(UUID.randomUUID(), EventStatus.DRAFT);
        UUID eventId = event.getEventId();
        SellerEventUpdateRequest updateRequest = EventTestFixture.createUpdateEventRequest();

        when(eventRepository.findWithDetailsByEventId(eventId)).thenReturn(Optional.of(event));

        // when & then
        assertThatThrownBy(() -> eventService.updateEvent(sellerId, eventId, updateRequest))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(EventErrorCode.UNAUTHORIZED_SELLER.getMessage());
    }
}
