package com.devticket.payment.application.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.devticket.payment.wallet.application.scheduler.WalletChargeRecoveryScheduler;
import com.devticket.payment.wallet.application.service.WalletService;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("사후 보정 스케줄러 (WalletChargeRecoveryScheduler)")
class WalletChargeRecoverySchedulerTest {

    @Mock private WalletService walletService;
    @Mock private WalletChargeRepository walletChargeRepository;

    @InjectMocks
    private WalletChargeRecoveryScheduler scheduler;

    // =====================================================================
    // 대상 조회
    // =====================================================================

    @Nested
    @DisplayName("대상 조회")
    class FindStaleCharges {

        @Test
        void 보정_대상_없으면_서비스_호출_없음() {
            // given
            given(walletChargeRepository.findStalePendingChargeIds(any(), any(), anyInt()))
                .willReturn(List.of());

            // when
            scheduler.recoverStalePendingCharges();

            // then
            then(walletService).should(never()).recoverStalePendingCharge(any());
        }

        @Test
        void 보정_대상_있으면_각_건별_서비스_호출() {
            // given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();
            given(walletChargeRepository.findStalePendingChargeIds(any(), any(), anyInt()))
                .willReturn(List.of(id1, id2, id3));

            // when
            scheduler.recoverStalePendingCharges();

            // then: 각 chargeId마다 정확히 1회씩 호출
            then(walletService).should(times(1)).recoverStalePendingCharge(id1);
            then(walletService).should(times(1)).recoverStalePendingCharge(id2);
            then(walletService).should(times(1)).recoverStalePendingCharge(id3);
        }
    }

    // =====================================================================
    // 오류 격리
    // =====================================================================

    @Nested
    @DisplayName("오류 격리")
    class ErrorIsolation {

        @Test
        void 특정_건_실패해도_나머지_건_계속_처리() {
            // given: id2 처리 중 예외 발생
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();
            given(walletChargeRepository.findStalePendingChargeIds(any(), any(), anyInt()))
                .willReturn(List.of(id1, id2, id3));
            willThrow(new RuntimeException("PG 오류"))
                .given(walletService).recoverStalePendingCharge(id2);

            // when: 예외가 스케줄러 밖으로 전파되지 않아야 함
            scheduler.recoverStalePendingCharges();

            // then: id1, id2, id3 모두 시도됨 (id2는 실패했지만 id3는 계속 처리)
            then(walletService).should(times(1)).recoverStalePendingCharge(id1);
            then(walletService).should(times(1)).recoverStalePendingCharge(id2);
            then(walletService).should(times(1)).recoverStalePendingCharge(id3);
        }

        @Test
        void 모든_건_실패해도_스케줄러_예외_미전파() {
            // given: 전체 실패 케이스
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            given(walletChargeRepository.findStalePendingChargeIds(any(), any(), anyInt()))
                .willReturn(List.of(id1, id2));
            willThrow(new RuntimeException("전체 오류"))
                .given(walletService).recoverStalePendingCharge(any());

            // when & then: 스케줄러 자체는 예외 없이 정상 종료
            org.assertj.core.api.Assertions.assertThatCode(scheduler::recoverStalePendingCharges)
                .doesNotThrowAnyException();
        }
    }

    // =====================================================================
    // 배치 크기 제한
    // =====================================================================

    @Nested
    @DisplayName("배치 크기 제한")
    class BatchLimit {

        @Test
        void DB_조회시_배치_크기_100_전달() {
            // given
            given(walletChargeRepository.findStalePendingChargeIds(any(), any(), anyInt()))
                .willReturn(List.of());

            // when
            scheduler.recoverStalePendingCharges();

            // then: limit=100 으로 조회
            then(walletChargeRepository).should(times(1))
                .findStalePendingChargeIds(any(), any(), org.mockito.ArgumentMatchers.eq(100));
        }
    }
}