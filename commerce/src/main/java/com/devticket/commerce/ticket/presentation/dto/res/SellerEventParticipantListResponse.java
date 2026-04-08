package com.devticket.commerce.ticket.presentation.dto.res;

import java.util.List;
import org.springframework.data.domain.Page;

public record SellerEventParticipantListResponse(

    List<SellerEventParticipantResponse> sellerEventParticipantListResponse,

    int page,

    int size,

    long totalElements,

    int totalPages
) {

    public static SellerEventParticipantListResponse of(
        Page<?> pageInfo,
        List<SellerEventParticipantResponse> content
    ) {
        return new SellerEventParticipantListResponse(
            content,
            pageInfo.getNumber(),
            pageInfo.getSize(),
            pageInfo.getTotalElements(),
            pageInfo.getTotalPages()
        );
    }


}
