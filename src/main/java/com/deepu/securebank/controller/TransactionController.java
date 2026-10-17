package com.deepu.securebank.controller;

import com.deepu.securebank.dto.AmountRequest;
import com.deepu.securebank.dto.PageResponse;
import com.deepu.securebank.dto.TransactionResponse;
import com.deepu.securebank.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "3. Deposit, withdraw, history", description = "Money movement on the logged-in customer's own account")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "Deposit money (max per deposit applies)")
    @PostMapping("/deposit")
    public TransactionResponse deposit(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody AmountRequest request) {
        return transactionService.deposit(userId, request);
    }

    @Operation(summary = "Withdraw money (needs enough balance)")
    @PostMapping("/withdraw")
    public TransactionResponse withdraw(@AuthenticationPrincipal Long userId,
                                        @Valid @RequestBody AmountRequest request) {
        return transactionService.withdraw(userId, request);
    }

    // Example: GET /api/v1/transactions/me?page=0&size=10&type=DEPOSIT&from=2026-10-01&to=2026-10-31
    @Operation(summary = "My transaction history, newest first, with filters and paging")
    @GetMapping("/me")
    public PageResponse<TransactionResponse> myHistory(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return transactionService.history(userId, page, size, type, from, to);
    }
}
