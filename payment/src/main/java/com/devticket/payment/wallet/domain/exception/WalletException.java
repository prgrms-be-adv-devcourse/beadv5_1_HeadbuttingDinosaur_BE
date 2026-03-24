package com.devticket.payment.wallet.domain.exception;

import com.devticket.payment.common.exception.BusinessException;
import com.devticket.payment.common.exception.ErrorCode;

public class WalletException extends BusinessException {
    public WalletException(ErrorCode errorCode) {
        super(errorCode);
    }
}
