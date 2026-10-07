package com.deepu.securebank.service;

import com.deepu.securebank.config.BankLimits;
import com.deepu.securebank.dto.TransactionResponse;
import com.deepu.securebank.dto.TransferRequest;
import com.deepu.securebank.exception.ApiException;
import com.deepu.securebank.model.Account;
import com.deepu.securebank.model.Transaction;
import com.deepu.securebank.repository.AccountRepository;
import com.deepu.securebank.repository.TransactionRepository;
import com.deepu.securebank.util.AccountMasker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Moves money from the logged-in customer's account to another account.
 * Rules: SRS FR-C3, FR-C4, FR-C5, FR-C6, FR-C10 and business rules BR-3, BR-4, BR-5, BR-6.
 */
@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BankLimits limits;

    public TransferService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           BankLimits limits) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.limits = limits;
    }

    /**
     * READ_COMMITTED: every SELECT inside this transaction sees the LATEST committed data.
     * MySQL's default (REPEATABLE READ) would show us an older "snapshot" for normal SELECTs,
     * and the daily-limit total could miss a transfer that finished while we were waiting for the lock.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse transfer(Long userId, TransferRequest request) {

        BigDecimal amount = checkAmount(request.getAmount());

        // STEP 1: a quick look (no lock) just to learn the two account ids.
        Account senderPeek = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        Account receiverPeek = accountRepository.findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Receiver account not found"));

        if (senderPeek.getId().equals(receiverPeek.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot transfer money to your own account");
        }

        // STEP 2: lock BOTH rows, always the LOWER id first (this prevents deadlock).
        Long firstId = Math.min(senderPeek.getId(), receiverPeek.getId());
        Long secondId = Math.max(senderPeek.getId(), receiverPeek.getId());
        Account first = lockById(firstId);
        Account second = lockById(secondId);

        // From now on we use ONLY the locked copies. The "peek" copies may be old.
        Account sender = first.getId().equals(senderPeek.getId()) ? first : second;
        Account receiver = (sender == first) ? second : first;

        // STEP 3: rules, checked on the locked (up-to-date) data.
        if (!"ACTIVE".equals(sender.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account is frozen. Please contact the bank.");
        }
        if (!"ACTIVE".equals(receiver.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Receiver account cannot receive money right now");
        }
        if (sender.getBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient balance");
        }
        checkDailyLimit(sender, amount);

        // STEP 4: move the money. Two balance updates + two history rows, all in ONE transaction.
        BigDecimal senderNewBalance = sender.getBalance().subtract(amount);
        BigDecimal receiverNewBalance = receiver.getBalance().add(amount);
        accountRepository.updateBalance(sender.getId(), senderNewBalance);
        accountRepository.updateBalance(receiver.getId(), receiverNewBalance);

        String remark = (request.getRemark() == null || request.getRemark().isBlank())
                ? null : request.getRemark().trim();

        Transaction out = buildRow(sender, "TRANSFER_OUT", amount, senderNewBalance, receiver.getId(), remark);
        Transaction in = buildRow(receiver, "TRANSFER_IN", amount, receiverNewBalance, sender.getId(), remark);
        transactionRepository.save(out);
        transactionRepository.save(in);

        // The sender sees their own TRANSFER_OUT row, with the receiver's number masked.
        return new TransactionResponse(out.getReferenceId(), "TRANSFER_OUT", amount, senderNewBalance,
                AccountMasker.mask(receiver.getAccountNumber()), "SUCCESS", remark, LocalDateTime.now());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------
    private BigDecimal checkAmount(BigDecimal raw) {
        BigDecimal amount = raw.setScale(2); // validation already guarantees at most 2 decimals
        if (amount.compareTo(limits.getMinAmount()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Minimum amount is " + limits.getMinAmount());
        }
        if (amount.compareTo(limits.getMaxTransfer()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Maximum per transfer is " + limits.getMaxTransfer());
        }
        return amount;
    }

    private Account lockById(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    /** BR-4: money already sent today + this transfer must not go above the daily limit. */
    private void checkDailyLimit(Account sender, BigDecimal amount) {
        LocalDate today = LocalDate.now();
        BigDecimal alreadySent = transactionRepository.sumTransferOut(
                sender.getId(), today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        if (alreadySent.add(amount).compareTo(limits.getDailyTransfer()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Daily transfer limit of " + limits.getDailyTransfer() + " would be exceeded. "
                            + "Already sent today: " + alreadySent);
        }
    }

    private Transaction buildRow(Account account, String type, BigDecimal amount, BigDecimal balanceAfter,
                                 Long relatedAccountId, String remark) {
        Transaction t = new Transaction();
        t.setReferenceId(UUID.randomUUID().toString()); // reference_id is UNIQUE, so each row gets its own
        t.setAccountId(account.getId());
        t.setType(type);
        t.setAmount(amount);
        t.setBalanceAfter(balanceAfter);
        t.setRelatedAccountId(relatedAccountId);
        t.setStatus("SUCCESS");
        t.setRemark(remark);
        return t;
    }
}
