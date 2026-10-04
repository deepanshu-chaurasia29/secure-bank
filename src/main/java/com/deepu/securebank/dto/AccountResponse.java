package com.deepu.securebank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** What the customer sees about their own account. */
public class AccountResponse {
    private final String accountNumber;
    private final String accountType;
    private final String status;
    private final BigDecimal balance;
    private final LocalDateTime createdAt;

    public AccountResponse(String accountNumber, String accountType, String status,
                           BigDecimal balance, LocalDateTime createdAt) {
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.status = status;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public String getAccountNumber() { return accountNumber; }
    public String getAccountType() { return accountType; }
    public String getStatus() { return status; }
    public BigDecimal getBalance() { return balance; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
