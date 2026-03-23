package com.devticket.member.presentation.dto.response;

public record MemberInternalResponse(
    Long id,
    String email,
    String role,
    String status,
    String providerType
) {

}