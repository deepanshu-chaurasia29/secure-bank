package com.deepu.securebank.service;

import com.deepu.securebank.dto.AuthResponse;
import com.deepu.securebank.dto.ChangePasswordRequest;
import com.deepu.securebank.dto.LoginRequest;
import com.deepu.securebank.exception.ApiException;
import com.deepu.securebank.model.Account;
import com.deepu.securebank.model.User;
import com.deepu.securebank.repository.AccountRepository;
import com.deepu.securebank.repository.AuditLogRepository;
import com.deepu.securebank.repository.UserRepository;
import com.deepu.securebank.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for login and change-password RULES (fake repositories, no database).
 * These tests cannot prove that the failed-attempt counter is really SAVED to MySQL.
 * That is checked by AuthLockoutIntegrationTest, which uses a real database.
 */
class AuthServiceTest {

    private UserRepository userRepository;
    private AccountRepository accountRepository;
    private AuditLogRepository auditLogRepository;
    private PasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        accountRepository = mock(AccountRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtUtil = mock(JwtUtil.class);
        service = new AuthService(userRepository, accountRepository, auditLogRepository, passwordEncoder, jwtUtil);
    }

    private User user(String role, int failedAttempts, LocalDateTime lockedUntil) {
        User u = new User();
        u.setId(1L);
        u.setFullName("Test User");
        u.setEmail("test@test.com");
        u.setPasswordHash("OLDHASH");
        u.setRole(role);
        u.setFailedAttempts(failedAttempts);
        u.setLockedUntil(lockedUntil);
        return u;
    }

    private LoginRequest login(String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail("test@test.com");
        r.setPassword(password);
        return r;
    }

    private ChangePasswordRequest change(String oldPassword, String newPassword) {
        ChangePasswordRequest r = new ChangePasswordRequest();
        r.setOldPassword(oldPassword);
        r.setNewPassword(newPassword);
        return r;
    }

    // ---------------- change password ----------------
    @Test
    void changePassword_savesTheNewHash_andWritesAnAuditLine() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("CUSTOMER", 0, null)));
        when(passwordEncoder.matches("OldPass1", "OLDHASH")).thenReturn(true);
        when(passwordEncoder.matches("NewPass1", "OLDHASH")).thenReturn(false);
        when(passwordEncoder.encode("NewPass1")).thenReturn("NEWHASH");

        service.changePassword(1L, change("OldPass1", "NewPass1"));

        verify(userRepository).updatePassword(1L, "NEWHASH");
        verify(auditLogRepository).log(eq(1L), eq("PASSWORD_CHANGED"), anyString());
    }

    @Test
    void changePassword_wrongOldPassword_isRejectedWith400_notWith401() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("CUSTOMER", 0, null)));
        when(passwordEncoder.matches("Wrong123", "OLDHASH")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> service.changePassword(1L, change("Wrong123", "NewPass1")));

        // 400 on purpose: the dashboard logs the user out on 401
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userRepository, never()).updatePassword(any(), any());
    }

    @Test
    void changePassword_newPasswordSameAsOld_isRejected() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("CUSTOMER", 0, null)));
        when(passwordEncoder.matches("SamePass1", "OLDHASH")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> service.changePassword(1L, change("SamePass1", "SamePass1")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userRepository, never()).updatePassword(any(), any());
    }

    // ---------------- login ----------------
    @Test
    void login_admin_withoutAnAccount_works() {
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user("ADMIN", 0, null)));
        when(passwordEncoder.matches("AdminPass1", "OLDHASH")).thenReturn(true);
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(jwtUtil.generateToken(1L, "ADMIN")).thenReturn("token-admin");

        AuthResponse response = service.login(login("AdminPass1"));

        assertEquals("ADMIN", response.getRole());
        assertEquals("token-admin", response.getToken());
        assertNull(response.getAccountNumber());
    }

    @Test
    void login_customer_withoutAnAccount_isAServerError() {
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user("CUSTOMER", 0, null)));
        when(passwordEncoder.matches("Pass1234", "OLDHASH")).thenReturn(true);
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.login(login("Pass1234")));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    }

    @Test
    void login_success_resetsTheCounter_andWritesAnAuditLine() {
        Account account = new Account();
        account.setAccountNumber("111111111111");
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user("CUSTOMER", 2, null)));
        when(passwordEncoder.matches("Pass1234", "OLDHASH")).thenReturn(true);
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account));
        when(jwtUtil.generateToken(1L, "CUSTOMER")).thenReturn("token");

        AuthResponse response = service.login(login("Pass1234"));

        assertEquals("111111111111", response.getAccountNumber());
        verify(userRepository).resetFailedAttempts(1L);
        verify(auditLogRepository).log(eq(1L), eq("LOGIN_SUCCESS"), anyString());
    }

    @Test
    void login_wrongPassword_countsTheAttempt_andWritesAnAuditLine() {
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user("CUSTOMER", 0, null)));
        when(passwordEncoder.matches("Wrong123", "OLDHASH")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> service.login(login("Wrong123")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(userRepository).incrementFailedAttempts(1L, 1);
        verify(auditLogRepository).log(eq(1L), eq("LOGIN_FAILED"), contains("attempt 1 of 3"));
    }

    @Test
    void login_thirdWrongPassword_locksTheUser() {
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user("CUSTOMER", 2, null)));
        when(passwordEncoder.matches("Wrong123", "OLDHASH")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> service.login(login("Wrong123")));

        assertEquals(HttpStatus.LOCKED, ex.getStatus());
        verify(userRepository).lockUser(eq(1L), any(LocalDateTime.class));
        verify(auditLogRepository).log(eq(1L), eq("ACCOUNT_LOCKED"), anyString());
    }

    @Test
    void login_whileLocked_isBlocked_evenWithTheRightPassword() {
        when(userRepository.findByEmail("test@test.com"))
                .thenReturn(Optional.of(user("CUSTOMER", 0, LocalDateTime.now().plusMinutes(10))));

        ApiException ex = assertThrows(ApiException.class, () -> service.login(login("Pass1234")));

        assertEquals(HttpStatus.LOCKED, ex.getStatus());
        verify(passwordEncoder, never()).matches(any(), any()); // the password is not even checked
        verify(auditLogRepository).log(eq(1L), eq("LOGIN_BLOCKED"), anyString());
    }
}
