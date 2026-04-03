package com.devticket.settlement.infrastructure.batch;

import com.devticket.settlement.domain.model.Settlement;
import com.devticket.settlement.domain.repository.SettlementRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementItemWriter implements ItemWriter<List<Settlement>> {

    private final SettlementRepository settlementRepository;

    @Override
    public void write(Chunk<? extends List<Settlement>> chunk) throws Exception {
        List<Settlement> settlements = chunk.getItems().stream()
            .flatMap(List::stream)
            .toList();
        log.info("[SettlementItemWriter] 정산 데이터 저장 - 건수: {}", settlements.size());
        settlementRepository.saveAll(settlements);
    }
}
