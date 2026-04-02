package com.devticket.settlement.infrastructure.external;

import com.devticket.settlement.infrastructure.client.dto.res.InternalSettlementDataResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
public class MockCommerceController {

    @GetMapping("/settlement-data")
    public InternalSettlementDataResponse getSettlementData(
        @RequestParam(required = false) UUID sellerId,
        @RequestParam String periodStart,
        @RequestParam String periodEnd
    ) {
        return new InternalSettlementDataResponse(
            sellerId,
            periodStart,
            periodEnd,
            List.of(
                new InternalSettlementDataResponse.EventSettlements(
                    UUID.randomUUID(),
                    810000,
                    45000,
                    27,
                    3,
                    List.of(
                        new InternalSettlementDataResponse.EventSettlements.OrderItems(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            30000,
                            1,
                            30000,
                            "PAID"
                        )
                    )
                )
            )
        );
    }
}
