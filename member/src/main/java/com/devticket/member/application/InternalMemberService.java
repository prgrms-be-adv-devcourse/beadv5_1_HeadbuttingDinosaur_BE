package com.devticket.member.application;

import com.devticket.member.presentation.dto.internal.response.InternalMemberInfoResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberRoleResponse;
import com.devticket.member.presentation.dto.internal.response.InternalMemberStatusResponse;
import com.devticket.member.presentation.dto.internal.response.InternalSellerInfoResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InternalMemberService {

    public InternalMemberInfoResponse getMemberInfo(UUID userId) {
        // TODO: Phase 4에서 구현
        return new InternalMemberInfoResponse(userId, "stub@example.com",
            "USER", "ACTIVE", "LOCAL");
    }

    public InternalMemberStatusResponse getMemberStatus(UUID userId) {
        // TODO: Phase 4에서 구현
        return new InternalMemberStatusResponse(userId, "ACTIVE");
    }

    public InternalMemberRoleResponse getMemberRole(UUID userId) {
        // TODO: Phase 4에서 구현
        return new InternalMemberRoleResponse(userId, "USER");
    }

    public InternalSellerInfoResponse getSellerInfo(UUID userId) {
        // TODO: Phase 4에서 구현
        return new InternalSellerInfoResponse(userId, "stub-bank", "000-000-0000", "stub-holder");
    }
}