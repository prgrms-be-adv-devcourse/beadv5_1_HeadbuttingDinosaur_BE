package com.devticket.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.devticket.admin.application.service.AdminSellerServiceImpl;
import com.devticket.admin.infrastructure.external.client.MemberInternalClient;
import com.devticket.admin.infrastructure.external.dto.req.InternalDecideSellerApplicationRequest;
import com.devticket.admin.infrastructure.external.dto.res.InternalSellerApplicationResponse;
import com.devticket.admin.presentation.dto.req.AdminDecideSellerApplicationRequest;
import com.devticket.admin.presentation.dto.res.SellerApplicationListResponse;
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
@DisplayName("AdminSellerServiceImpl 단위 테스트")
class AdminSellerServiceImplTest {

    @Mock
    private MemberInternalClient memberInternalClient;

    @InjectMocks
    private AdminSellerServiceImpl adminSellerService;

    @Nested
    @DisplayName("getSellerApplicationList")
    class GetSellerApplicationList {

        @Test
        @DisplayName("내부 클라이언트의 판매자 신청 목록을 매핑해 반환한다")
        void shouldReturnMappedSellerApplications() {
            // given
            InternalSellerApplicationResponse application = new InternalSellerApplicationResponse(
                "app-1", "user-1", "국민은행", "123-456-789", "홍길동", "PENDING", "2026-04-10T10:00:00"
            );
            given(memberInternalClient.getSellerApplications()).willReturn(List.of(application));

            // when
            List<SellerApplicationListResponse> result = adminSellerService.getSellerApplicationList();

            // then
            assertThat(result).hasSize(1);
            SellerApplicationListResponse first = result.get(0);
            assertThat(first.applicationId()).isEqualTo("app-1");
            assertThat(first.userId()).isEqualTo("user-1");
            assertThat(first.bankName()).isEqualTo("국민은행");
            assertThat(first.accountNumber()).isEqualTo("123-456-789");
            assertThat(first.accountHolder()).isEqualTo("홍길동");
            assertThat(first.status()).isEqualTo("PENDING");
            assertThat(first.createdAt()).isEqualTo("2026-04-10T10:00:00");
        }

        @Test
        @DisplayName("신청 내역이 없으면 빈 목록을 반환한다")
        void shouldReturnEmptyListWhenNoApplications() {
            // given
            given(memberInternalClient.getSellerApplications()).willReturn(List.of());

            // when
            List<SellerApplicationListResponse> result = adminSellerService.getSellerApplicationList();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("decideApplication")
    class DecideApplication {

        @Test
        @DisplayName("판매자 신청 결정 요청을 내부 클라이언트로 위임한다 - APPROVE")
        void shouldForwardApproveDecisionToInternalClient() {
            // given
            UUID adminId = UUID.randomUUID();
            UUID applicationId = UUID.randomUUID();
            AdminDecideSellerApplicationRequest request =
                new AdminDecideSellerApplicationRequest("APPROVE");

            // when
            adminSellerService.decideApplication(adminId, applicationId, request);

            // then
            then(memberInternalClient).should()
                .decideSellerApplication(applicationId, new InternalDecideSellerApplicationRequest("APPROVE"));
        }

        @Test
        @DisplayName("판매자 신청 결정 요청을 내부 클라이언트로 위임한다 - REJECT")
        void shouldForwardRejectDecisionToInternalClient() {
            // given
            UUID adminId = UUID.randomUUID();
            UUID applicationId = UUID.randomUUID();
            AdminDecideSellerApplicationRequest request =
                new AdminDecideSellerApplicationRequest("REJECT");

            // when
            adminSellerService.decideApplication(adminId, applicationId, request);

            // then
            then(memberInternalClient).should()
                .decideSellerApplication(applicationId, new InternalDecideSellerApplicationRequest("REJECT"));
        }
    }
}
