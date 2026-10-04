package com.deepu.securebank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Holds the business limits (BR-1, BR-2, BR-5) read from application.properties.
 * Services use this class, so limits are never hardcoded inside business code.
 */
@Component
public class BankLimits {

    private final BigDecimal minAmount;
    private final BigDecimal maxDeposit;
    private final BigDecimal maxWithdraw;

    public BankLimits(@Value("${bank.limits.min-amount}") BigDecimal minAmount,
                      @Value("${bank.limits.max-deposit}") BigDecimal maxDeposit,
                      @Value("${bank.limits.max-withdraw}") BigDecimal maxWithdraw) {
        this.minAmount = minAmount;
        this.maxDeposit = maxDeposit;
        this.maxWithdraw = maxWithdraw;
    }

    public BigDecimal getMinAmount() { return minAmount; }
    public BigDecimal getMaxDeposit() { return maxDeposit; }
    public BigDecimal getMaxWithdraw() { return maxWithdraw; }
}
