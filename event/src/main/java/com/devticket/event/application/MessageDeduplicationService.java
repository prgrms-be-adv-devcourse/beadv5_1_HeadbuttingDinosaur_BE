package com.devticket.event.application;

import com.devticket.event.domain.model.ProcessedMessage;
import com.devticket.event.domain.repository.ProcessedMessageRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageDeduplicationService {

    private final ProcessedMessageRepository processedMessageRepository;

    public boolean isDuplicate(UUID messageId) {
        return processedMessageRepository.existsByMessageId(messageId.toString());
    }

    public void markProcessed(UUID messageId, String topic) {
        ProcessedMessage record = ProcessedMessage.builder()
            .messageId(messageId.toString())
            .topic(topic)
            .processedAt(Instant.now())
            .build();
        processedMessageRepository.save(record);
    }
}
