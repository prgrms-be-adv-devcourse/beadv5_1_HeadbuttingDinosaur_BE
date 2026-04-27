package com.devticket.payment.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * MessageDeduplicationService — Consumer dedup 회귀 방지.
 *
 * 본 테스트는 messageId 타입 통일(UUID → String) 변경(issue #583)에 따른 회귀 가드.
 *  - kafka-idempotency-guide.md §3-5: messageId = String / VARCHAR(36)
 *  - 3모듈(Commerce/Event/Payment) 컨벤션 일치
 *
 * @SpringBootTest + @Transactional — 각 테스트 메서드 종료 시 자동 롤백.
 * @DataJpaTest 는 본 프로젝트 Spring Boot 4.0 starter 구성상 사용 불가.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("MessageDeduplicationService — Consumer dedup")
class MessageDeduplicationServiceTest {

    @Autowired
    private MessageDeduplicationService deduplicationService;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    private static final String TOPIC = "payment.completed";

    @Nested
    @DisplayName("isDuplicate(String)")
    class IsDuplicate {

        @Test
        void 미처리_messageId는_false_반환() {
            String messageId = UUID.randomUUID().toString();

            boolean result = deduplicationService.isDuplicate(messageId);

            assertThat(result).isFalse();
        }

        @Test
        void markProcessed_후_동일_messageId는_true_반환() {
            String messageId = UUID.randomUUID().toString();
            deduplicationService.markProcessed(messageId, TOPIC);

            boolean result = deduplicationService.isDuplicate(messageId);

            assertThat(result).isTrue();
        }

        @Test
        void 서로_다른_messageId는_독립적으로_판정된다() {
            String first = UUID.randomUUID().toString();
            String second = UUID.randomUUID().toString();
            deduplicationService.markProcessed(first, TOPIC);

            boolean result = deduplicationService.isDuplicate(second);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("markProcessed(String, String)")
    class MarkProcessed {

        @Test
        void ProcessedMessage_row가_저장된다() {
            String messageId = UUID.randomUUID().toString();

            deduplicationService.markProcessed(messageId, TOPIC);

            assertThat(processedMessageRepository.existsByMessageId(messageId)).isTrue();
        }

        @Test
        void UUID_표준_36자_String_정상_저장() {
            // UUID v4 toString() = 8-4-4-4-12 = 36자 (하이픈 4개 포함)
            String messageId = UUID.randomUUID().toString();
            assertThat(messageId).hasSize(36);

            deduplicationService.markProcessed(messageId, TOPIC);

            assertThat(processedMessageRepository.existsByMessageId(messageId)).isTrue();
        }

        @Test
        void nameUUIDFromBytes_fallback도_36자_보장() {
            // extractMessageId() 의 fallback 경로 — topic:partition:offset 기반 UUID v3
            String fallback = UUID.nameUUIDFromBytes(
                "payment.completed:0:42".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ).toString();
            assertThat(fallback).hasSize(36);

            deduplicationService.markProcessed(fallback, TOPIC);

            assertThat(processedMessageRepository.existsByMessageId(fallback)).isTrue();
        }
    }
}
