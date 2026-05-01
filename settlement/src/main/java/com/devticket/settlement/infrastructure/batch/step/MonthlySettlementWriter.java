package com.devticket.settlement.infrastructure.batch.step;

import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.repository.SettlementItemRepository;
import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.infrastructure.batch.dto.SettlementResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlySettlementWriter implements ItemWriter<SettlementResult> {

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;

    @Override
    public void write(Chunk<? extends SettlementResult> chunk) {
        for (SettlementResult result : chunk.getItems()) {
            settlementRepository.save(result.settlement());

            if (result.pendingSettlement() != null) {
                result.pendingSettlement().updateCarriedToSettlementId(result.settlement().getSettlementId());
                settlementRepository.save(result.pendingSettlement());
            }

            List<SettlementItem> items = result.items();
            if (!items.isEmpty()) {
                items.forEach(item -> item.finalize(result.settlement().getSettlementId()));
                settlementItemRepository.saveAll(items);
            }
        }
        log.info("[MonthlySettlementWriter] 정산서 저장 완료 - {}건", chunk.size());
    }
}