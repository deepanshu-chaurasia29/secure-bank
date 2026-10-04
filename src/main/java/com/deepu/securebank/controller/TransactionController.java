package com.deepu.securebank.controller;

import com.deepu.securebank.dto.AmountRequest;
import com.deepu.securebank.dto.PageResponse;
import com.deepu.securebank.dto.TransactionResponse;
import com.deepu.securebank.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/deposit")
    public TransactionResponse deposit(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody AmountRequest request) {
        return transactionService.deposit(userId, request);
    }

    @PostMapping("/withdraw")
    public TransactionResponse withdraw(@AuthenticationPrincipal Long userId,
                                        @Valid @RequestBody AmountRequest request) {
        return transactionService.withdraw(userId, request);
    }

    // Example: GET /api/v1/transactions/me?page=0&size=10&type=DEPOSIT&from=2026-10-01&to=2026-10-31
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
