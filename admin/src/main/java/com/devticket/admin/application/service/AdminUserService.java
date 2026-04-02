package com.devticket.admin.application.service;

import com.devticket.admin.presentation.dto.req.UserRoleRequest;
import com.devticket.admin.presentation.dto.req.UserSearchCondition;
import com.devticket.admin.presentation.dto.req.UserStatusRequest;
import com.devticket.admin.presentation.dto.res.UserListResponse;
import java.util.List;
import java.util.UUID;

public interface AdminUserService {

    List<UserListResponse> getMembers(UserSearchCondition condition);

    void penalizeUser(UUID adminId, UUID userId, UserStatusRequest request);

    void updateUserRole(UUID adminId, UUID userId, UserRoleRequest request);

}
