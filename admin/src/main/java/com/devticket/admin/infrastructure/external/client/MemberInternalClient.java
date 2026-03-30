package com.devticket.admin.infrastructure.external.client;

import com.devticket.admin.infrastructure.external.dto.InternalMemberInfoResponse;
import com.devticket.admin.presentation.dto.req.UserRoleRequest;
import com.devticket.admin.presentation.dto.req.UserSearchCondition;
import com.devticket.admin.presentation.dto.req.UserStatusRequest;
import java.util.List;
import java.util.UUID;

public interface MemberInternalClient {

    List<InternalMemberInfoResponse> getMembers(UserSearchCondition condition);

    void updateUserStatus(UUID userId, UserStatusRequest request);

    void updateUserRole(UUID userId, UserRoleRequest request);
}
