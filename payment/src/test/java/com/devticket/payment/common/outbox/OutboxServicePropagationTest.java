package com.devticket.payment.common.outbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

/**
 * OutboxService.save() @Transactional(propagation=MANDATORY) 불변식 회귀 방지.
 *
 * outbox_fix.md §2:
 *   외부 `@Transactional` 없이 호출되면 비즈니스 DB 커밋과 Outbox 커밋 분리 위험 →
 *   MANDATORY 로 강제하여 컴파일 이후 첫 호출 시 예외 발생.
 *
 * 본 테스트가 통과해야 "실수로 @Transactional 빠뜨린 호출부"를 CI 에서 즉시 감지 가능.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("OutboxService.save() — @Transactional(MANDATORY) 불변식")
class OutboxServicePropagationTest {

    @Autowired
    private OutboxService outboxService;

    @Test
    void 외부_트랜잭션_없이_호출하면_IllegalTransactionStateException() {
        assertThatThrownBy(() ->
            outboxService.save(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "payment.completed",
                "payment.completed",
                new TestEvent("order-1")
            )
        ).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @Transactional
    void 외부_트랜잭션_안에서_호출하면_정상_저장() {
        assertThatCode(() ->
            outboxService.save(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "payment.completed",
                "payment.completed",
                new TestEvent("order-2")
            )
        ).doesNotThrowAnyException();
    }

    private record TestEvent(String orderId) {}
}
