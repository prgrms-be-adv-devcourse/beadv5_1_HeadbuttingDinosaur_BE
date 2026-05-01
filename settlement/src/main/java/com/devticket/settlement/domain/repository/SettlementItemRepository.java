package com.devticket.settlement.domain.repository;
import com.devticket.settlement.domain.model.SettlementItem;
import com.devticket.settlement.domain.model.SettlementItemStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SettlementItemRepository {

    List<SettlementItem> findBySettlementId(UUID settlementId);

    boolean existsByOrderItemId(UUID orderItemId);

    SettlementItem save(SettlementItem settlementItem);

    List<SettlementItem> findByStatusAndEventDateTimeBetween(
        SettlementItemStatus status, LocalDate from, LocalDate to);

    List<SettlementItem> findBySellerIdAndStatusAndEventDateTimeBetween(
        UUID sellerId, SettlementItemStatus status, LocalDate from, LocalDate to);

    List<SettlementItem> saveAll(List<SettlementItem> items);

}
