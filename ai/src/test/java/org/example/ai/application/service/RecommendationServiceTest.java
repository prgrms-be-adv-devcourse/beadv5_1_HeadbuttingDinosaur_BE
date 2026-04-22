package org.example.ai.application.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.ai.common.exception.BusinessException;
import org.example.ai.domain.exception.AiErrorCode;
import org.example.ai.domain.model.UserVector;
import org.example.ai.domain.repository.EventRepository;
import org.example.ai.domain.repository.MemberRepository;
import org.example.ai.domain.repository.TechStackEmbeddingRepository;
import org.example.ai.domain.repository.UserVectorRepository;

import org.example.ai.infrastructure.external.dto.res.PopularEventListResponse;
import org.example.ai.infrastructure.external.dto.res.UserTechStackResponse;
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
public class RecommendationServiceTest {

    @Spy
    @InjectMocks
    private RecommendationService recommendationService;

    @Mock
    private UserVectorRepository userVectorRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TechStackEmbeddingRepository techStackEmbeddingRepository;

    @Mock
    private EventRepository eventRepository;

    private UserVector buildNormalUserVector() {
        return UserVector.builder()
            .userId("user-1")
            .preferenceVector(new float[1536])
            .preferenceWeightSum(20f)
            .cartVector(new float[1536])
            .cartWeightSum(0f)
            .recentVector(new float[1536])
            .recentWeightSum(0f)
            .negativeVector(new float[1536])
            .negativeWeightSum(0f)
            .updatedAt("2026-04-16T00:00:00Z")
            .build();
    }

    private List<Map<String, Object>> buildMockCandidates() {
        List<Double> mockEmbedding = new java.util.ArrayList<>();
        for (int i = 0; i < 1536; i++) mockEmbedding.add(0.1);

        return List.of(
            Map.of("eventId", "event-1", "embedding", mockEmbedding),
            Map.of("eventId", "event-2", "embedding", mockEmbedding),
            Map.of("eventId", "event-3", "embedding", mockEmbedding),
            Map.of("eventId", "event-4", "embedding", mockEmbedding),
            Map.of("eventId", "event-5", "embedding", mockEmbedding),
            Map.of("eventId", "event-6", "embedding", mockEmbedding),
            Map.of("eventId", "event-7", "embedding", mockEmbedding)
        );
    }

    // ======================== 1. userVector 없음 → 콜드 스타트 분기 ======================== //

    @Test
    @DisplayName("UserVector 없음 → 콜드 스타트 분기")
    void userVector_없을때_콜드스타트_분기() {
        // given
        given(userVectorRepository.findById("user-1"))
            .willReturn(Optional.empty());

        given(memberRepository.getUserTechStack("user-1"))
            .willReturn(new UserTechStackResponse("user-1", List.of()));

        given(eventRepository.getPopularEvents(any()))
            .willReturn(new PopularEventListResponse(List.of()));



        // when
        RecommendationResponse response = recommendationService
            .recommendByUserVector(new RecommendationRequest("user-1"));

        // then
        assertThat(response.userId()).isEqualTo("user-1");
    }

    // ======================== 2. searchKnn 실패 → BusinessException ======================== //

    @Test
    @DisplayName("searchKnn 실패 → BusinessException")
    void searchKnn_실패시_예외_던지기() {
        // given
        given(userVectorRepository.findById("user-1"))
            .willReturn(Optional.of(buildNormalUserVector()));

        doThrow(new BusinessException(AiErrorCode.EVENT_INDEX_SEARCH_FAILED))
            .when(recommendationService).searchKnn(any(), eq(30));

        // when & then
        assertThatThrownBy(() -> recommendationService
            .recommendByUserVector(new RecommendationRequest("user-1")))
            .isInstanceOf(BusinessException.class);
    }

    // ======================== 3. 정상 흐름 → topEventIds 5개 반환 ======================== //

    @Test
    @DisplayName("정상 흐름 → topEventIds 5개 반환")
    void 정상흐름_topEventIds_5개_반환() {
        // given
        given(userVectorRepository.findById("user-1"))
            .willReturn(Optional.of(buildNormalUserVector()));

        doReturn(buildMockCandidates()).when(recommendationService).searchKnn(any(), eq(30));

        // when
        RecommendationResponse response = recommendationService
            .recommendByUserVector(new RecommendationRequest("user-1"));

        // then
        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.eventIdList().size()).isEqualTo(5);
    }
}