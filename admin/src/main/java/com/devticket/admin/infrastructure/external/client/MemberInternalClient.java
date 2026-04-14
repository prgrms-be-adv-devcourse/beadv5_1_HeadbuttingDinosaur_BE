package com.devticket.admin.infrastructure.external.client;

import com.devticket.admin.infrastructure.external.dto.req.InternalDecideSellerApplicationRequest;
import com.devticket.admin.infrastructure.external.dto.res.InternalDecideSellerApplicationResponse;
import com.devticket.admin.infrastructure.external.dto.res.InternalMemberInfoResponse;
import com.devticket.admin.infrastructure.external.dto.res.InternalSellerApplicationResponse;
import com.devticket.admin.presentation.dto.req.UserRoleRequest;
import com.devticket.admin.presentation.dto.req.UserSearchCondition;
import com.devticket.admin.presentation.dto.req.UserStatusRequest;
import com.devticket.admin.presentation.dto.res.InternalMemberDetailResponse;
import java.util.List;
import java.util.UUID;

public interface MemberInternalClient {

    List<InternalMemberInfoResponse> getMembers(UserSearchCondition condition);

    InternalMemberDetailResponse getMember(UUID userId);

    void updateUserStatus(UUID userId, UserStatusRequest request);

    void updateUserRole(UUID userId, UserRoleRequest request);

    List<InternalSellerApplicationResponse> getSellerApplications();

    InternalDecideSellerApplicationResponse decideSellerApplication(UUID applicationId, InternalDecideSellerApplicationRequest request);
}
