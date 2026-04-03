package com.devticket.payment.wallet.domain;

public final class WalletPolicyConstants {

    public static final int MIN_CHARGE_AMOUNT = 1_000;
    public static final int MAX_CHARGE_AMOUNT = 50_000;
    public static final int DAILY_CHARGE_LIMIT = 1_000_000;

    private WalletPolicyConstants() {
    }
}
