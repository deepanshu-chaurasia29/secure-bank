package com.deepu.securebank.service;

import com.deepu.securebank.config.BankLimits;
import com.deepu.securebank.dto.AmountRequest;
import com.deepu.securebank.dto.TransactionResponse;
import com.deepu.securebank.exception.ApiException;
import com.deepu.securebank.model.Account;
import com.deepu.securebank.model.Transaction;
import com.deepu.securebank.repository.AccountRepository;
import com.deepu.securebank.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests: no database, no Spring. The repositories are fake (mocked),
 * so we test only the business rules inside TransactionService.
 */
class TransactionServiceTest {

    private AccountRepository accountRepository;
    private TransactionRepository transactionRepository;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        BankLimits limits = new BankLimits(new BigDecimal("1.00"),
                new BigDecimal("100000.00"), new BigDecimal("50000.00"));
        service = new TransactionService(accountRepository, transactionRepository, limits);
    }

    private Account account(String balance, String status) {
        Account a = new Account();
        a.setId(10L);
        a.setUserId(1L);
        a.setAccountNumber("123456789012");
        a.setBalance(new BigDecimal(balance));
        a.setStatus(status);
        return a;
    }

    private AmountRequest request(String amount) {
        AmountRequest r = new AmountRequest();
        r.setAmount(new BigDecimal(amount));
        return r;
    }

    @Test
    void deposit_increasesBalance_andSavesTransaction() {
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account("100.00", "ACTIVE")));

        TransactionResponse response = service.deposit(1L, request("250.50"));

        assertEquals(new BigDecimal("350.50"), response.getBalanceAfter());
        verify(accountRepository).updateBalance(10L, new BigDecimal("350.50"));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals("DEPOSIT", captor.getValue().getType());
        assertEquals(new BigDecimal("250.50"), captor.getValue().getAmount());
    }

    @Test
    void withdraw_decreasesBalance() {
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account("500.00", "ACTIVE")));

        TransactionResponse response = service.withdraw(1L, request("200.00"));

        assertEquals(new BigDecimal("300.00"), response.getBalanceAfter());
        verify(accountRepository).updateBalance(10L, new BigDecimal("300.00"));
    }

    @Test
    void withdraw_moreThanBalance_isRejected_andNothingIsChanged() {
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account("100.00", "ACTIVE")));

        ApiException ex = assertThrows(ApiException.class, () -> service.withdraw(1L, request("100.01")));

        assertEquals("Insufficient balance", ex.getMessage());
        verify(accountRepository, never()).updateBalance(anyLong(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdraw_exactBalance_isAllowed_balanceBecomesZero() {
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account("100.00", "ACTIVE")));

        TransactionResponse response = service.withdraw(1L, request("100.00"));

        assertEquals(new BigDecimal("0.00"), response.getBalanceAfter());
    }

    @Test
    void deposit_overLimit_isRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> service.deposit(1L, request("100000.01")));

        assertTrue(ex.getMessage().startsWith("Maximum per deposit"));
        verify(accountRepository, never()).findByUserIdForUpdate(anyLong());
    }

    @Test
    void withdraw_overLimit_isRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> service.withdraw(1L, request("50000.01")));

        assertTrue(ex.getMessage().startsWith("Maximum per withdrawal"));
    }

    @Test
    void frozenAccount_cannotDeposit_orWithdraw() {
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account("500.00", "FROZEN")));

        assertThrows(ApiException.class, () -> service.deposit(1L, request("10.00")));
        assertThrows(ApiException.class, () -> service.withdraw(1L, request("10.00")));
        verify(accountRepository, never()).updateBalance(anyLong(), any());
    }

    @Test
    void history_rejectsBadInput() {
        assertThrows(ApiException.class, () -> service.history(1L, -1, 10, null, null, null));
        assertThrows(ApiException.class, () -> service.history(1L, 0, 0, null, null, null));
        assertThrows(ApiException.class, () -> service.history(1L, 0, 51, null, null, null));
        assertThrows(ApiException.class, () -> service.history(1L, 0, 10, "GIFT", null, null));
        assertThrows(ApiException.class, () -> service.history(1L, 0, 10, null,
                java.time.LocalDate.of(2026, 10, 10), java.time.LocalDate.of(2026, 10, 1)));
    }
}
