package com.devticket.payment.wallet.infrastructure.client.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InternalEventOrdersResponse {

    private Long eventId;
    private List<OrderInfo> orders;

    @Getter
    @NoArgsConstructor
    public static class OrderInfo {

        private Long orderId;
        private String userId;       // UUID String — Commerce 팀과 합의 필요
        private String paymentMethod; // "WALLET" | "PG"
        private int totalAmount;
        private String status;        // "PAID" 필터링용
    }
}

