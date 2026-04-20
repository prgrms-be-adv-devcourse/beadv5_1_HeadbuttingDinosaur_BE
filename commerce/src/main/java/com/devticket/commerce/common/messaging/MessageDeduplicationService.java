package com.devticket.commerce.common.messaging;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageDeduplicationService {

    private final ProcessedMessageRepository processedMessageRepository;

    // 중복 메시지 여부 조회 — 대부분의 중복을 빠르게 스킵
    public boolean isDuplicate(UUID messageId) {
        return processedMessageRepository.existsByMessageId(messageId.toString());
    }

    // 처리 완료 기록 — 반드시 비즈니스 로직과 동일한 @Transactional 경계 안에서 호출
    // UNIQUE 충돌(DataIntegrityViolationException) 시: 다른 요청이 이미 처리 완료 → 호출부에서 catch 후 롤백 + ACK
    public void markProcessed(UUID messageId, String topic) {
        ProcessedMessage record = ProcessedMessage.builder()
                .messageId(messageId.toString())
                .topic(topic)
                .processedAt(Instant.now())
                .build();
        processedMessageRepository.save(record);
    }
}
