package com.deepu.securebank.integration;

import com.deepu.securebank.dto.AuthResponse;
import com.deepu.securebank.dto.ChangePasswordRequest;
import com.deepu.securebank.dto.LoginRequest;
import com.deepu.securebank.dto.RegisterRequest;
import com.deepu.securebank.exception.ApiException;
import com.deepu.securebank.repository.UserRepository;
import com.deepu.securebank.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * INTEGRATION test with a REAL MySQL database (securebank_test).
 * It proves that the failed-login counter and the lock are really SAVED.
 * (Before Sprint 4 they were silently undone, because Spring rolled the transaction back
 * when login threw its error. Fixed with noRollbackFor = ApiException.class.)
 *
 * Needs: MySQL running and DB_PASSWORD set. It deletes all rows of the test database only.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthLockoutIntegrationTest {

    private static final String EMAIL = "lock@test.com";
    private static final String PASSWORD = "Right1234";

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTestDatabase() throws Exception {
        TestDatabase.cleanOrRefuse(jdbcTemplate);
        RegisterRequest r = new RegisterRequest();
        r.setFullName("Lock Test");
        r.setEmail(EMAIL);
        r.setPhone("9876543210");
        r.setDateOfBirth(LocalDate.of(2000, 1, 1));
        r.setPassword(PASSWORD);
        r.setAccountType("SAVINGS");
        authService.register(r);
    }

    private LoginRequest login(String password) {
        LoginRequest l = new LoginRequest();
        l.setEmail(EMAIL);
        l.setPassword(password);
        return l;
    }

    private int failedAttempts() {
        return jdbcTemplate.queryForObject("SELECT failed_attempts FROM users WHERE email = ?", Integer.class, EMAIL);
    }

    private boolean isLockedInDb() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? AND locked_until IS NOT NULL", Integer.class, EMAIL);
        return n != null && n == 1;
    }

    private int auditCount(String action) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ?", Integer.class, action);
    }

    @Test
    void wrongPasswords_arePersisted_andTheThirdOneLocksTheUser() {
        // attempt 1 and 2: rejected, and the counter is SAVED in the database
        ApiException first = assertThrows(ApiException.class, () -> authService.login(login("Wrong0001")));
        assertEquals(HttpStatus.UNAUTHORIZED, first.getStatus());
        assertEquals(1, failedAttempts(), "The counter must survive the error");

        assertThrows(ApiException.class, () -> authService.login(login("Wrong0002")));
        assertEquals(2, failedAttempts());

        // attempt 3: locked
        ApiException third = assertThrows(ApiException.class, () -> authService.login(login("Wrong0003")));
        assertEquals(HttpStatus.LOCKED, third.getStatus());
        assertTrue(isLockedInDb(), "locked_until must be saved");

        // while locked, even the RIGHT password is refused
        ApiException blocked = assertThrows(ApiException.class, () -> authService.login(login(PASSWORD)));
        assertEquals(HttpStatus.LOCKED, blocked.getStatus());

        // what the admin's "Unlock user" button does
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, EMAIL);
        userRepository.resetFailedAttempts(userId);

        AuthResponse ok = authService.login(login(PASSWORD));
        assertNotNull(ok.getToken());
        assertEquals(0, failedAttempts());

        // the audit log kept every step
        assertEquals(2, auditCount("LOGIN_FAILED"));
        assertEquals(1, auditCount("ACCOUNT_LOCKED"));
        assertEquals(1, auditCount("LOGIN_BLOCKED"));
        assertEquals(1, auditCount("LOGIN_SUCCESS"));
    }

    @Test
    void changePassword_isSaved_theNewOneWorks_theOldOneDoesNot() {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, EMAIL);

        ChangePasswordRequest change = new ChangePasswordRequest();
        change.setOldPassword(PASSWORD);
        change.setNewPassword("Brandnew99");
        authService.changePassword(userId, change);

        assertNotNull(authService.login(login("Brandnew99")).getToken());
        ApiException ex = assertThrows(ApiException.class, () -> authService.login(login(PASSWORD)));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals(1, auditCount("PASSWORD_CHANGED"));
    }
}
