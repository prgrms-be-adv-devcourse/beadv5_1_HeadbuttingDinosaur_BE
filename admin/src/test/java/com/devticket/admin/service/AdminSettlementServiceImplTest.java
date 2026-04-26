package com.devticket.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.devticket.admin.application.service.AdminSettlementServiceImpl;
import com.devticket.admin.domain.model.AdminActionHistory;
import com.devticket.admin.domain.model.AdminActionType;
import com.devticket.admin.domain.model.AdminTargetType;
import com.devticket.admin.domain.repository.AdminActionRepository;
import com.devticket.admin.infrastructure.external.client.SettlementInternalClient;
import com.devticket.admin.infrastructure.external.dto.res.InternalSettlementPageResponse;
import com.devticket.admin.infrastructure.external.dto.res.InternalSettlementResponse;
import com.devticket.admin.presentation.dto.req.AdminSettlementSearchRequest;
import com.devticket.admin.presentation.dto.res.AdminSettelmentListResponse;
import com.devticket.admin.presentation.dto.res.SettlementResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminSettlementServiceImpl 단위 테스트")
class AdminSettlementServiceImplTest {

    @Mock
    private SettlementInternalClient settlementInternalClient;

    @Mock
    private AdminActionRepository adminActionRepository;

    @InjectMocks
    private AdminSettlementServiceImpl adminSettlementService;

    @Nested
    @DisplayName("getSettlementList")
    class GetSettlementList {

        @Test
        @DisplayName("내부 클라이언트 응답을 매핑해 정산 목록을 반환한다")
        void shouldReturnMappedSettlementList() {
            // given
            AdminSettlementSearchRequest condition =
                new AdminSettlementSearchRequest("COMPLETED", null, null, null, 0, 20);

            LocalDateTime now = LocalDateTime.of(2026, 4, 1, 12, 0, 0);
            InternalSettlementResponse internal = new InternalSettlementResponse(
                1L, now.minusDays(7), now,
                10_000L, 1_000L, 500L, 8_500L, "COMPLETED", now
            );
            InternalSettlementPageResponse page = new InternalSettlementPageResponse(
                List.of(internal), 0, 20, 1L, 1
            );
            given(settlementInternalClient.getSettlements(condition)).willReturn(page);

            // when
            AdminSettelmentListResponse response = adminSettlementService.getSettlementList(condition);

            // then
            assertThat(response.page()).isEqualTo(0);
            assertThat(response.size()).isEqualTo(20);
            assertThat(response.totalElements()).isEqualTo(1L);
            assertThat(response.totalPages()).isEqualTo(1);
            assertThat(response.content()).hasSize(1);

            SettlementResponse first = response.content().get(0);
            assertThat(first.settlementId()).isEqualTo(1L);
            assertThat(first.periodStart()).isEqualTo(now.minusDays(7));
            assertThat(first.periodEnd()).isEqualTo(now);
            assertThat(first.totalSalesAmount()).isEqualTo(10_000L);
            assertThat(first.totalRefundAmount()).isEqualTo(1_000L);
            assertThat(first.totalFeeAmount()).isEqualTo(500L);
            assertThat(first.finalSettlementAmount()).isEqualTo(8_500L);
            assertThat(first.status()).isEqualTo("COMPLETED");
            assertThat(first.settledAt()).isEqualTo(now);

            then(settlementInternalClient).should().getSettlements(condition);
        }

        @Test
        @DisplayName("내부 클라이언트가 빈 페이지를 반환하면 빈 목록을 그대로 응답한다")
        void shouldReturnEmptyListWhenNoSettlements() {
            // given
            AdminSettlementSearchRequest condition =
                new AdminSettlementSearchRequest(null, null, null, null, 0, 20);
            given(settlementInternalClient.getSettlements(condition))
                .willReturn(new InternalSettlementPageResponse(List.of(), 0, 20, 0L, 0));

            // when
            AdminSettelmentListResponse response = adminSettlementService.getSettlementList(condition);

            // then
            assertThat(response.content()).isEmpty();
            assertThat(response.totalElements()).isZero();
            assertThat(response.totalPages()).isZero();
        }
    }

    @Nested
    @DisplayName("runSettlement")
    class RunSettlement {

        @Test
        @DisplayName("정산 실행 후 RUN_SETTLEMENT 액션 이력을 저장한다")
        void shouldRunSettlementAndSaveHistory() {
            // given
            UUID adminId = UUID.randomUUID();

            // when
            adminSettlementService.runSettlement(adminId);

            // then
            then(settlementInternalClient).should().runSettlement();

            ArgumentCaptor<AdminActionHistory> captor = ArgumentCaptor.forClass(AdminActionHistory.class);
            then(adminActionRepository).should().save(captor.capture());

            AdminActionHistory saved = captor.getValue();
            assertThat(saved.getAdminId()).isEqualTo(adminId);
            assertThat(saved.getTargetType()).isEqualTo(AdminTargetType.SETTLEMENT);
            assertThat(saved.getTargetId()).isNull();
            assertThat(saved.getActionType()).isEqualTo(AdminActionType.RUN_SETTLEMENT);
        }
    }
}
