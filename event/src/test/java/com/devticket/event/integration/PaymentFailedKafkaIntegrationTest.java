package com.devticket.event.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.devticket.event.common.messaging.KafkaTopics;
import com.devticket.event.common.messaging.event.PaymentFailedEvent;
import com.devticket.event.common.outbox.OutboxScheduler;
import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import com.devticket.event.domain.repository.ProcessedMessageRepository;
import com.devticket.event.infrastructure.client.MemberClient;
import com.devticket.event.infrastructure.client.OpenAiEmbeddingClient;
import com.devticket.event.infrastructure.persistence.EventRepository;
import com.devticket.event.infrastructure.search.EventSearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 재고 복구 Kafka E2E 통합 테스트
 *
 * <p>EmbeddedKafka → PaymentFailedConsumer → StockRestoreService → DB 전체 흐름 검증
 *
 * <p>외부 의존성(ES, MemberClient, OpenAI, OutboxScheduler)은 Mock으로 대체.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = {"payment.failed"}
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.ack-mode=manual",
    "spring.kafka.consumer.enable-auto-commit=false",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
class PaymentFailedKafkaIntegrationTest {

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private EventRepository eventRepository;
    @Autowired private ProcessedMessageRepository processedMessageRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EventSearchRepository eventSearchRepository;
    @MockitoBean private MemberClient memberClient;
    @MockitoBean private OpenAiEmbeddingClient openAiEmbeddingClient;
    @MockitoBean private OutboxScheduler outboxScheduler;

    @BeforeEach
    void setUp() {
        processedMessageRepository.deleteAll();
        eventRepository.deleteAll();
    }

