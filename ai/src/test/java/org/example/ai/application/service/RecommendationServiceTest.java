package org.example.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.example.ai.domain.model.UserVector;
import org.example.ai.domain.repository.EventRepository;
import org.example.ai.domain.repository.MemberRepository;
import org.example.ai.domain.repository.TechStackEmbeddingRepository;
import org.example.ai.domain.repository.UserVectorRepository;
import org.example.ai.infrastructure.external.dto.req.PopularEventListRequest;
import org.example.ai.infrastructure.external.dto.res.PopularEventListResponse;
import org.example.ai.infrastructure.external.dto.res.PopularEventListResponse.EventInfo;
import org.example.ai.infrastructure.external.dto.res.UserTechStackResponse;
import org.example.ai.infrastructure.external.dto.res.UserTechStackResponse.TechStackInfo;
import org.example.ai.presentation.dto.req.RecommendationRequest;
import org.example.ai.presentation.dto.res.RecommendationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Spy
    @InjectMocks
    RecommendationService recommendationService;

    @Mock UserVectorRepository userVectorRepository;
    @Mock TechStackEmbeddingRepository techStackEmbeddingRepository;
    @Mock ElasticsearchClient elasticsearchClient;
    @Mock MemberRepository memberRepository;
    @Mock EventRepository eventRepository;

    private static final String USER_ID = "user-1";
    private static final RecommendationRequest REQUEST = new RecommendationRequest(USER_ID);

    // ────────────────────────────────────────────
    // 콜드스타트 진입 조건
    // ────────────────────────────────────────────

    @Test
    @DisplayName("UserVector가 없으면 콜드스타트로 진입한다")
    void userVector_없으면_콜드스타트_진입() {
        // given
        given(userVectorRepository.findById(USER_ID)).willReturn(Optional.empty());
        given(memberRepository.getUserTechStack(USER_ID))
            .willReturn(new UserTechStackResponse(USER_ID, List.of()));
        given(eventRepository.getPopularEvents(any())).willReturn(popularResponse(5));

        // when
        RecommendationResponse result = recommendationService.recommendByUserVector(REQUEST);

        // then
        verify(recommendationService).recommendByColdStart(any(), any());
        assertThat(result.eventIdList()).hasSize(5);
    }

    @Test
    @DisplayName("weightSum < 20이면 콜드스타트로 진입한다")
    void weightSum_20미만이면_콜드스타트_진입() {
        // given
        given(userVectorRepository.findById(USER_ID))
            .willReturn(Optional.of(userVectorWithWeightSum(3f, 3f, 3f))); // 합 = 9
        given(memberRepository.getUserTechStack(USER_ID))
            .willReturn(new UserTechStackResponse(USER_ID, List.of()));
        given(eventRepository.getPopularEvents(any())).willReturn(popularResponse(5));

        // when
        RecommendationResponse result = recommendationService.recommendByUserVector(REQUEST);

        // then
        verify(recommendationService).recommendByColdStart(any(), any());
        assertThat(result.eventIdList()).hasSize(5);
    }

    @Test
    @DisplayName("weightSum >= 20이면 일반 추천으로 진입한다")
    void weightSum_20이상이면_일반추천_진입() {
        // given
        given(userVectorRepository.findById(USER_ID))
            .willReturn(Optional.of(userVectorWithWeightSum(10f, 5f, 5f))); // 합 = 20
        doReturn(knnCandidates(5)).when(recommendationService).searchKnn(any(), anyInt());

        // when
        RecommendationResponse result = recommendationService.recommendByUserVector(REQUEST);

        // then
        verify(recommendationService, never()).recommendByColdStart(any(), any());
        assertThat(result.eventIdList()).hasSize(5);
    }

    // ────────────────────────────────────────────
    // 콜드스타트 내부 — 테크스택 kNN
    // ────────────────────────────────────────────

    @Test
    @DisplayName("콜드스타트: 테크스택 임베딩이 있으면 kNN이 호출된다")
    void 콜드스타트_테크스택_임베딩_있으면_kNN_호출() {
        // given
        given(memberRepository.getUserTechStack(USER_ID))
            .willReturn(new UserTechStackResponse(USER_ID,
                List.of(new TechStackInfo("stack-1", "Java"))));
        given(techStackEmbeddingRepository.findEmbeddingByName("Java"))
            .willReturn(Optional.of(new float[1536]));
        doReturn(knnCandidates(5)).when(recommendationService).searchKnn(any(), anyInt());

        // when
        RecommendationResponse result = recommendationService.recommendByColdStart(REQUEST, null);

        // then
        verify(recommendationService).searchKnn(any(), anyInt());
        assertThat(result.eventIdList()).hasSize(5);
    }

    @Test
    @DisplayName("콜드스타트: 테크스택 임베딩이 없으면 kNN을 호출하지 않고 popular를 반환한다")
    void 콜드스타트_테크스택_임베딩_없으면_kNN_미호출() {
        // given
        given(memberRepository.getUserTechStack(USER_ID))
            .willReturn(new UserTechStackResponse(USER_ID,
                List.of(new TechStackInfo("stack-1", "UnknownStack"))));
        given(techStackEmbeddingRepository.findEmbeddingByName("UnknownStack"))
            .willReturn(Optional.empty());
        given(eventRepository.getPopularEvents(any())).willReturn(popularResponse(5));

        // when
        RecommendationResponse result = recommendationService.recommendByColdStart(REQUEST, null);

        // then
        verify(recommendationService, never()).searchKnn(any(), anyInt());
        assertThat(result.eventIdList()).hasSize(5);
    }

    @Test
    @DisplayName("콜드스타트: kNN 결과가 3개면 popular로 2개 보충해 총 5개를 반환한다")
    void 콜드스타트_kNN_결과_부족하면_popular_보충() {
        // given
        given(memberRepository.getUserTechStack(USER_ID))
            .willReturn(new UserTechStackResponse(USER_ID,
                List.of(new TechStackInfo("stack-1", "Java"))));
        given(techStackEmbeddingRepository.findEmbeddingByName("Java"))
            .willReturn(Optional.of(new float[1536]));
        doReturn(knnCandidates(3)).when(recommendationService).searchKnn(any(), anyInt());
        given(eventRepository.getPopularEvents(any())).willReturn(popularResponse(2));

        // when
        RecommendationResponse result = recommendationService.recommendByColdStart(REQUEST, null);

        // then
        assertThat(result.eventIdList()).hasSize(5);
        verify(eventRepository).getPopularEvents(new PopularEventListRequest(2));
    }

    @Test
    @DisplayName("콜드스타트: kNN 결과가 5개면 popular를 호출하지 않는다")
    void 콜드스타트_kNN_결과_충분하면_popular_미호출() {
        // given
        given(memberRepository.getUserTechStack(USER_ID))
            .willReturn(new UserTechStackResponse(USER_ID,
                List.of(new TechStackInfo("stack-1", "Java"))));
        given(techStackEmbeddingRepository.findEmbeddingByName("Java"))
            .willReturn(Optional.of(new float[1536]));
        doReturn(knnCandidates(5)).when(recommendationService).searchKnn(any(), anyInt());

        // when
        RecommendationResponse result = recommendationService.recommendByColdStart(REQUEST, null);

        // then
        verify(eventRepository, never()).getPopularEvents(any());
        assertThat(result.eventIdList()).hasSize(5);
    }

    // ────────────────────────────────────────────
    // 헬퍼
    // ────────────────────────────────────────────

    private UserVector userVectorWithWeightSum(
        float preferenceWeightSum, float cartWeightSum, float negativeWeightSum
    ) {
        return UserVector.builder()
            .userId(USER_ID)
            .preferenceVector(new float[1536])
            .cartVector(new float[1536])
            .recentVector(new float[1536])
            .negativeVector(new float[1536])
            .preferenceWeightSum(preferenceWeightSum)
            .cartWeightSum(cartWeightSum)
            .negativeWeightSum(negativeWeightSum)
            .build();
    }

    private PopularEventListResponse popularResponse(int count) {
        List<EventInfo> events = IntStream.range(0, count)
            .mapToObj(i -> new EventInfo("popular-event-" + i))
            .toList();
        return new PopularEventListResponse(events, "성공", 200);
    }

    private List<Map<String, Object>> knnCandidates(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> {
                Map<String, Object> m = new HashMap<>();
                m.put("eventId", "knn-event-" + i);
                return m;
            })
            .toList();
    }
}