package com.deepu.securebank.service;

import com.deepu.securebank.dto.AdminAccountResponse;
import com.deepu.securebank.dto.PageResponse;
import com.deepu.securebank.dto.TransactionResponse;
import com.deepu.securebank.exception.ApiException;
import com.deepu.securebank.model.User;
import com.deepu.securebank.repository.AccountRepository;
import com.deepu.securebank.repository.AdminRepository;
import com.deepu.securebank.repository.AuditLogRepository;
import com.deepu.securebank.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything an ADMIN can do (FR-E2 to FR-E5, FR-E8).
 * An admin can look and block. An admin can NOT move money and can NOT see passwords.
 * Every change is written to the audit log with the admin's user id.
 */
@Service
public class AdminService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SEARCH_LENGTH = 100;

    private final AdminRepository adminRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final TransactionService transactionService;

    public AdminService(AdminRepository adminRepository,
                        AccountRepository accountRepository,
                        UserRepository userRepository,
                        AuditLogRepository auditLogRepository,
                        TransactionService transactionService) {
        this.adminRepository = adminRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.transactionService = transactionService;
    }

    // FR-E2: list and search accounts, with pages
    public PageResponse<AdminAccountResponse> searchAccounts(String search, int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page cannot be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String cleanSearch = (search == null || search.isBlank()) ? null : search.trim();
        if (cleanSearch != null && cleanSearch.length() > MAX_SEARCH_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "search text is too long");
        }

        List<AdminAccountResponse> rows = adminRepository.search(cleanSearch, page, size);
        long total = adminRepository.count(cleanSearch);
        return new PageResponse<>(rows, page, size, total);
    }

    // FR-E3: freeze
    @Transactional
    public AdminAccountResponse freezeAccount(Long adminId, Long accountId) {
        AdminAccountResponse account = getAccountOrThrow(accountId);
        if ("FROZEN".equals(account.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Account is already frozen");
        }
        accountRepository.updateStatus(accountId, "FROZEN");
        auditLogRepository.log(adminId, "FREEZE_ACCOUNT", "account " + account.getAccountNumber());
        return getAccountOrThrow(accountId);
    }

    // FR-E3: unfreeze
    @Transactional
    public AdminAccountResponse unfreezeAccount(Long adminId, Long accountId) {
        AdminAccountResponse account = getAccountOrThrow(accountId);
        if (!"FROZEN".equals(account.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Account is not frozen");
        }
        accountRepository.updateStatus(accountId, "ACTIVE");
        auditLogRepository.log(adminId, "UNFREEZE_ACCOUNT", "account " + account.getAccountNumber());
        return getAccountOrThrow(accountId);
    }

    // FR-E4: history of any account
    public PageResponse<TransactionResponse> accountTransactions(Long accountId, int page, int size,
                                                                 String type, LocalDate from, LocalDate to) {
        return transactionService.historyByAccountId(accountId, page, size, type, from, to);
    }

    // FR-E5: unlock a user who was locked out after wrong passwords
    @Transactional
    public void unlockUser(Long adminId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        userRepository.resetFailedAttempts(userId);
        auditLogRepository.log(adminId, "UNLOCK_USER", "user " + user.getEmail());
    }

    private AdminAccountResponse getAccountOrThrow(Long accountId) {
        return adminRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }
}