    // =========================================================================
    // 테스트 1: E2E — payment.failed 수신 시 재고 복구 성공
    //
    // 공격: payment.failed Kafka 메시지 수신
    // 방어: findAllByEventIdInWithLock 비관적 락 → restoreStock → markProcessed
    // 검증: remainingQuantity 증가 + processed_message 1건
    // =========================================================================
    @Test
    @DisplayName("payment.failed 수신 시 재고가 복구되고 processed_message가 저장된다")
    void payment_failed_수신시_재고_복구_및_processedMessage_저장() throws Exception {
        // given
        Event event = saveEvent(95, 100, EventStatus.ON_SALE);
        UUID messageId = UUID.randomUUID();

        // when
        sendPaymentFailed(messageId, event.getEventId(), 5);

        // then
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Event saved = eventRepository.findByEventId(event.getEventId()).orElseThrow();
            assertThat(saved.getRemainingQuantity()).isEqualTo(100);
        });

        assertThat(processedMessageRepository.existsByMessageId(messageId.toString())).isTrue();
    }

    // =========================================================================
    // 테스트 2: E2E — SOLD_OUT 상태에서 재고 복구 시 ON_SALE로 전이
    //
    // 검증: remainingQuantity > 0 + status = ON_SALE
    // =========================================================================
    @Test
    @DisplayName("SOLD_OUT 이벤트에 payment.failed 수신 시 재고 복구 후 ON_SALE로 전이된다")
    void SOLD_OUT_이벤트_payment_failed_수신시_ON_SALE_전이() throws Exception {
        // given
        Event event = saveEvent(0, 100, EventStatus.SOLD_OUT);
        UUID messageId = UUID.randomUUID();

        // when
        sendPaymentFailed(messageId, event.getEventId(), 3);

        // then
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Event saved = eventRepository.findByEventId(event.getEventId()).orElseThrow();
            assertThat(saved.getRemainingQuantity()).isEqualTo(3);
            assertThat(saved.getStatus()).isEqualTo(EventStatus.ON_SALE);
        });
    }

    // =========================================================================
    // 테스트 3: E2E — 동일 messageId로 두 번 전송 시 재고 1회만 복구
    //
    // 공격: 같은 X-Message-Id로 payment.failed 메시지를 두 번 전송
    // 방어: isDuplicate() → 첫 번째 처리 후 두 번째는 dedup 스킵
    // 검증: 재고 1회만 복구 + processed_message 1건
    // =========================================================================
    @Test
    @DisplayName("동일 X-Message-Id로 두 번 전송 시 재고는 1회만 복구된다")
    void 동일_messageId_두번_전송__재고_1회만_복구() throws Exception {
        // given
        Event event = saveEvent(90, 100, EventStatus.ON_SALE);
        UUID messageId = UUID.randomUUID();

        // when — 첫 번째 전송
        sendPaymentFailed(messageId, event.getEventId(), 5);

        // 첫 번째 처리 완료 대기
        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(processedMessageRepository.existsByMessageId(messageId.toString())).isTrue()
        );

        // 두 번째 전송 — 같은 messageId
        sendPaymentFailed(messageId, event.getEventId(), 5);

        Thread.sleep(2000);

        // then — 1회만 복구 (90 + 5 = 95, 이중 복구 시 100)
        assertThat(eventRepository.findByEventId(event.getEventId()).orElseThrow()
            .getRemainingQuantity()).isEqualTo(95);
        assertThat(processedMessageRepository.count()).isEqualTo(1);
    }

    // =========================================================================
    // 테스트 4: E2E — FORCE_CANCELLED 이벤트는 재고 복구 스킵
    //
    // 검증: remainingQuantity 불변 + processed_message는 저장됨 (처리 완료로 기록)
    // =========================================================================
    @Test
    @DisplayName("FORCE_CANCELLED 이벤트에 payment.failed 수신 시 재고 복구를 스킵한다")
    void FORCE_CANCELLED_이벤트_payment_failed_수신시_재고_복구_스킵() throws Exception {
        // given
        Event event = saveEvent(95, 100, EventStatus.FORCE_CANCELLED);
        UUID messageId = UUID.randomUUID();

        // when
        sendPaymentFailed(messageId, event.getEventId(), 5);

        // then — processed_message 저장 대기 (스킵이지만 처리 완료 기록됨)
        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(processedMessageRepository.existsByMessageId(messageId.toString())).isTrue()
        );

        assertThat(eventRepository.findByEventId(event.getEventId()).orElseThrow()
            .getRemainingQuantity()).isEqualTo(95);
    }

    // =========================================================================
    // 테스트 5: E2E — X-Message-Id 헤더 누락 시 DB 상태 변화 없음
    //
    // 공격: X-Message-Id 헤더 없이 payment.failed 전송
    // 현재 동작: IllegalStateException → 에러 핸들러가 재시도 후 스킵
    // 검증: DB 상태 변화 없음
    // =========================================================================
    @Test
    @DisplayName("X-Message-Id 헤더 없이 payment.failed 수신 시 DB 상태 변화 없이 스킵된다")
    void X_Message_Id_헤더_누락시_DB_상태_변화_없음() throws Exception {
        // given
        Event event = saveEvent(95, 100, EventStatus.ON_SALE);
        String payload = buildPayload(UUID.randomUUID(), event.getEventId(), 5);
        ProducerRecord<String, String> record = new ProducerRecord<>(
            KafkaTopics.PAYMENT_FAILED, null, UUID.randomUUID().toString(), payload
        );
        // X-Message-Id 헤더를 의도적으로 추가하지 않음

        // when
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);

        Thread.sleep(3000);

        // then
        assertThat(processedMessageRepository.count()).isEqualTo(0);
        assertThat(eventRepository.findByEventId(event.getEventId()).orElseThrow()
            .getRemainingQuantity()).isEqualTo(95);
    }

    // =========================================================================
    // 픽스처 / 헬퍼
    // =========================================================================

    private Event saveEvent(int remainingQuantity, int totalQuantity, EventStatus status) {
        Event event = Event.create(
            UUID.randomUUID(), "테스트 이벤트", "설명", "서울",
            LocalDateTime.now().plusDays(10),
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(5),
            50000, totalQuantity, 10, EventCategory.MEETUP
        );
        ReflectionTestUtils.setField(event, "remainingQuantity", remainingQuantity);
        ReflectionTestUtils.setField(event, "status", status);
        return eventRepository.save(event);
    }

    private void sendPaymentFailed(UUID messageId, UUID eventId, int quantity) throws Exception {
        String payload = buildPayload(UUID.randomUUID(), eventId, quantity);
        ProducerRecord<String, String> record = new ProducerRecord<>(
            KafkaTopics.PAYMENT_FAILED, null, UUID.randomUUID().toString(), payload
        );
        record.headers().add("X-Message-Id", messageId.toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
    }

    private String buildPayload(UUID orderId, UUID eventId, int quantity) {
        PaymentFailedEvent event = new PaymentFailedEvent(
            orderId, UUID.randomUUID(),
            List.of(new PaymentFailedEvent.OrderItem(eventId, quantity)),
            "PG 실패", Instant.now()
        );
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("직렬화 실패", e);
        }
    }
}
