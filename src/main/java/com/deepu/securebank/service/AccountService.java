package com.deepu.securebank.service;

import com.deepu.securebank.dto.AccountResponse;
import com.deepu.securebank.exception.ApiException;
import com.deepu.securebank.model.Account;
import com.deepu.securebank.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** FR-B2: the logged-in customer sees their own account. userId comes from the JWT, never from the URL. */
    public AccountResponse getMyAccount(Long userId) {
        Account a = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        return new AccountResponse(a.getAccountNumber(), a.getAccountType(), a.getStatus(),
                a.getBalance(), a.getCreatedAt());
    }
}
