package com.devticket.settlement.presentation.scheduler;

import com.devticket.settlement.application.service.SettlementInternalService;
import com.devticket.settlement.application.service.SettlementService;
import com.devticket.settlement.presentation.dto.SettlementTargetPreviewResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementService settlementService;
    private final SettlementInternalService settlementInternalService;

    // 매일 10:00:00 실행 (기존 00:00:01에서 변경)
    @Scheduled(cron = "0 0 10 * * *")
    public void collectDailySettlementTargets() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("[SettlementScheduler] 정산대상 데이터 수집 시작 - targetDate: {}", yesterday);
        try {
            SettlementTargetPreviewResponse result = settlementService.collectSettlementTargets(yesterday);
            log.info("[SettlementScheduler] 정산대상 데이터 수집 완료 - 이벤트: {}건, 저장: {}건, 스킵: {}건",
                result.totalEventCount(), result.savedCount(), result.skippedCount());
        } catch (Exception e) {
            log.error("[SettlementScheduler] 정산대상 데이터 수집 실패 - targetDate: {}", yesterday, e);
        }
    }

    // 매월 1일 10:10:00 실행 (기존 00:10:00에서 변경)
    @Scheduled(cron = "0 10 10 1 * *")
    public void createMonthlySettlement() {
        log.info("[SettlementScheduler] 월 정산 생성 시작");
        try {
            settlementInternalService.createSettlementFromItems();
            log.info("[SettlementScheduler] 월 정산 생성 완료");
        } catch (Exception e) {
            log.error("[SettlementScheduler] 월 정산 생성 실패", e);
        }
    }

}