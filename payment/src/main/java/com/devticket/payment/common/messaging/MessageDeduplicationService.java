package com.devticket.payment.common.messaging;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageDeduplicationService {

    private final ProcessedMessageRepository processedMessageRepository;

    /**
     * 이미 처리된 메시지인지 확인한다.
     */
    public boolean isDuplicate(UUID messageId) {
        return processedMessageRepository.existsByMessageId(messageId);
    }

    /**
     * 메시지를 처리 완료로 기록한다.
     * 반드시 비즈니스 로직과 같은 트랜잭션 안에서 호출해야 한다.
     */
    public void markProcessed(UUID messageId) {
        processedMessageRepository.save(ProcessedMessage.of(messageId));
    }
}
