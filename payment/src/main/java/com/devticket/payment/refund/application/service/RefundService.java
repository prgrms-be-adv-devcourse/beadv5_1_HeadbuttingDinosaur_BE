package com.devticket.payment.refund.application.service;

import com.devticket.payment.refund.presentation.dto.RefundInfoResponse;
import java.util.UUID;

public interface RefundService {
    RefundInfoResponse getRefundInfo(UUID userId, String ticketId);
}
