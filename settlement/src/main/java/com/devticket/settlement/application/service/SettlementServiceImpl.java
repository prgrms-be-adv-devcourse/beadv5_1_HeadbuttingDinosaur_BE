package com.devticket.settlement.application.service;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devticket.settlement.domain.repository.SettlementRepository;
import com.devticket.settlement.presentation.dto.SettlementResponse;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementServiceImpl implements SettlementService{

    private final SettlementRepository settlementRepository;

    @Override
    public List<SettlementResponse> getSellerSettlements(UUID sellerId) {
//        settlementRepository.findBySellerId(sellerId).stream()
//            .map(SettlementResponse::)
        return List.of();
    }
}
