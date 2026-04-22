package com.devticket.payment.refund.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerEventCancelRequest(
    @NotBlank(message = "취소 사유는 필수입니다.")
    @Size(max = 200, message = "취소 사유는 200자 이하로 작성해주세요.")
    String reason
) {}
