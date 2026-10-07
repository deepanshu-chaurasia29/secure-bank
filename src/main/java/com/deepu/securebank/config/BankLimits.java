package com.deepu.securebank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Holds the business limits read from application.properties.
 * BR-1 max deposit, BR-2 max withdraw, BR-3 max per transfer, BR-4 daily transfer limit, BR-5 min amount.
 * The ":default" part after each setting name means: if the setting is missing, use this value.
 */
@Component
public class BankLimits {

  private final BigDecimal minAmount;
  private final BigDecimal maxDeposit;
  private final BigDecimal maxWithdraw;
  private final BigDecimal maxTransfer;
  private final BigDecimal dailyTransfer;

  public BankLimits(@Value("${bank.limits.min-amount:1.00}") BigDecimal minAmount,
                    @Value("${bank.limits.max-deposit:100000.00}") BigDecimal maxDeposit,
                    @Value("${bank.limits.max-withdraw:50000.00}") BigDecimal maxWithdraw,
                    @Value("${bank.limits.max-transfer:100000.00}") BigDecimal maxTransfer,
                    @Value("${bank.limits.daily-transfer:200000.00}") BigDecimal dailyTransfer) {
    this.minAmount = minAmount;
    this.maxDeposit = maxDeposit;
    this.maxWithdraw = maxWithdraw;
    this.maxTransfer = maxTransfer;
    this.dailyTransfer = dailyTransfer;
  }

  public BigDecimal getMinAmount() { return minAmount; }
  public BigDecimal getMaxDeposit() { return maxDeposit; }
  public BigDecimal getMaxWithdraw() { return maxWithdraw; }
  public BigDecimal getMaxTransfer() { return maxTransfer; }
  public BigDecimal getDailyTransfer() { return dailyTransfer; }
}
