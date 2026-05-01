package com.devticket.commerce.common.outbox;

public class OutboxPublishException extends RuntimeException {

    public OutboxPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
