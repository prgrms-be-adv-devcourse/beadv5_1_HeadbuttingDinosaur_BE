package com.devticket.admin.infrastructure.external.dto.res;


public record InternalMemberInfoResponse(
    String id,
    String email,
    String role,
    String status,
    String providerType
) {


}
