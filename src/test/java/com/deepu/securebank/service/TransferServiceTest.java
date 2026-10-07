package com.deepu.securebank.service;

import com.deepu.securebank.config.BankLimits;
import com.deepu.securebank.dto.TransactionResponse;
import com.deepu.securebank.dto.TransferRequest;
import com.deepu.securebank.exception.ApiException;
import com.deepu.securebank.model.Account;
import com.deepu.securebank.model.Transaction;
import com.deepu.securebank.repository.AccountRepository;
import com.deepu.securebank.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Unit tests for the transfer RULES. No database: the repositories are fake (mocked). */
class TransferServiceTest {

    private static final String SENDER_NUMBER = "111111111111";
    private static final String RECEIVER_NUMBER = "222222222222";

    private AccountRepository accountRepository;
    private TransactionRepository transactionRepository;
    private TransferService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        BankLimits limits = new BankLimits(new BigDecimal("1.00"), new BigDecimal("100000.00"),
                new BigDecimal("50000.00"), new BigDecimal("100000.00"), new BigDecimal("200000.00"));
        service = new TransferService(accountRepository, transactionRepository, limits);
    }

    private Account account(long id, String number, String balance, String status) {
        Account a = new Account();
        a.setId(id);
        a.setAccountNumber(number);
        a.setBalance(new BigDecimal(balance));
        a.setStatus(status);
        return a;
    }

    private TransferRequest request(String toNumber, String amount) {
        TransferRequest r = new TransferRequest();
        r.setToAccountNumber(toNumber);
        r.setAmount(new BigDecimal(amount));
        r.setRemark("rent");
        return r;
    }

    /** Sender is user 1 with account id senderId; receiver account has id receiverId. */
    private void givenAccounts(long senderId, String senderBalance, String senderStatus,
                               long receiverId, String receiverBalance, String receiverStatus) {
        Account sender = account(senderId, SENDER_NUMBER, senderBalance, senderStatus);
        Account receiver = account(receiverId, RECEIVER_NUMBER, receiverBalance, receiverStatus);
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(sender));
        when(accountRepository.findByAccountNumber(RECEIVER_NUMBER)).thenReturn(Optional.of(receiver));
        when(accountRepository.findByIdForUpdate(senderId)).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate(receiverId)).thenReturn(Optional.of(receiver));
        when(transactionRepository.sumTransferOut(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(BigDecimal.ZERO);
    }

    @Test
    void transfer_movesMoney_andWritesTwoHistoryRows() {
        givenAccounts(10, "1000.00", "ACTIVE", 20, "50.00", "ACTIVE");

        TransactionResponse response = service.transfer(1L, request(RECEIVER_NUMBER, "300.00"));

        verify(accountRepository).updateBalance(10L, new BigDecimal("700.00"));
        verify(accountRepository).updateBalance(20L, new BigDecimal("350.00"));
        assertEquals(new BigDecimal("700.00"), response.getBalanceAfter());
        assertEquals("XXXXXXXX2222", response.getRelatedAccount());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        List<Transaction> saved = captor.getAllValues();
        assertEquals("TRANSFER_OUT", saved.get(0).getType());
        assertEquals(10L, saved.get(0).getAccountId());
        assertEquals(20L, saved.get(0).getRelatedAccountId());
        assertEquals("TRANSFER_IN", saved.get(1).getType());
        assertEquals(20L, saved.get(1).getAccountId());
        assertEquals(10L, saved.get(1).getRelatedAccountId());
        assertNotEquals(saved.get(0).getReferenceId(), saved.get(1).getReferenceId());
    }

    @Test
    void transfer_locksTheLowerAccountIdFirst_evenWhenSenderHasTheHigherId() {
        // sender id = 9, receiver id = 5  ->  lock order must be 5 and then 9
        givenAccounts(9, "1000.00", "ACTIVE", 5, "0.00", "ACTIVE");

        service.transfer(1L, request(RECEIVER_NUMBER, "100.00"));

        InOrder inOrder = inOrder(accountRepository);
        inOrder.verify(accountRepository).findByIdForUpdate(5L);
        inOrder.verify(accountRepository).findByIdForUpdate(9L);
        // and the money still goes the right way
        verify(accountRepository).updateBalance(9L, new BigDecimal("900.00"));
        verify(accountRepository).updateBalance(5L, new BigDecimal("100.00"));
    }

    @Test
    void transfer_toOwnAccount_isRejected_beforeAnyLock() {
        Account me = account(10, SENDER_NUMBER, "1000.00", "ACTIVE");
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(me));
        when(accountRepository.findByAccountNumber(SENDER_NUMBER)).thenReturn(Optional.of(me));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.transfer(1L, request(SENDER_NUMBER, "10.00")));

        assertTrue(ex.getMessage().contains("own account"));
        verify(accountRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void transfer_toUnknownAccount_isRejected() {
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account(10, SENDER_NUMBER, "1000.00", "ACTIVE")));
        when(accountRepository.findByAccountNumber("999999999999")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.transfer(1L, request("999999999999", "10.00")));

        assertEquals("Receiver account not found", ex.getMessage());
    }

    @Test
    void transfer_moreThanBalance_isRejected_andNothingChanges() {
        givenAccounts(10, "100.00", "ACTIVE", 20, "0.00", "ACTIVE");

        ApiException ex = assertThrows(ApiException.class,
                () -> service.transfer(1L, request(RECEIVER_NUMBER, "100.01")));

        assertEquals("Insufficient balance", ex.getMessage());
        verify(accountRepository, never()).updateBalance(anyLong(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_exactBalance_isAllowed() {
        givenAccounts(10, "100.00", "ACTIVE", 20, "0.00", "ACTIVE");

        TransactionResponse response = service.transfer(1L, request(RECEIVER_NUMBER, "100.00"));

        assertEquals(new BigDecimal("0.00"), response.getBalanceAfter());
    }

    @Test
    void transfer_fromFrozenAccount_isRejected() {
        givenAccounts(10, "1000.00", "FROZEN", 20, "0.00", "ACTIVE");

        assertThrows(ApiException.class, () -> service.transfer(1L, request(RECEIVER_NUMBER, "10.00")));
        verify(accountRepository, never()).updateBalance(anyLong(), any());
    }

    @Test
    void transfer_toFrozenAccount_isRejected() {
        givenAccounts(10, "1000.00", "ACTIVE", 20, "0.00", "FROZEN");

        assertThrows(ApiException.class, () -> service.transfer(1L, request(RECEIVER_NUMBER, "10.00")));
        verify(accountRepository, never()).updateBalance(anyLong(), any());
    }

    @Test
    void transfer_overTheSingleTransferLimit_isRejected_beforeAnyLookup() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.transfer(1L, request(RECEIVER_NUMBER, "100000.01")));

        assertTrue(ex.getMessage().startsWith("Maximum per transfer"));
        verify(accountRepository, never()).findByUserId(anyLong());
    }

    @Test
    void transfer_thatBreaksTheDailyLimit_isRejected() {
        givenAccounts(10, "500000.00", "ACTIVE", 20, "0.00", "ACTIVE");
        when(transactionRepository.sumTransferOut(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("150000.00"));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.transfer(1L, request(RECEIVER_NUMBER, "50000.01")));

        assertTrue(ex.getMessage().startsWith("Daily transfer limit"));
        verify(accountRepository, never()).updateBalance(anyLong(), any());
    }

    @Test
    void transfer_thatReachesExactlyTheDailyLimit_isAllowed() {
        givenAccounts(10, "500000.00", "ACTIVE", 20, "0.00", "ACTIVE");
        when(transactionRepository.sumTransferOut(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("150000.00"));

        TransactionResponse response = service.transfer(1L, request(RECEIVER_NUMBER, "50000.00"));

        assertEquals(new BigDecimal("450000.00"), response.getBalanceAfter());
    }
}
