package com.devticket.commerce.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxEventProducer outboxEventProducer;

    @Mock
    private OutboxAfterCommitPublisher outboxAfterCommitPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxService outboxService;

    @BeforeEach
    void setUpTransactionSync() {
        // afterCommit 훅 등록 검증을 위해 동기화 컨텍스트 활성화
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDownTransactionSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void save_호출시_직렬화된_이벤트를_Outbox로_저장한다() throws Exception {
        // given
        TestEvent event = new TestEvent(1L, "created");
        given(objectMapper.writeValueAsString(event)).willReturn("{\"id\":1,\"type\":\"created\"}");
        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        doAnswer(invocation -> {
            Outbox arg = invocation.getArgument(0);
            ReflectionTestUtils.setField(arg, "id", 100L);
            return arg;
        }).when(outboxRepository).save(any(Outbox.class));

        // when
        outboxService.save("aggregate-1", "partition-1", "OrderCreated", "order.created", event);

        // then
        verify(outboxRepository).save(outboxCaptor.capture());
        Outbox saved = outboxCaptor.getValue();
        assertThat(saved.getAggregateId()).isEqualTo("aggregate-1");
        assertThat(saved.getPartitionKey()).isEqualTo("partition-1");
        assertThat(saved.getEventType()).isEqualTo("OrderCreated");
        assertThat(saved.getTopic()).isEqualTo("order.created");
        assertThat(saved.getPayload()).isEqualTo("{\"id\":1,\"type\":\"created\"}");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void save_호출시_afterCommit_훅을_등록하고_훅_실행시_직접발행을_위임한다() throws Exception {
        // given
        TestEvent event = new TestEvent(1L, "created");
        given(objectMapper.writeValueAsString(event)).willReturn("{\"id\":1,\"type\":\"created\"}");
        doAnswer(invocation -> {
            Outbox arg = invocation.getArgument(0);
            ReflectionTestUtils.setField(arg, "id", 100L);
            return arg;
        }).when(outboxRepository).save(any(Outbox.class));

        // when
        outboxService.save("aggregate-1", "partition-1", "OrderCreated", "order.created", event);

        // 훅 등록 직후에는 직접발행을 호출해서는 안 됨 (커밋 후에만 호출)
        verify(outboxAfterCommitPublisher, never()).schedulePublish(any());

        // 훅을 직접 실행해 afterCommit 시점 동작 검증
        List<TransactionSynchronization> syncs = new ArrayList<>(
                TransactionSynchronizationManager.getSynchronizations());
        assertThat(syncs).hasSize(1);
        syncs.get(0).afterCommit();

        // then
        verify(outboxAfterCommitPublisher, times(1)).schedulePublish(eq(100L));
    }

    @Test
    void processOne_성공시_SENT로_변경하고_저장한다() {
        // given
        Outbox outbox = Outbox.create("aggregate-1", "partition-1", "OrderCreated", "order.created", "{\"id\":1}");

        // when
        outboxService.processOne(outbox);

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(outbox.getSentAt()).isNotNull();
        verify(outboxRepository).save(outbox);
    }

    @Test
    void processOne_발행_실패시_markFailed후_저장한다() {
        // given
        Outbox outbox = Outbox.create("aggregate-1", "partition-1", "OrderCreated", "order.created", "{\"id\":1}");
        doThrow(new OutboxPublishException("publish failed", new RuntimeException("kafka error")))
                .when(outboxEventProducer).publish(any());

        // when
        outboxService.processOne(outbox);

        // then
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextRetryAt()).isNotNull();
        verify(outboxRepository).save(outbox);
    }

    @Test
    void processOne_여섯번째_실패시_FAILED로_저장한다() {
        // given
        Outbox outbox = Outbox.create("aggregate-1", "partition-1", "OrderCreated", "order.created", "{\"id\":1}");
        for (int i = 0; i < 5; i++) {
            outbox.markFailed();
        }
        doThrow(new OutboxPublishException("publish failed", new RuntimeException("kafka error")))
                .when(outboxEventProducer).publish(any());

        // when
        outboxService.processOne(outbox);

        // then
        assertThat(outbox.getRetryCount()).isEqualTo(6);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(outboxRepository).save(outbox);
    }

    private record TestEvent(Long id, String type) {
    }
}
