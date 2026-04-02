package com.devticket.payment.refund.domain.exception;

import com.devticket.payment.common.exception.BusinessException;
import com.devticket.payment.common.exception.ErrorCode;

public class RefundException extends BusinessException {
    public RefundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
