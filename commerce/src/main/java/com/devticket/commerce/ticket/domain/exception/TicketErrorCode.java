package com.devticket.commerce.ticket.domain.exception;

import com.devticket.commerce.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TicketErrorCode implements ErrorCode {
    TICKET_NOT_FOUND(404, "TICKET_001", "존재하지 않는 티켓입니다."),
    UNAUTHORIZED_EVENT_ACCESS(403, "TICKET_002", "해당 이벤트에 대한 접근 권한이 없습니다."),
    INVALID_TICKET_STATUS_TRANSITION(400, "TICKET_003", "현재 티켓 상태에서 허용되지 않는 전이입니다.");

    private final int status;
    private final String code;
    private final String message;
}
