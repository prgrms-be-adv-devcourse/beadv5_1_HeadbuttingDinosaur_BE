package com.devticket.payment.common.idempotency;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByKey(String key);

    boolean existsByKey(String key);
}
