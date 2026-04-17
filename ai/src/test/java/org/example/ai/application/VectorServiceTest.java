package org.example.ai.application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.example.ai.application.service.VectorService;
import org.example.ai.domain.model.UserVector;
import org.example.ai.domain.repository.EventEmbeddingRepository;
import org.example.ai.domain.repository.UserVectorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class VectorServiceTest {

    @InjectMocks
    private VectorService vectorService;

    @Mock
    private UserVectorRepository userVectorRepository;

    @Mock
    private EventEmbeddingRepository eventEmbeddingRepository;

    private UserVector buildUserVector(){
        return UserVector.builder()
            .userId("user-1")
            .preferenceVector(new float[1536])
            .preferenceWeightSum(0f)
            .cartVector(new float[1536])
            .cartWeightSum(0f)
            .negativeVector(new float[1536])
            .negativeWeightSum(0f)
            .recentVector(new float[1536])
            .recentWeightSum(0f)
            .updatedAt("2026-04-16T00:00:00Z")
            .build();
    }

    // ================== 부가 메서드 테스트 ================== //
    // 1. updateVector
    @Nested
    @DisplayName("updateVector")
    class UpdateVector{

        @Test
        @DisplayName("임베딩 없음 → 벡터 갱신 안 함")
        void 임베딩_비었을때_임베딩_갱신_안함(){
            // given
            given(eventEmbeddingRepository.findEmbeddingById("event-1"))
                .willReturn(Optional.empty());

            // when
            vectorService.updateVector("user-1", "event-1", 5.0f, "preference");

            // then
            verify(userVectorRepository, never()).save(any());
        }

        @Test
        @DisplayName("유저 벡터 없음 → 신규 UserVector 생성 후 저장")
        void 유저벡터_없을때_신규_유저벡터_생성후_저장(){
            // given
            float[] embedding = new float[1536];
            embedding[0] = 1.0f;

            given(eventEmbeddingRepository.findEmbeddingById("event-1"))
                .willReturn(Optional.of(embedding));
            given(userVectorRepository.findById("user-1"))
                .willReturn(Optional.empty());
            given(userVectorRepository.save(any()))
                .willAnswer(i -> i.getArgument(0));

            // when
            vectorService.updateVector("user-1", "event-1", 5.0f, "preference");

            // then
            ArgumentCaptor<UserVector> captor = ArgumentCaptor.forClass(UserVector.class);
            verify(userVectorRepository).save(captor.capture());

            UserVector saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo("user-1");
            assertThat(saved.getPreferenceVector()).isNotNull();
            assertThat(saved.getPreferenceWeightSum()).isEqualTo(5.0f);

        }
    }

    // 2. getVector
    @Nested
    @DisplayName("getVector")
    class getVector{
        @DisplayName("preference null → 빈 벡터 반환")
        @Test
        void vectorType_null일때_빈벡터_반환(){
            // given
            UserVector userVector = UserVector.builder()
                .userId("user-1")
                .preferenceVector(null)
                .preferenceWeightSum(0f)
                .cartVector(new float[1536])
                .cartWeightSum(0f)
                .negativeVector(new float[1536])
                .negativeWeightSum(0f)
                .recentVector(new float[1536])
                .recentWeightSum(0f)
                .updatedAt("2026-04-16T00:00:00Z")
                .build();

            // when
            float[] result = vectorService.getVector(userVector, "preference");

            // then
            assertThat(result).isNotNull();
            assertThat(result.length).isEqualTo(1536);
            assertThat(result[0]).isEqualTo(0f);
        }

        @Test
        @DisplayName("알 수 없는 vectorType → IllegalArgumentException")
        void vectorType_틀린값일때_예외_던지기(){
            // given
            UserVector userVector = buildUserVector();

            // when, then
            assertThatThrownBy(()->vectorService.getVector(userVector, "abnormal_type"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 vectorType");
        }
    }

    // ======================== getWeightSum ======================== //

    @Nested
    @DisplayName("getWeightSum")
    class getWeightSum{

        @Test
        @DisplayName("알 수 없는 vectorType → IllegalArgumentException")
        void vectorType_틀린값일때_예외_던지기(){
            // given
            UserVector userVector = buildUserVector();

            // when, then
            assertThatThrownBy(()->vectorService.getWeightSum(userVector, "abnormal_type"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 vectorType");
        }
    }

    // ========================== computeVector ========================== //

    @Nested
    @DisplayName("computeVector")
    class ComputeVector {

        @Test
        @DisplayName("totalWeight <= 0 → 빈 벡터 반환")
        void 누적_가중치_0보다_작거나_같을때_빈벡터_반환(){
            // given
            float[] current = new float[1536];
            float[] event = new float[1536];

            // when
            float[] result = vectorService.computeVector(current, 0f, event, 0f);

            // then
            assertThat(result).isNotNull();
            assertThat(result.length).isEqualTo(1536);
            assertThat(result[0]).isEqualTo(0f);
        }

        @Test
        @DisplayName("(weightSum=0) → 이벤트 벡터 그대로 반영")
        void 유저_weight_sum_0일때_이벤트_벡터로_벡터_반영(){
            // given
            float[] current = new float[1536];
            float[] event = new float[1536];
            event[0] = 1.0f;
            event[1] = 0.5f;

            float[] result = vectorService.computeVector(current, 0f, event, 5f);

            assertThat(result[0]).isEqualTo(event[0]);
            assertThat(result[1]).isEqualTo(event[1]);
        }

        @Test
        @DisplayName("가중 평균 공식 수치 검증")
        void 가중평균치_공식_검증(){
            // given
            float[] currnet = new float[1536];
            currnet[0] = 1.0f;
            float[] event = new float[1536];

            // when : weightSum = 5, weight = 5 -> totalWeight = 0
            float[] result = vectorService.computeVector(currnet, 5f, event, -5f);

            // then
            assertThat(result[0]).isEqualTo(0f);
        }

        @Test
        @DisplayName("PURCHASE 후 REFUND → totalWeight=0 → 빈 벡터 반환")
        void 구매_후_환불_시_totalWeight가_0이_될때_빈벡터_반환(){
            // given
            float[] current = new float[1536];
            float[] event = new float[1536];

            // when
            float[] result = vectorService.computeVector(current, 5f, event, -5f);

            // then
            assertThat(result[0]).isEqualTo(0f);
            assertThat(result.length).isEqualTo(1536);
        }
    }

    // ======================== buildUpdatedUserVector ======================== //

    @Nested
    @DisplayName("buildUpdatedUserVector")
    class BuildUpdatedUserVector {

        @Test
        @DisplayName("preference 갱신 시 cart, negative는 그대로")
        void 장기취향벡터_갱신_시_다른_벡터_유지(){
            // given
            float[] updatedVecter = new float[1536];
            updatedVecter[0] = 0.9f;

            UserVector userVector = UserVector.builder()
                .userId("user-1")
                .preferenceVector(new float[1536])
                .preferenceWeightSum(0f)
                .cartVector(new float[]{0.1f})
                .cartWeightSum(3f)
                .negativeVector(new float[]{0.2f})
                .negativeWeightSum(3f)
                .recentVector(new float[1536])
                .recentWeightSum(0f)
                .updatedAt("2026-04-16T00:00:00Z")
                .build();

            // when
            UserVector result = vectorService.buildUpdatedUserVector(userVector, "preference", updatedVecter, 5f);

            // then
            assertThat(result.getPreferenceVector()[0]).isEqualTo(0.9f);
            assertThat(result.getPreferenceWeightSum()).isEqualTo(5f);
            assertThat(result.getCartVector()).isEqualTo(userVector.getCartVector());
            assertThat(result.getCartWeightSum()).isEqualTo(3f);
            assertThat(result.getNegativeVector()).isEqualTo(userVector.getNegativeVector());
            assertThat(result.getNegativeWeightSum()).isEqualTo(3f);
        }

        @Test
        @DisplayName("cart 갱신 시 preference, negative는 그대로")
        void 카드벡터_갱신시_다른_벡터_유지() {
            // given
            float[] updatedVector = new float[1536];
            updatedVector[0] = 0.8f;

            UserVector userVector = UserVector.builder()
                .userId("user-1")
                .preferenceVector(new float[]{0.5f})
                .preferenceWeightSum(5f)
                .cartVector(new float[1536])
                .cartWeightSum(0f)
                .negativeVector(new float[]{0.3f})
                .negativeWeightSum(3f)
                .recentVector(new float[1536])
                .recentWeightSum(0f)
                .updatedAt("2026-04-16T00:00:00Z")
                .build();

            // when
            UserVector result = vectorService.buildUpdatedUserVector(userVector, "cart", updatedVector, 3f);

            // then
            assertThat(result.getCartVector()[0]).isEqualTo(0.8f);
            assertThat(result.getCartWeightSum()).isEqualTo(3f);
            assertThat(result.getPreferenceVector()).isEqualTo(userVector.getPreferenceVector());
            assertThat(result.getPreferenceWeightSum()).isEqualTo(5f);
            assertThat(result.getNegativeVector()).isEqualTo(userVector.getNegativeVector());
        }

        @Test
        @DisplayName("negative 갱신 시 preference, cart는 그대로")
        void 부정신호벡터_갱신시_다른_벡터_유지() {
            // given
            float[] updatedVector = new float[1536];
            updatedVector[0] = 0.7f;

            UserVector userVector = UserVector.builder()
                .userId("user-1")
                .preferenceVector(new float[]{0.5f})
                .preferenceWeightSum(5f)
                .cartVector(new float[]{0.3f})
                .cartWeightSum(3f)
                .negativeVector(new float[1536])
                .negativeWeightSum(0f)
                .recentVector(new float[1536])
                .recentWeightSum(0f)
                .updatedAt("2026-04-16T00:00:00Z")
                .build();

            // when
            UserVector result = vectorService.buildUpdatedUserVector(userVector, "negative", updatedVector, 3f);

            // then
            assertThat(result.getNegativeVector()[0]).isEqualTo(0.7f);
            assertThat(result.getNegativeWeightSum()).isEqualTo(3f);
            assertThat(result.getPreferenceVector()).isEqualTo(userVector.getPreferenceVector());
            assertThat(result.getPreferenceWeightSum()).isEqualTo(5f);
            assertThat(result.getCartVector()).isEqualTo(userVector.getCartVector());
            assertThat(result.getCartWeightSum()).isEqualTo(3f);
        }

    }





    }





