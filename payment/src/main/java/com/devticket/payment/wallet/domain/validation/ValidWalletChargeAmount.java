package com.devticket.payment.wallet.domain.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WalletChargeAmountValidator.class)
public @interface ValidWalletChargeAmount {

    String message() default "충전 금액이 정책 범위를 벗어났습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
