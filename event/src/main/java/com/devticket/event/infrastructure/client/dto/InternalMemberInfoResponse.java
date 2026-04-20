package com.devticket.event.infrastructure.client.dto;

public record InternalMemberInfoResponse(
    String userId,
    String email,
    String nickname,
    String role,
    String status,
    String position
) {}