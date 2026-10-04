package com.deepu.securebank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One transaction row as shown to the customer. */
public class TransactionResponse {
    private final String referenceId;
    private final String type;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final String relatedAccount; // masked, e.g. XXXXXXXX1234 (null for deposit/withdraw)
    private final String status;
    private final String remark;
    private final LocalDateTime createdAt;

    public TransactionResponse(String referenceId, String type, BigDecimal amount, BigDecimal balanceAfter,
                               String relatedAccount, String status, String remark, LocalDateTime createdAt) {
        this.referenceId = referenceId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.relatedAccount = relatedAccount;
        this.status = status;
        this.remark = remark;
        this.createdAt = createdAt;
    }

    public String getReferenceId() { return referenceId; }
    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getRelatedAccount() { return relatedAccount; }
    public String getStatus() { return status; }
    public String getRemark() { return remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
