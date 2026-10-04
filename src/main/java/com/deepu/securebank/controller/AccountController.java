package com.deepu.securebank.controller;

import com.deepu.securebank.dto.AccountResponse;
import com.deepu.securebank.service.AccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // The user id comes from the verified JWT (set by JwtFilter), NOT from the request.
    // So a customer can only ever see their own account (FR-S2).
    @GetMapping("/me")
    public AccountResponse myAccount(@AuthenticationPrincipal Long userId) {
        return accountService.getMyAccount(userId);
    }
}
