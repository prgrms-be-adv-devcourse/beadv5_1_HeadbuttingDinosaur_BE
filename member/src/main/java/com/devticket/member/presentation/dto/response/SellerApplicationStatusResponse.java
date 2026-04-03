package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.SellerApplication;
import java.time.LocalDateTime;

public record SellerApplicationStatusResponse(
    String status,
    LocalDateTime createdAt
) {

    public static SellerApplicationStatusResponse from(SellerApplication application) {
        return new SellerApplicationStatusResponse(
            application.getStatus().name(),
            application.getCreatedAt()
        );
    }
}

