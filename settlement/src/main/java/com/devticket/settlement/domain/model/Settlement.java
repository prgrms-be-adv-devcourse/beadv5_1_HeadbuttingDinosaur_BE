package com.devticket.settlement.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Settlement {

    private UUID id;
    private UUID seller_id;
    private LocalDateTime period_start_at;
    private LocalDateTime period_end_at;
    private int total_sales_amount;
    private int total_refund_amount;
    private int total_fee_amount;
    private int final_settlement_amount;
    
}