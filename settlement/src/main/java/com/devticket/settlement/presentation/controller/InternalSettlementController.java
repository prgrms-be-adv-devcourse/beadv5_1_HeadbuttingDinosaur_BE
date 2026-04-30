package com.devticket.settlement.presentation.controller;

import com.devticket.settlement.application.service.SettlementInternalService;
import com.devticket.settlement.infrastructure.external.dto.AdminSettlementDetailResponse;
import com.devticket.settlement.infrastructure.external.dto.InternalSettlementPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Settlement", description = "정산 관리자 API")
@Slf4j
@RestController
@RequestMapping("/api/admin/settlements")
public class InternalSettlementController {

    private final SettlementInternalService settlementInternalService;
    private final JobOperator jobOperator;
    private final Job dailySettlementJob;
    private final Job monthlySettlementJob;

    public InternalSettlementController(
        SettlementInternalService settlementInternalService,
        JobOperator jobOperator,
        @Qualifier("dailySettlementJob") Job dailySettlementJob,
        @Qualifier("monthlySettlementJob") Job monthlySettlementJob
    ) {
        this.settlementInternalService = settlementInternalService;
        this.jobOperator = jobOperator;
        this.dailySettlementJob = dailySettlementJob;
        this.monthlySettlementJob = monthlySettlementJob;
    }

    // ────────────────────────────────────────────────
    // 정산서 조회
    // ────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<InternalSettlementPageResponse> getSettlements(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID sellerId,
        @RequestParam(required = false) String yearMonth,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
            settlementInternalService.getSettlements(status, sellerId, yearMonth, pageable)
        );
    }

    @GetMapping("/{settlementId}")
    public ResponseEntity<AdminSettlementDetailResponse> getSettlementDetail(
        @PathVariable UUID settlementId
    ) {
        return ResponseEntity.ok(settlementInternalService.getSettlementDetail(settlementId));
    }

    // ────────────────────────────────────────────────
    // 정산서 관리
    // ────────────────────────────────────────────────

    @PostMapping("/{settlementId}/cancel")
    public ResponseEntity<Void> cancelSettlement(@PathVariable UUID settlementId) {
        settlementInternalService.cancelSettlement(settlementId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{settlementId}/payment")
    public ResponseEntity<Void> processPayment(@PathVariable UUID settlementId) {
        settlementInternalService.processPayment(settlementId);
        return ResponseEntity.ok().build();
    }

    // ────────────────────────────────────────────────
    // 배치 수동 실행
    // ────────────────────────────────────────────────

    @Operation(
        summary = "일별 정산대상 수집 배치 수동 실행",
        description = "date 미입력 시 어제 날짜로 실행합니다. 예: 2026-04-29"
    )
    @PostMapping("/batch/daily")
    public ResponseEntity<String> launchDailyJob(
        @RequestParam(required = false) LocalDate date
    ) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now().minusDays(1);
        log.info("[InternalSettlementController] 일별 정산대상 수집 배치 수동 실행 - targetDate: {}", targetDate);
        try {
            JobParameters params = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters();
            jobOperator.start(dailySettlementJob, params);
            return ResponseEntity.ok("일별 정산대상 수집 배치 실행 완료 - targetDate: " + targetDate);
        } catch (Exception e) {
            log.error("[InternalSettlementController] 일별 정산대상 수집 배치 실행 실패", e);
            return ResponseEntity.internalServerError().body("배치 실행 실패: " + e.getMessage());
        }
    }

    @Operation(
        summary = "월별 정산서 생성 배치 수동 실행",
        description = "yearMonth 미입력 시 전월로 실행합니다. 예: 2026-04"
    )
    @PostMapping("/batch/monthly")
    public ResponseEntity<String> launchMonthlyJob(
        @RequestParam(required = false) String yearMonth
    ) {
        String targetYearMonth;
        try {
            targetYearMonth = (yearMonth != null)
                ? YearMonth.parse(yearMonth).toString()
                : YearMonth.now().minusMonths(1).toString();
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("잘못된 yearMonth 형식입니다. 예: 2026-04");
        }

        log.info("[InternalSettlementController] 월 정산 배치 수동 실행 - yearMonth: {}", targetYearMonth);
        try {
            JobParameters params = new JobParametersBuilder()
                .addString("yearMonth", targetYearMonth)
                .toJobParameters();
            jobOperator.start(monthlySettlementJob, params);
            return ResponseEntity.ok("월 정산 배치 실행 완료 - yearMonth: " + targetYearMonth);
        } catch (Exception e) {
            log.error("[InternalSettlementController] 월 정산 배치 실행 실패", e);
            return ResponseEntity.internalServerError().body("배치 실행 실패: " + e.getMessage());
        }
    }
}