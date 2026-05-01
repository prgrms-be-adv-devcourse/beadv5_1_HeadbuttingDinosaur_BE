package com.devticket.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.devticket.admin.application.service.AdminUserServiceImpl;
import com.devticket.admin.domain.model.AdminActionHistory;
import com.devticket.admin.domain.model.AdminActionType;
import com.devticket.admin.domain.model.AdminTargetType;
import com.devticket.admin.domain.repository.AdminActionRepository;
import com.devticket.admin.infrastructure.external.client.MemberInternalClient;
import com.devticket.admin.infrastructure.external.dto.res.InternalMemberInfoResponse;
import com.devticket.admin.infrastructure.persistence.repository.AdminActionHistoryRepositoryImpl;
import com.devticket.admin.presentation.dto.req.UserRoleRequest;
import com.devticket.admin.presentation.dto.req.UserSearchCondition;
import com.devticket.admin.presentation.dto.req.UserStatusRequest;
import com.devticket.admin.presentation.dto.res.AdminUserListResponse;
import com.devticket.admin.presentation.dto.res.InternalMemberDetailResponse;
import com.devticket.admin.presentation.dto.res.InternalMemberPageResponse;
import com.devticket.admin.presentation.dto.res.UserDetailResponse;
import com.devticket.admin.presentation.dto.res.UserListItem;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserServiceImpl 단위 테스트")
class AdminUserServiceImplTest {

    @Mock
    private MemberInternalClient memberInternalClient;

    @Mock
    private AdminActionHistoryRepositoryImpl adminActionHistoryRepository;

    @Mock
    private AdminActionRepository adminActionRepository;

    private AdminUserServiceImpl adminUserService;

    @BeforeEach
    void setUp() {
        // AdminActionHistoryRepositoryImpl 이 AdminActionRepository 를 구현하기 때문에
        // @InjectMocks 만으로는 두 의존성을 정확히 구분해서 주입하지 못한다.
        // 생성자 인자 순서대로 명시적으로 주입한다.
        adminUserService = new AdminUserServiceImpl(
            memberInternalClient,
            adminActionHistoryRepository,
            adminActionRepository
        );
    }

    @Nested
    @DisplayName("getMembers")
    class GetMembers {

        @Test
        @DisplayName("내부 회원 페이지 응답을 어드민 회원 목록으로 매핑한다")
        void shouldReturnMappedMemberList() {
            // given
            UserSearchCondition condition =
                new UserSearchCondition("USER", "ACTIVE", "kim", 0, 50);
            InternalMemberInfoResponse member = new InternalMemberInfoResponse(
                "user-1", "kim@test.com", "kimkim",
                "USER", "ACTIVE", "LOCAL",
                "2026-01-01T00:00:00", null
            );
            InternalMemberPageResponse page =
                new InternalMemberPageResponse(List.of(member), 0, 50, 1L, 1);
            given(memberInternalClient.getMembers(condition)).willReturn(page);

            // when
            AdminUserListResponse response = adminUserService.getMembers(condition);

            // then
            assertThat(response.page()).isEqualTo(0);
            assertThat(response.size()).isEqualTo(50);
            assertThat(response.totalElements()).isEqualTo(1L);
            assertThat(response.totalPages()).isEqualTo(1);
            assertThat(response.content()).hasSize(1);

            UserListItem item = response.content().get(0);
            assertThat(item.userId()).isEqualTo("user-1");
            assertThat(item.email()).isEqualTo("kim@test.com");
            assertThat(item.nickname()).isEqualTo("kimkim");
            assertThat(item.role()).isEqualTo("USER");
            assertThat(item.status()).isEqualTo("ACTIVE");
            assertThat(item.providerType()).isEqualTo("LOCAL");
            assertThat(item.createdAt()).isEqualTo("2026-01-01T00:00:00");
            assertThat(item.withdrawnAt()).isNull();
        }

