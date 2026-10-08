package com.deepu.securebank.service;

import com.deepu.securebank.dto.AdminAccountResponse;
import com.deepu.securebank.dto.PageResponse;
import com.deepu.securebank.exception.ApiException;
import com.deepu.securebank.model.User;
import com.deepu.securebank.repository.AccountRepository;
import com.deepu.securebank.repository.AdminRepository;
import com.deepu.securebank.repository.AuditLogRepository;
import com.deepu.securebank.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;

/** Unit tests for the admin rules. No database: every repository is fake (mocked). */
class AdminServiceTest {

    private static final Long ADMIN_ID = 99L;

    private AdminRepository adminRepository;
    private AccountRepository accountRepository;
    private UserRepository userRepository;
    private AuditLogRepository auditLogRepository;
    private TransactionService transactionService;
    private AdminService service;

    @BeforeEach
    void setUp() {
        adminRepository = mock(AdminRepository.class);
        accountRepository = mock(AccountRepository.class);
        userRepository = mock(UserRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        transactionService = mock(TransactionService.class);
        service = new AdminService(adminRepository, accountRepository, userRepository,
                auditLogRepository, transactionService);
    }

    private AdminAccountResponse account(String status) {
        return new AdminAccountResponse(5L, "111111111111", "SAVINGS", status, new BigDecimal("100.00"),
                LocalDateTime.now(), 7L, "Test User", "test@test.com", "9876543210", false, null);
    }

    // ---------------- search ----------------
    @Test
    void search_trimsText_andBuildsThePage() {
        when(adminRepository.search("deepu", 0, 10)).thenReturn(List.of(account("ACTIVE")));
        when(adminRepository.count("deepu")).thenReturn(25L);

        PageResponse<AdminAccountResponse> page = service.searchAccounts("  deepu  ", 0, 10);

        assertEquals(1, page.getContent().size());
        assertEquals(25, page.getTotalElements());
        assertEquals(3, page.getTotalPages()); // 25 rows, 10 per page
    }

    @Test
    void search_blankTextMeansNoFilter() {
        when(adminRepository.search(isNull(), anyInt(), anyInt())).thenReturn(List.of());
        when(adminRepository.count(isNull())).thenReturn(0L);

        service.searchAccounts("   ", 0, 10);

        verify(adminRepository).search(isNull(), eq(0), eq(10));
    }

    @Test
    void search_rejectsBadInput() {
        assertThrows(ApiException.class, () -> service.searchAccounts(null, -1, 10));
        assertThrows(ApiException.class, () -> service.searchAccounts(null, 0, 0));
        assertThrows(ApiException.class, () -> service.searchAccounts(null, 0, 51));
        assertThrows(ApiException.class, () -> service.searchAccounts("x".repeat(101), 0, 10));
    }

    // ---------------- freeze / unfreeze ----------------
    @Test
    void freeze_changesStatus_andWritesAnAuditLine() {
        when(adminRepository.findByAccountId(5L))
                .thenReturn(Optional.of(account("ACTIVE")), Optional.of(account("FROZEN")));

        AdminAccountResponse result = service.freezeAccount(ADMIN_ID, 5L);

        verify(accountRepository).updateStatus(5L, "FROZEN");
        verify(auditLogRepository).log(eq(ADMIN_ID), eq("FREEZE_ACCOUNT"), contains("111111111111"));
        assertEquals("FROZEN", result.getStatus());
    }

    @Test
    void freeze_aFrozenAccount_isRejected() {
        when(adminRepository.findByAccountId(5L)).thenReturn(Optional.of(account("FROZEN")));

        ApiException ex = assertThrows(ApiException.class, () -> service.freezeAccount(ADMIN_ID, 5L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(accountRepository, never()).updateStatus(any(), any());
        verify(auditLogRepository, never()).log(any(), any(), any());
    }

    @Test
    void freeze_unknownAccount_isNotFound() {
        when(adminRepository.findByAccountId(5L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.freezeAccount(ADMIN_ID, 5L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void unfreeze_changesStatusBackToActive_andWritesAnAuditLine() {
        when(adminRepository.findByAccountId(5L))
                .thenReturn(Optional.of(account("FROZEN")), Optional.of(account("ACTIVE")));

        AdminAccountResponse result = service.unfreezeAccount(ADMIN_ID, 5L);

        verify(accountRepository).updateStatus(5L, "ACTIVE");
        verify(auditLogRepository).log(eq(ADMIN_ID), eq("UNFREEZE_ACCOUNT"), contains("111111111111"));
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void unfreeze_anActiveAccount_isRejected() {
        when(adminRepository.findByAccountId(5L)).thenReturn(Optional.of(account("ACTIVE")));

        ApiException ex = assertThrows(ApiException.class, () -> service.unfreezeAccount(ADMIN_ID, 5L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(accountRepository, never()).updateStatus(any(), any());
    }

    // ---------------- unlock ----------------
    @Test
    void unlockUser_resetsTheCounter_andWritesAnAuditLine() {
        User u = new User();
        u.setId(7L);
        u.setEmail("test@test.com");
        u.setDateOfBirth(LocalDate.of(2000, 1, 1));
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        service.unlockUser(ADMIN_ID, 7L);

        verify(userRepository).resetFailedAttempts(7L);
        verify(auditLogRepository).log(eq(ADMIN_ID), eq("UNLOCK_USER"), contains("test@test.com"));
    }

    @Test
    void unlockUser_unknownUser_isNotFound() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.unlockUser(ADMIN_ID, 7L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(userRepository, never()).resetFailedAttempts(any());
    }

    // ---------------- history ----------------
    @Test
    void accountTransactions_usesTheSharedHistoryLogic() {
        service.accountTransactions(5L, 0, 10, "DEPOSIT", null, null);

        verify(transactionService).historyByAccountId(5L, 0, 10, "DEPOSIT", null, null);
    }
}
