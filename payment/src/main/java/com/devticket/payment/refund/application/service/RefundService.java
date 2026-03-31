package com.devticket.payment.refund.application.service;

import com.devticket.payment.refund.presentation.dto.RefundInfoResponse;
import com.devticket.payment.refund.presentation.dto.PgRefundRequest;
import com.devticket.payment.refund.presentation.dto.PgRefundResponse;
import java.util.UUID;

public interface RefundService {
    RefundInfoResponse getRefundInfo(UUID userId, String ticketId);
    PgRefundResponse refundPgTicket(UUID userId, String ticketId, PgRefundRequest request);
}
