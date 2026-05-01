package com.devticket.payment.payment.domain.exception;

import com.devticket.payment.common.exception.BusinessException;
import com.devticket.payment.common.exception.ErrorCode;

public class PaymentException extends BusinessException {
    public PaymentException(ErrorCode errorCode) {
        super(errorCode);
    }
}
