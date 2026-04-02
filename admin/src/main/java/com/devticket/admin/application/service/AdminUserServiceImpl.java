package com.devticket.admin.application.service;

import com.devticket.admin.domain.model.AdminActionHistory;
import com.devticket.admin.domain.model.AdminActionType;
import com.devticket.admin.domain.model.AdminTargetType;
import com.devticket.admin.domain.model.temporaryEnum.UserStatus;
import com.devticket.admin.infrastructure.external.client.MemberInternalClient;
import com.devticket.admin.infrastructure.persistence.repository.AdminActionHistoryRepositoryImpl;
import com.devticket.admin.presentation.dto.req.UserRoleRequest;
import com.devticket.admin.presentation.dto.req.UserSearchCondition;
import com.devticket.admin.presentation.dto.req.UserStatusRequest;
import com.devticket.admin.presentation.dto.res.UserListResponse;
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


    @Override
    public List<UserListResponse> getMembers(UserSearchCondition condition) {
        return memberInternalClient.getMembers(condition).stream()
            .map(member -> new UserListResponse(
                member.id(),
                member.email(),
                member.role(),
                member.status(),
                member.providerType(),
                null,
                null,
                null
            )).toList();
    }

    @Override
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
