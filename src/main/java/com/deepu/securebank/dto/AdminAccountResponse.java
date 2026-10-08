package com.deepu.securebank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What an ADMIN sees about one customer account (account + owner in one row).
 * It never contains the password hash.
 */
public class AdminAccountResponse {
    private final Long accountId;
    private final String accountNumber;
    private final String accountType;
    private final String status;          // ACTIVE or FROZEN
    private final BigDecimal balance;
    private final LocalDateTime createdAt;
    private final Long userId;
    private final String fullName;
    private final String email;
    private final String phone;
    private final boolean locked;         // true while the user is locked out after wrong passwords
    private final LocalDateTime lockedUntil;

    public AdminAccountResponse(Long accountId, String accountNumber, String accountType, String status,
                                BigDecimal balance, LocalDateTime createdAt, Long userId, String fullName,
                                String email, String phone, boolean locked, LocalDateTime lockedUntil) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.status = status;
        this.balance = balance;
        this.createdAt = createdAt;
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.locked = locked;
        this.lockedUntil = lockedUntil;
    }

    public Long getAccountId() { return accountId; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountType() { return accountType; }
    public String getStatus() { return status; }
    public BigDecimal getBalance() { return balance; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public boolean isLocked() { return locked; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
}
