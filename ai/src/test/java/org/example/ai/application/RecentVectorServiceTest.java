package org.example.ai.application;



import java.util.List;
import java.util.Optional;
import org.example.ai.application.service.RecentVectorService;
import org.example.ai.domain.model.UserVector;
import org.example.ai.domain.repository.EventEmbeddingRepository;
import org.example.ai.domain.repository.UserVectorRepository;
import org.example.ai.infrastructure.external.client.LogServiceClient;
import org.example.ai.infrastructure.external.dto.res.ActionLogResponse;
import org.example.ai.infrastructure.external.dto.res.ActionLogResponse.ActionLogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;


import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RecentVectorServiceTest {

    @InjectMocks
    private RecentVectorService recentVectorService;

    @Mock
    private UserVectorRepository userVectorRepository;

    @Mock
    private EventEmbeddingRepository eventEmbeddingRepository;

    @Mock
    private LogServiceClient logServiceClient;

    @Mock
    private ActionLogResponse  actionLogResponse;


    @Test
    @DisplayName("logData가 비어 있을 경우, 갱신 x")
    void Log_데이터_비었을_경우_계산_중단(){
        // given
        given(logServiceClient.getRecentActionLog("user-1"))
            .willReturn(null);

        // when
        recentVectorService.recalculateRecentVector("user-1");

        // then
        verify(userVectorRepository, never()).save(any());
    }

    @Test
    @DisplayName("logs empty → 갱신 안 함")
    void logs_empty일때_갱신_안함() {
        // given
        given(logServiceClient.getRecentActionLog("user-1"))
            .willReturn(new ActionLogResponse("user-1", List.of()));

        // when
        recentVectorService.recalculateRecentVector("user-1");

        // then
        verify(userVectorRepository, never()).save(any());
    }

    // ======================== 2. actionType 별 가중치 ======================== //

    @Test
    @DisplayName("VIEW 5회 → 3회까지만 반영, weight 합 = 0.9f")
    void view_5회_중_3회만_반영() {
        // given
        List<ActionLogResponse.ActionLogEntry> logs = List.of(
            new ActionLogResponse.ActionLogEntry("event-1", "VIEW", null),
            new ActionLogResponse.ActionLogEntry("event-1", "VIEW", null),
            new ActionLogResponse.ActionLogEntry("event-1", "VIEW", null),
            new ActionLogResponse.ActionLogEntry("event-1", "VIEW", null),
            new ActionLogResponse.ActionLogEntry("event-1", "VIEW", null)
        );

        given(logServiceClient.getRecentActionLog("user-1"))
            .willReturn(new ActionLogResponse("user-1", logs));

        float[] embedding = new float[1536];
        embedding[0] = 1.0f;
        given(eventEmbeddingRepository.findEmbeddingById("event-1"))
            .willReturn(Optional.of(embedding));
        given(userVectorRepository.findById("user-1")).willReturn(Optional.empty());
        given(userVectorRepository.save(any())).willAnswer(i -> i.getArgument(0));

        // when
        recentVectorService.recalculateRecentVector("user-1");

        // then
        ArgumentCaptor<UserVector> captor = ArgumentCaptor.forClass(UserVector.class);
        verify(userVectorRepository).save(captor.capture());

        // VIEW 3회 * 0.3f = 0.9f, recentVector[0] = embedding[0] * 0.9 / 0.9 = 1.0f
        assertThat(captor.getValue().getRecentWeightSum()).isCloseTo(0.9f, within(0.0001f));
        assertThat(captor.getValue().getRecentVector()[0]).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("DETAIL_VIEW 5회 → 3회까지만 반영, weight 합 = 3.0f")
    void detailView_5회_중_3회만_반영() {
        // given
        List<ActionLogResponse.ActionLogEntry> logs = List.of(
            new ActionLogResponse.ActionLogEntry("event-1", "DETAIL_VIEW", null),
            new ActionLogResponse.ActionLogEntry("event-1", "DETAIL_VIEW", null),
            new ActionLogResponse.ActionLogEntry("event-1", "DETAIL_VIEW", null),
            new ActionLogResponse.ActionLogEntry("event-1", "DETAIL_VIEW", null),
            new ActionLogResponse.ActionLogEntry("event-1", "DETAIL_VIEW", null)
        );

        given(logServiceClient.getRecentActionLog("user-1"))
            .willReturn(new ActionLogResponse("user-1", logs));

        float[] embedding = new float[1536];
        embedding[0] = 1.0f;
        given(eventEmbeddingRepository.findEmbeddingById("event-1"))
            .willReturn(Optional.of(embedding));
        given(userVectorRepository.findById("user-1")).willReturn(Optional.empty());
        given(userVectorRepository.save(any())).willAnswer(i -> i.getArgument(0));

        // when
        recentVectorService.recalculateRecentVector("user-1");

        // then
        ArgumentCaptor<UserVector> captor = ArgumentCaptor.forClass(UserVector.class);
        verify(userVectorRepository).save(captor.capture());

        // DETAIL_VIEW 3회 * 1.0f = 3.0f
        assertThat(captor.getValue().getRecentWeightSum()).isEqualTo(3.0f);
        assertThat(captor.getValue().getRecentVector()[0]).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("DWELL_TIME 100초 → weight = 2.0f")
    void dwellTime_100초_weight_2f() {
        // given
        List<ActionLogResponse.ActionLogEntry> logs = List.of(
            new ActionLogResponse.ActionLogEntry("event-1", "DWELL_TIME", 100)
        );

        given(logServiceClient.getRecentActionLog("user-1"))
            .willReturn(new ActionLogResponse("user-1", logs));

        float[] embedding = new float[1536];
        embedding[0] = 1.0f;
        given(eventEmbeddingRepository.findEmbeddingById("event-1"))
            .willReturn(Optional.of(embedding));
        given(userVectorRepository.findById("user-1")).willReturn(Optional.empty());
        given(userVectorRepository.save(any())).willAnswer(i -> i.getArgument(0));

        // when
        recentVectorService.recalculateRecentVector("user-1");

        // then
        ArgumentCaptor<UserVector> captor = ArgumentCaptor.forClass(UserVector.class);
        verify(userVectorRepository).save(captor.capture());


        assertThat(captor.getValue().getRecentWeightSum()).isEqualTo(2.0f);
    }

    @Test
    @DisplayName("DWELL_TIME 3초 → 5초 미만 → 무시 → 갱신 안 함")
    void dwellTime_3초_5초미만_무시() {
        // given
        List<ActionLogResponse.ActionLogEntry> logs = List.of(
            new ActionLogResponse.ActionLogEntry("event-1", "DWELL_TIME", 3)
        );

        given(logServiceClient.getRecentActionLog("user-1"))
            .willReturn(new ActionLogResponse("user-1", logs));

        // when
        recentVectorService.recalculateRecentVector("user-1");

        // then
        verify(userVectorRepository, never()).save(any());
    }

    // ======================== 3. eventWeightMap empty ======================== //

    @Test
    @DisplayName("유효한 로그 없음 → eventWeightMap empty → 갱신 안 함")
    void 유효한_로그_없을때_갱신_안함() {
        // given - DWELL_TIME 3초 로그만 있어서 모두 필터링됨
        List<ActionLogResponse.ActionLogEntry> logs = List.of(
            new ActionLogResponse.ActionLogEntry("event-1", "DWELL_TIME", 3),
            new ActionLogResponse.ActionLogEntry("event-2", "DWELL_TIME", 1)
        );

        given(logServiceClient.getRecentActionLog("user-1"))
            .willReturn(new ActionLogResponse("user-1", logs));

        // when
        recentVectorService.recalculateRecentVector("user-1");

        // then
        verify(userVectorRepository, never()).save(any());
    }

    // ======================== 4. embedding null ======================== //

    @Test
    @DisplayName("embedding null → 해당 이벤트 건너뜀 → totalWeight=0 → 갱신 안 함")
    void embedding_null일때_갱신_안함() {
        // given
        List<ActionLogResponse.ActionLogEntry> logs = List.of(
            new ActionLogResponse.ActionLogEntry("event-1", "VIEW", null)
        );

        given(logServiceClient.getRecentActionLog("user-1"))
            .willReturn(new ActionLogResponse("user-1", logs));
        given(eventEmbeddingRepository.findEmbeddingById("event-1"))
            .willReturn(Optional.empty());

        // when
        recentVectorService.recalculateRecentVector("user-1");

        // then
        verify(userVectorRepository, never()).save(any());
    }

    // ======================== 5. 정상 흐름 ======================== //

    @Test
    @DisplayName("정상 흐름 → recentVector 갱신, 다른 벡터는 그대로")
    void 정상흐름_recentVector_갱신_다른벡터_유지() {
        // given
        List<ActionLogEntry> logs = List.of(
            new ActionLogResponse.ActionLogEntry("event-1", "DETAIL_VIEW", null)
        );

        given(logServiceClient.getRecentActionLog("user-1"))
            .willReturn(new ActionLogResponse("user-1", logs));

        float[] embedding = new float[1536];
        embedding[0] = 0.8f;
        given(eventEmbeddingRepository.findEmbeddingById("event-1"))
            .willReturn(Optional.of(embedding));

        float[] originalPreference = new float[1536];
        originalPreference[0] = 0.5f;

        UserVector existingUser = UserVector.builder()
            .userId("user-1")
            .preferenceVector(originalPreference)
            .preferenceWeightSum(5f)
            .recentVector(new float[1536])
            .recentWeightSum(0f)
            .cartVector(new float[1536])
            .cartWeightSum(0f)
            .negativeVector(new float[1536])
            .negativeWeightSum(0f)
            .updatedAt("2026-04-16T00:00:00Z")
            .build();

        given(userVectorRepository.findById("user-1")).willReturn(Optional.of(existingUser));
        given(userVectorRepository.save(any())).willAnswer(i -> i.getArgument(0));

        // when
        recentVectorService.recalculateRecentVector("user-1");

        // then
        ArgumentCaptor<UserVector> captor = ArgumentCaptor.forClass(UserVector.class);
        verify(userVectorRepository).save(captor.capture());

        UserVector saved = captor.getValue();

        // recentVector 갱신 확인
        assertThat(saved.getRecentVector()[0]).isEqualTo(0.8f);
        assertThat(saved.getRecentWeightSum()).isEqualTo(1.0f);

        // 다른 벡터는 그대로인지 확인
        assertThat(saved.getPreferenceVector()).isEqualTo(originalPreference);
        assertThat(saved.getPreferenceWeightSum()).isEqualTo(5f);
    }
}



