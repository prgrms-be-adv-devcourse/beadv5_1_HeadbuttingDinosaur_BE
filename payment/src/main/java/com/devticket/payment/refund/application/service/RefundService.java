package com.devticket.payment.refund.application.service;

import com.devticket.payment.refund.presentation.dto.OrderRefundResponse;
import com.devticket.payment.refund.presentation.dto.RefundDetailResponse;
import com.devticket.payment.refund.presentation.dto.RefundInfoResponse;
import com.devticket.payment.refund.presentation.dto.RefundListItemResponse;
import com.devticket.payment.refund.presentation.dto.SellerRefundListItemResponse;
import com.devticket.payment.refund.presentation.dto.PgRefundRequest;
import com.devticket.payment.refund.presentation.dto.PgRefundResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RefundService {
    RefundInfoResponse getRefundInfo(UUID userId, String ticketId);
    void cancelSellerEvent(UUID sellerId, UUID eventId, String reason);
    void cancelAdminEvent(UUID adminId, UUID eventId, String reason);
    PgRefundResponse refundPgTicket(UUID userId, String ticketId, PgRefundRequest request);
    OrderRefundResponse refundOrder(UUID userId, UUID orderId, String reason);
    Page<RefundListItemResponse> getRefundList(UUID userId, Pageable pageable);
    RefundDetailResponse getRefundDetail(UUID userId, UUID refundId);
    Page<SellerRefundListItemResponse> getSellerRefundListByEventId(UUID sellerId, String eventId, Pageable pageable);
}
