package org.example.ai.application;


import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.ai.application.service.RecommendationService;
import org.example.ai.domain.model.UserVector;
import org.example.ai.domain.repository.UserVectorRepository;
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

    // ======================== 1. userVector 없음 ======================== //
    @Test
    @DisplayName("UserVector 없음 → 빈 리스트 반환")
    void userVector_없을때_빈리스트_반환() {
        // given
        given(userVectorRepository.findById("user-1"))
            .willReturn(Optional.empty());

        // when
        RecommendationResponse response = recommendationService.recommendByUserVector(new RecommendationRequest("user-1"));

        // then
        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.eventIdList().isEmpty()).isTrue();
    }


    // ======================== 2. searchKnn 실패 ======================== //

    @Test
    @DisplayName("searchKnn 실패 → BusinessException")
    void searchKnn_실패시_예외_던지기() {
        float[] normalizedVector = new float[1536];
        // given
        UserVector userVector = UserVector.builder()
            .userId("user-1")
            .preferenceVector(new float[1536])
            .preferenceWeightSum(0f)
            .cartVector(new float[1536])
            .cartWeightSum(0f)
            .recentVector(new float[1536])
            .recentWeightSum(0f)
            .negativeVector(new float[1536])
            .negativeWeightSum(0f)
            .updatedAt("2026-04-16T00:00:00Z")
            .build();

        given(userVectorRepository.findById("user-1"))
            .willReturn(Optional.of(userVector));

        doReturn(null).when(recommendationService).searchKnn(any());

        // when & then
        assertThatThrownBy(() -> recommendationService
            .recommendByUserVector(new RecommendationRequest("user-1")))
            .isInstanceOf(Exception.class);
    }

    // ======================== 3. 정상 흐름 ======================== //

    @Test
    @DisplayName("정상 흐름 → topEventIds 5개 반환")
    void 정상흐름_topEventIds_5개_반환() {
        // given
        UserVector userVector = UserVector.builder()
            .userId("user-1")
            .preferenceVector(new float[1536])
            .preferenceWeightSum(0f)
            .cartVector(new float[1536])
            .cartWeightSum(0f)
            .recentVector(new float[1536])
            .recentWeightSum(0f)
            .negativeVector(new float[1536])
            .negativeWeightSum(0f)
            .updatedAt("2026-04-16T00:00:00Z")
            .build();

        given(userVectorRepository.findById("user-1"))
            .willReturn(Optional.of(userVector));

        // mock candidates - embedding은 1536 크기 double 리스트
        List<Double> mockEmbedding = new java.util.ArrayList<>();
        for (int i = 0; i < 1536; i++) mockEmbedding.add(0.1);

        List<Map<String, Object>> mockCandidates = List.of(
            Map.of("eventId", "event-1", "embedding", mockEmbedding),
            Map.of("eventId", "event-2", "embedding", mockEmbedding),
            Map.of("eventId", "event-3", "embedding", mockEmbedding),
            Map.of("eventId", "event-4", "embedding", mockEmbedding),
            Map.of("eventId", "event-5", "embedding", mockEmbedding),
            Map.of("eventId", "event-6", "embedding", mockEmbedding),
            Map.of("eventId", "event-7", "embedding", mockEmbedding)
        );

        doReturn(mockCandidates).when(recommendationService).searchKnn(any());

        // when
        RecommendationResponse response = recommendationService
            .recommendByUserVector(new RecommendationRequest("user-1"));

        // then
        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.eventIdList().size()).isEqualTo(5);
    }

}
