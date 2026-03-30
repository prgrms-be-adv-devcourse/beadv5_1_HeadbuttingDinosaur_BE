package com.devticket.payment.common.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    List<Outbox> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
