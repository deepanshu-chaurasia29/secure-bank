package com.deepu.securebank.controller;

import com.deepu.securebank.dto.AdminAccountResponse;
import com.deepu.securebank.dto.MessageResponse;
import com.deepu.securebank.dto.PageResponse;
import com.deepu.securebank.dto.TransactionResponse;
import com.deepu.securebank.service.AdminService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Every URL under /api/v1/admin/** is already limited to the ADMIN role in SecurityConfig
 * (.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")), so a customer gets 403 here.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // GET /api/v1/admin/accounts?search=deepu&page=0&size=10
    @GetMapping("/accounts")
    public PageResponse<AdminAccountResponse> accounts(@RequestParam(required = false) String search,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return adminService.searchAccounts(search, page, size);
    }

    @PutMapping("/accounts/{id}/freeze")
    public AdminAccountResponse freeze(@AuthenticationPrincipal Long adminId, @PathVariable Long id) {
        return adminService.freezeAccount(adminId, id);
    }

    @PutMapping("/accounts/{id}/unfreeze")
    public AdminAccountResponse unfreeze(@AuthenticationPrincipal Long adminId, @PathVariable Long id) {
        return adminService.unfreezeAccount(adminId, id);
    }

    @GetMapping("/accounts/{id}/transactions")
    public PageResponse<TransactionResponse> transactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return adminService.accountTransactions(id, page, size, type, from, to);
    }

    @PutMapping("/users/{id}/unlock")
    public MessageResponse unlock(@AuthenticationPrincipal Long adminId, @PathVariable Long id) {
        adminService.unlockUser(adminId, id);
        return new MessageResponse("User unlocked");
    }
}
