package com.devticket.payment.wallet.infrastructure.persistence;

import com.devticket.payment.wallet.domain.model.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletJpaRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(UUID userId);

    @Modifying
    @Query(value = "INSERT INTO payment.wallet (wallet_id, user_id, balance, version, created_at, updated_at) " +
                   "VALUES (:walletId, :userId, 0, 0, NOW(), NOW()) " +
                   "ON CONFLICT DO NOTHING",
           nativeQuery = true)
    void insertIfAbsent(@Param("walletId") UUID walletId, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);

    // 충전 (입금)
    //원자적 업데이트는 JPA의 자동 버전 관리를 타지 않음 -> 수동으로 버전을 올려주기.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Wallet w SET w.balance = w.balance + :amount, w.version = w.version + 1 " +
           "WHERE w.userId = :userId")
    int chargeBalanceAtomic(@Param("userId") UUID userId, @Param("amount") int amount);

    // 사용/출금 (차감) - 잔액 검증 포함
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Wallet w SET w.balance = w.balance - :amount, w.version = w.version + 1 " +
           "WHERE w.userId = :userId AND w.balance >= :amount")
    int useBalanceAtomic(@Param("userId") UUID userId, @Param("amount") int amount);

    // 환불 (복구)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Wallet w SET w.balance = w.balance + :amount, w.version = w.version + 1 " +
           "WHERE w.userId = :userId")
    int refundBalanceAtomic(@Param("userId") UUID userId, @Param("amount") int amount);
}