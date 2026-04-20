package com.devticket.member.presentation.dto.response;

import com.devticket.member.presentation.domain.model.SellerApplication;
import java.util.UUID;

public record SellerApplicationResponse(UUID applicationId) {

    public static SellerApplicationResponse from(SellerApplication application) {
        return new SellerApplicationResponse(application.getSellerApplicationId());
    }
}

