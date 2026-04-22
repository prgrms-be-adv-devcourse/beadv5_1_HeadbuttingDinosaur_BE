package com.devticket.admin.application.service;

import com.devticket.admin.domain.model.AdminActionHistory;
import com.devticket.admin.domain.model.AdminActionType;
import com.devticket.admin.domain.model.AdminTargetType;
import com.devticket.admin.domain.model.temporaryEnum.UserStatus;
import com.devticket.admin.domain.repository.AdminActionRepository;
import com.devticket.admin.infrastructure.external.client.MemberInternalClient;
import com.devticket.admin.infrastructure.persistence.repository.AdminActionHistoryRepositoryImpl;
import com.devticket.admin.presentation.dto.req.UserRoleRequest;
import com.devticket.admin.presentation.dto.req.UserSearchCondition;
import com.devticket.admin.presentation.dto.req.UserStatusRequest;
import com.devticket.admin.presentation.dto.res.AdminActionHistorySummary;
import com.devticket.admin.presentation.dto.res.AdminUserListResponse;
import com.devticket.admin.presentation.dto.res.InternalMemberDetailResponse;
import com.devticket.admin.presentation.dto.res.InternalMemberPageResponse;
import com.devticket.admin.presentation.dto.res.UserDetailResponse;
import com.devticket.admin.presentation.dto.res.UserListItem;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {

    private final MemberInternalClient memberInternalClient;
    private final AdminActionHistoryRepositoryImpl adminActionHistoryRepository;
    private final AdminActionRepository adminActionRepository;


    @Override
    public AdminUserListResponse getMembers(UserSearchCondition condition) {
        InternalMemberPageResponse page = memberInternalClient.getMembers(condition);

        List<UserListItem> content = page.content().stream()
            .map(member -> new UserListItem(
                member.userId(),
                member.email(),
                member.nickname(),
                member.role(),
                member.status(),
                member.providerType(),
                member.createdAt(),
                member.withdrawnAt()
            ))
            .toList();

        return new AdminUserListResponse(
            content,
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages()
        );
    }

    @Override
    public UserDetailResponse getUserDetail(UUID userId) {
        InternalMemberDetailResponse member = memberInternalClient.getMember(userId);
        // 존재하지 않으면 MemberInternalClient에서 4xx 터뜨리고 전역 핸들러가 MEMBER_009로 변환

        List<AdminActionHistorySummary> history =
            adminActionRepository.findByTarget(AdminTargetType.USER, userId).stream()
                .map(h -> new AdminActionHistorySummary(
                    h.getActionType().name(),
                    h.getAdminId(),
                    h.getCreatedAt()))
                .toList();

        return new UserDetailResponse(
            member.id(),
            member.email(),
            member.nickname(),
            member.role(),
            member.status(),
            member.providerType(),
            member.createdAt(),
            member.withdrawnAt(),
            history
        );
    }

    @Override
    @Transactional
    public void penalizeUser(UUID adminId, UUID userId, UserStatusRequest request) {
        memberInternalClient.updateUserStatus(userId, request);

        // enum 변경
        UserStatus status = UserStatus.valueOf(request.status());

        // 유저 상태 변경 request에 따른 분기
        AdminActionType actionType;
        if (status == UserStatus.SUSPENDED) {
            actionType = AdminActionType.SUSPENDED_USER;
        } else if (status == UserStatus.WITHDRAWN) {
            actionType = AdminActionType.WITHDRAW_USER;
        } else {
            return;
        }

        adminActionHistoryRepository.save(
            AdminActionHistory.builder()
                .adminId(adminId)
                .targetType(AdminTargetType.USER)
                .targetId(userId)
                .actionType(actionType)
                .build()
        );
    }

    @Override
    @Transactional
    public void updateUserRole(UUID adminId, UUID userId, UserRoleRequest request) {
        memberInternalClient.updateUserRole(userId, request);

        adminActionHistoryRepository.save(
            AdminActionHistory.builder()
                .adminId(adminId)
                .targetType(AdminTargetType.USER)
                .targetId(userId)
                .actionType(AdminActionType.CHANGE_ROLE)
                .build()
        );
    }
}
