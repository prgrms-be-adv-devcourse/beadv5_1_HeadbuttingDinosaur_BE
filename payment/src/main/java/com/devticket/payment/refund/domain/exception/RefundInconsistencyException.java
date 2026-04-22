package com.devticket.payment.refund.domain.exception;

/**
 * Saga 진행 중 Refund / SagaState 레코드 부정합이 감지됐을 때 발생.
 *
 * 재시도해도 결과가 바뀌지 않으므로 KafkaConsumerConfig 에서 not-retryable 로 등록되어 즉시 DLT 이동한다.
 * 발생 시 [Saga.Inconsistency] 마커 로그가 함께 출력되어 알람/대시보드 트리거에 사용된다.
 */
public class RefundInconsistencyException extends RuntimeException {

    private final String topic;
    private final String messageId;
    private final String payloadSnapshot;

    public RefundInconsistencyException(String topic, String messageId, String payloadSnapshot, Throwable cause) {
        super("환불 saga 부정합 — topic=" + topic + ", messageId=" + messageId, cause);
        this.topic = topic;
        this.messageId = messageId;
        this.payloadSnapshot = payloadSnapshot;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getPayloadSnapshot() {
        return payloadSnapshot;
    }
}
