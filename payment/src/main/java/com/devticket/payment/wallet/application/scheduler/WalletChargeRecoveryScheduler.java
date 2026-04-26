package com.devticket.payment.wallet.application.scheduler;

import com.devticket.payment.wallet.application.service.WalletService;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PENDING 상태로 장시간 체류 중인 WalletCharge를 주기적으로 감지하여
 * Toss 결제 상태를 조회한 뒤 COMPLETED / FAILED 로 보정하는 사후 보정 스케줄러.
 *
 * 대상: createdAt 기준 30분 초과 ~ 24시간 이내인 PENDING 건
 * 원인: 브라우저 종료, 타임아웃, 프론트엔드 런타임 오류 등
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletChargeRecoveryScheduler {

    private static final int STALE_THRESHOLD_MINUTES = 30;  // 이 시간 이상 PENDING이면 보정 대상
    private static final int MAX_RETENTION_HOURS = 24;      // 이 시간 초과 건은 대상에서 제외(별도 처리)
    private static final int BATCH_SIZE = 100;              // 1회 처리 최대 건수

    private final WalletService walletService;
    private final WalletChargeRepository walletChargeRepository;

    /**
     * 10분마다 실행
     * initialDelay: 서버 기동 직후 즉시 실행 방지.
     */
    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void recoverStalePendingCharges() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        LocalDateTime after = LocalDateTime.now().minusHours(MAX_RETENTION_HOURS);

        // PENDING 상태인 chargeId 목록 반환
        List<UUID> staleIds = walletChargeRepository.findStalePendingChargeIds(before, after, BATCH_SIZE);
        if (staleIds.isEmpty()) {
            return;
        }

        log.info("[Recovery] 사후 보정 시작 — 대상 {} 건", staleIds.size());
        int success = 0, skip = 0, fail = 0;

        for (UUID chargeId : staleIds) {
            try {
                walletService.recoverStalePendingCharge(chargeId);
                success++;
            } catch (Exception e) {
                // 개별 건 실패는 전체를 멈추지 않음
                log.error("[Recovery] 처리 중 오류 — chargeId={}, error={}", chargeId, e.getMessage());
                fail++;
            }
        }

        log.info("[Recovery] 사후 보정 완료 — 성공={}, 스킵={}, 실패={}", success, skip, fail);
    }
}