package com.devticket.event.common.outbox;

/**
 * Outbox → Kafka 발행 실패를 나타내는 checked exception
 *
 * <p>Producer 내부에서 발생한 Kafka 관련 예외
 * (ExecutionException, TimeoutException, InterruptedException, KafkaException 등)를
 * 단일 계약으로 감싸 호출부에 전달한다.
 *
 * <p>checked로 설계한 이유: 호출부(OutboxService.processOne)에서
 * catch 후 markFailed/save 보장을 강제하기 위함.
 */
public class OutboxPublishException extends Exception {

    public OutboxPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
