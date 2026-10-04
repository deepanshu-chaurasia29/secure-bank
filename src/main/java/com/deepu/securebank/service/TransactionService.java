package com.deepu.securebank.service;

import com.deepu.securebank.config.BankLimits;
import com.deepu.securebank.dto.AmountRequest;
import com.deepu.securebank.dto.PageResponse;
import com.deepu.securebank.dto.TransactionResponse;
import com.deepu.securebank.exception.ApiException;
import com.deepu.securebank.model.Account;
import com.deepu.securebank.model.Transaction;
import com.deepu.securebank.repository.AccountRepository;
import com.deepu.securebank.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TransactionService {

    private static final Set<String> VALID_TYPES = Set.of("DEPOSIT", "WITHDRAW", "TRANSFER_OUT", "TRANSFER_IN");
    private static final int MAX_PAGE_SIZE = 50;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BankLimits limits;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              BankLimits limits) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.limits = limits;
    }

    // ------------------------------------------------------------------
    // DEPOSIT (FR-C1)
    // ------------------------------------------------------------------
    @Transactional
    public TransactionResponse deposit(Long userId, AmountRequest request) {
        BigDecimal amount = checkAmount(request.getAmount(), limits.getMaxDeposit(), "deposit");

        Account account = lockActiveAccount(userId);          // row is locked from here
        BigDecimal newBalance = account.getBalance().add(amount);

        return applyChange(account, "DEPOSIT", amount, newBalance, request.getRemark());
    }

    // ------------------------------------------------------------------
    // WITHDRAW (FR-C2)
    // ------------------------------------------------------------------
    @Transactional
    public TransactionResponse withdraw(Long userId, AmountRequest request) {
        BigDecimal amount = checkAmount(request.getAmount(), limits.getMaxWithdraw(), "withdrawal");

        Account account = lockActiveAccount(userId);          // row is locked from here

        // Check the balance AFTER locking. If we checked before, another request
        // could spend the money in between (race condition).
        if (account.getBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient balance");
        }
        BigDecimal newBalance = account.getBalance().subtract(amount);

        return applyChange(account, "WITHDRAW", amount, newBalance, request.getRemark());
    }

    // ------------------------------------------------------------------
    // HISTORY (FR-D1, FR-D2, FR-D3 with size=5)
    // ------------------------------------------------------------------
    public PageResponse<TransactionResponse> history(Long userId, int page, int size,
                                                     String type, LocalDate from, LocalDate to) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page cannot be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String cleanType = null;
        if (type != null && !type.isBlank()) {
            cleanType = type.trim().toUpperCase();
            if (!VALID_TYPES.contains(cleanType)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "type must be one of " + VALID_TYPES);
            }
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "'from' date cannot be after 'to' date");
        }

        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));

        LocalDateTime fromTime = (from == null) ? null : from.atStartOfDay();
        // "to" is a whole day, so we go up to (but not including) the next day's midnight.
        LocalDateTime toExclusive = (to == null) ? null : to.plusDays(1).atStartOfDay();

        List<TransactionResponse> rows = transactionRepository
                .findPage(account.getId(), cleanType, fromTime, toExclusive, page, size)
                .stream().map(this::toResponse).toList();
        long total = transactionRepository.count(account.getId(), cleanType, fromTime, toExclusive);

        return new PageResponse<>(rows, page, size, total);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------
    private BigDecimal checkAmount(BigDecimal raw, BigDecimal max, String what) {
        BigDecimal amount = raw.setScale(2); // validation already guarantees at most 2 decimals
        if (amount.compareTo(limits.getMinAmount()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Minimum amount is " + limits.getMinAmount());
        }
        if (amount.compareTo(max) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Maximum per " + what + " is " + max);
        }
        return amount;
    }

    /** Locks the customer's account row and makes sure it is not FROZEN (FR-C10). */
    private Account lockActiveAccount(Long userId) {
        Account account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is frozen. Please contact the bank.");
        }
        return account;
    }

    /** Update the balance and write the transaction row. Both happen inside the caller's transaction. */
    private TransactionResponse applyChange(Account account, String type, BigDecimal amount,
                                            BigDecimal newBalance, String remark) {
        accountRepository.updateBalance(account.getId(), newBalance);

        Transaction t = new Transaction();
        t.setReferenceId(UUID.randomUUID().toString());
        t.setAccountId(account.getId());
        t.setType(type);
        t.setAmount(amount);
        t.setBalanceAfter(newBalance);
        t.setStatus("SUCCESS");
        t.setRemark(remark == null || remark.isBlank() ? null : remark.trim());
        transactionRepository.save(t);

        return new TransactionResponse(t.getReferenceId(), type, amount, newBalance,
                null, "SUCCESS", t.getRemark(), LocalDateTime.now());
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(t.getReferenceId(), t.getType(), t.getAmount(), t.getBalanceAfter(),
                mask(t.getRelatedAccountNumber()), t.getStatus(), t.getRemark(), t.getCreatedAt());
    }

    /** FR-D5: show only the last 4 digits of the other party's account number. */
    private String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return null;
        }
        return "XXXXXXXX" + accountNumber.substring(accountNumber.length() - 4);
    }
}
