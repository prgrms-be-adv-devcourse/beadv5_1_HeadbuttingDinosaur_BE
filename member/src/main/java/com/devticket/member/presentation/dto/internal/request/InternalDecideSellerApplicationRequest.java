package com.devticket.member.presentation.dto.internal.request;

import com.devticket.member.presentation.domain.SellerApplicationDecision;

public record InternalDecideSellerApplicationRequest(
    SellerApplicationDecision decision) {

}
