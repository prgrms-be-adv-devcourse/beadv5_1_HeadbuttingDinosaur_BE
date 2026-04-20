package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.User;
import java.time.LocalDateTime;
import java.util.UUID;

public record WithdrawResponse(
    UUID userId,
    String status,
    LocalDateTime withdrawnAt
) {

    public static WithdrawResponse from(User user) {
        return new WithdrawResponse(
            user.getUserId(),
            user.getStatus().name(),
            user.getWithdrawnAt()
        );
    }
}
