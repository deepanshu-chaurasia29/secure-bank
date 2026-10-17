package com.deepu.securebank.controller;

import com.deepu.securebank.dto.TransactionResponse;
import com.deepu.securebank.dto.TransferRequest;
import com.deepu.securebank.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "4. Transfer", description = "Send money to another account")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    // POST /api/v1/transactions/transfer
    // The sender is the logged-in user (from the JWT). The body only says WHO receives and HOW MUCH.
    @Operation(summary = "Transfer money to another account (all or nothing, daily limit applies)")
    @PostMapping("/transfer")
    public TransactionResponse transfer(@AuthenticationPrincipal Long userId,
                                        @Valid @RequestBody TransferRequest request) {
        return transferService.transfer(userId, request);
    }
}
