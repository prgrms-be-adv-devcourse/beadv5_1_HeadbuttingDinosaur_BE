package com.devticket.payment.wallet.domain.repository;

import com.devticket.payment.wallet.domain.model.WalletCharge;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface WalletChargeRepository {

    WalletCharge save(WalletCharge walletCharge);

    Optional<WalletCharge> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<WalletCharge> findByChargeId(UUID chargeId);

    int sumTodayChargeAmount(UUID userId, LocalDateTime startOfDay);
}