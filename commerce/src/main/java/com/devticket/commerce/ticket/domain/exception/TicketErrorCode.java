package com.devticket.commerce.ticket.domain.exception;


import com.devticket.commerce.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TicketErrorCode implements ErrorCode {

    TICKET_NOT_FOUND(404, "TICKET_001", "티켓 정보가 없습니다.");


    private final int status;
    private final String code;
    private final String message;
}