        @Test
        @DisplayName("내부 클라이언트가 빈 페이지를 반환하면 빈 목록을 반환한다")
        void shouldReturnEmptyListWhenNoMembers() {
            // given
            UserSearchCondition condition =
                new UserSearchCondition(null, null, null, 0, 50);
            given(memberInternalClient.getMembers(condition))
                .willReturn(new InternalMemberPageResponse(List.of(), 0, 50, 0L, 0));

            // when
            AdminUserListResponse response = adminUserService.getMembers(condition);

            // then
            assertThat(response.content()).isEmpty();
            assertThat(response.totalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("getUserDetail")
    class GetUserDetail {

        @Test
        @DisplayName("회원 정보와 어드민 액션 이력을 합쳐 응답한다")
        void shouldReturnUserDetailWithHistory() {
            // given
            UUID userId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            InternalMemberDetailResponse memberDetail = new InternalMemberDetailResponse(
                userId.toString(), "lee@test.com", "lee",
                "USER", "ACTIVE", "LOCAL",
                "2026-01-01T00:00:00", null
            );
            given(memberInternalClient.getMember(userId)).willReturn(memberDetail);

            AdminActionHistory history = AdminActionHistory.builder()
                .adminId(adminId)
                .targetType(AdminTargetType.USER)
                .targetId(userId)
                .actionType(AdminActionType.SUSPENDED_USER)
                .build();
            given(adminActionRepository.findByTarget(AdminTargetType.USER, userId))
                .willReturn(List.of(history));

            // when
            UserDetailResponse response = adminUserService.getUserDetail(userId);

            // then
            assertThat(response.userId()).isEqualTo(userId.toString());
            assertThat(response.email()).isEqualTo("lee@test.com");
            assertThat(response.nickname()).isEqualTo("lee");
            assertThat(response.role()).isEqualTo("USER");
            assertThat(response.status()).isEqualTo("ACTIVE");
            assertThat(response.providerType()).isEqualTo("LOCAL");

            assertThat(response.penaltyHistory()).hasSize(1);
            assertThat(response.penaltyHistory().get(0).actionType())
                .isEqualTo("SUSPENDED_USER");
            assertThat(response.penaltyHistory().get(0).adminId()).isEqualTo(adminId);
        }

        @Test
        @DisplayName("어드민 액션 이력이 없으면 빈 리스트를 반환한다")
        void shouldReturnEmptyHistoryWhenNoActions() {
            // given - findByTarget 는 Mockito 기본 동작인 빈 리스트 반환에 의존한다
            UUID userId = UUID.randomUUID();
            InternalMemberDetailResponse memberDetail = new InternalMemberDetailResponse(
                userId.toString(), "park@test.com", "park",
                "USER", "ACTIVE", "LOCAL",
                "2026-01-01T00:00:00", null
            );
            given(memberInternalClient.getMember(userId)).willReturn(memberDetail);

            // when
            UserDetailResponse response = adminUserService.getUserDetail(userId);

            // then
            assertThat(response.penaltyHistory()).isEmpty();
            then(adminActionRepository).should().findByTarget(AdminTargetType.USER, userId);
        }
    }

    @Nested
    @DisplayName("penalizeUser")
    class PenalizeUser {

        @Test
        @DisplayName("상태가 SUSPENDED 면 회원 상태를 변경하고 SUSPENDED_USER 이력을 저장한다")
        void shouldSuspendUserAndSaveHistory() {
            // given
            UUID adminId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UserStatusRequest request = new UserStatusRequest("SUSPENDED");

            // when
            adminUserService.penalizeUser(adminId, userId, request);

            // then
            then(memberInternalClient).should().updateUserStatus(userId, request);

            ArgumentCaptor<AdminActionHistory> captor = ArgumentCaptor.forClass(AdminActionHistory.class);
            then(adminActionHistoryRepository).should().save(captor.capture());

            AdminActionHistory saved = captor.getValue();
            assertThat(saved.getAdminId()).isEqualTo(adminId);
            assertThat(saved.getTargetType()).isEqualTo(AdminTargetType.USER);
            assertThat(saved.getTargetId()).isEqualTo(userId);
            assertThat(saved.getActionType()).isEqualTo(AdminActionType.SUSPENDED_USER);
        }

        @Test
        @DisplayName("상태가 WITHDRAWN 면 WITHDRAW_USER 이력을 저장한다")
        void shouldWithdrawUserAndSaveHistory() {
            // given
            UUID adminId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UserStatusRequest request = new UserStatusRequest("WITHDRAWN");

            // when
            adminUserService.penalizeUser(adminId, userId, request);

            // then
            then(memberInternalClient).should().updateUserStatus(userId, request);

            ArgumentCaptor<AdminActionHistory> captor = ArgumentCaptor.forClass(AdminActionHistory.class);
            then(adminActionHistoryRepository).should().save(captor.capture());
            assertThat(captor.getValue().getActionType())
                .isEqualTo(AdminActionType.WITHDRAW_USER);
        }

        @Test
        @DisplayName("상태가 ACTIVE 면 회원 상태만 변경하고 이력은 저장하지 않는다")
        void shouldNotSaveHistoryWhenActive() {
            // given
            UUID adminId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UserStatusRequest request = new UserStatusRequest("ACTIVE");

            // when
            adminUserService.penalizeUser(adminId, userId, request);

            // then
            then(memberInternalClient).should().updateUserStatus(userId, request);
            then(adminActionHistoryRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateUserRole")
    class UpdateUserRole {

        @Test
        @DisplayName("회원 권한을 변경하고 CHANGE_ROLE 이력을 저장한다")
        void shouldUpdateRoleAndSaveHistory() {
            // given
            UUID adminId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UserRoleRequest request = new UserRoleRequest("SELLER");

            // when
            adminUserService.updateUserRole(adminId, userId, request);

            // then
            then(memberInternalClient).should().updateUserRole(userId, request);

            ArgumentCaptor<AdminActionHistory> captor = ArgumentCaptor.forClass(AdminActionHistory.class);
            then(adminActionHistoryRepository).should().save(captor.capture());

            AdminActionHistory saved = captor.getValue();
            assertThat(saved.getAdminId()).isEqualTo(adminId);
            assertThat(saved.getTargetType()).isEqualTo(AdminTargetType.USER);
            assertThat(saved.getTargetId()).isEqualTo(userId);
            assertThat(saved.getActionType()).isEqualTo(AdminActionType.CHANGE_ROLE);
        }
    }
}
