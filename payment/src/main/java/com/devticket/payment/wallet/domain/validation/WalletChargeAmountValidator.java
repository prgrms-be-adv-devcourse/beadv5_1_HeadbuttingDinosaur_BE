package com.devticket.payment.wallet.domain.validation;

import com.devticket.payment.wallet.domain.WalletPolicyConstants;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class WalletChargeAmountValidator implements ConstraintValidator<ValidWalletChargeAmount, Integer> {

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {

        if (value == null) return false;

        if (value < WalletPolicyConstants.MIN_CHARGE_AMOUNT ||
            value > WalletPolicyConstants.MAX_CHARGE_AMOUNT) {

            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "충전 금액은 " +
                    WalletPolicyConstants.MIN_CHARGE_AMOUNT +
                    "원 이상 " +
                    WalletPolicyConstants.MAX_CHARGE_AMOUNT +
                    "원 이하여야 합니다."
            ).addConstraintViolation();

            return false;
        }

        return true;
    }
}
