package com.devticket.payment.common.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {

    boolean existsByMessageId(String messageId);
}
