package com.deepu.securebank.integration;

import com.deepu.securebank.dto.LoginRequest;
import com.deepu.securebank.dto.RegisterRequest;
import com.deepu.securebank.exception.ApiException;
import com.deepu.securebank.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INTEGRATION test (real MySQL, test database only).
 * 20 wrong passwords for the SAME user arrive at the same moment.
 * Because login locks the user row, they run one after the other:
 *   attempt 1 and 2 -> LOGIN_FAILED, attempt 3 -> ACCOUNT_LOCKED, the other 17 -> LOGIN_BLOCKED.
 * Before Sprint 5, parallel attempts could read the same counter and beat the lockout.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthParallelLoginIntegrationTest {

    private static final String EMAIL = "parallel@test.com";

    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepare() throws Exception {
        TestDatabase.cleanOrRefuse(jdbcTemplate);
        RegisterRequest r = new RegisterRequest();
        r.setFullName("Parallel Test");
        r.setEmail(EMAIL);
        r.setPhone("9876543210");
        r.setDateOfBirth(LocalDate.of(2000, 1, 1));
        r.setPassword("Right1234");
        r.setAccountType("SAVINGS");
        authService.register(r);
    }

    private int auditCount(String action) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ?", Integer.class, action);
    }

    @Test
    void twentyParallelWrongPasswords_stillLockAfterExactlyThree() throws Exception {
        int n = 20;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger unauthorized = new AtomicInteger();
        AtomicInteger locked = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                LoginRequest l = new LoginRequest();
                l.setEmail(EMAIL);
                l.setPassword("Wrong" + Thread.currentThread().getId());
                try {
                    authService.login(l);
                    unexpected.incrementAndGet();               // a wrong password must never log in
                } catch (ApiException e) {
                    if (e.getStatus() == HttpStatus.UNAUTHORIZED) unauthorized.incrementAndGet();
                    else if (e.getStatus() == HttpStatus.LOCKED) locked.incrementAndGet();
                    else unexpected.incrementAndGet();
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                    e.printStackTrace();
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get(90, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(0, unexpected.get());
        assertEquals(2, unauthorized.get(), "Only the first 2 wrong passwords get 401");
        assertEquals(18, locked.get(), "The 3rd locks, and the other 17 are blocked");
        assertEquals(2, auditCount("LOGIN_FAILED"));
        assertEquals(1, auditCount("ACCOUNT_LOCKED"));
        assertEquals(17, auditCount("LOGIN_BLOCKED"));
        Integer lockedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? AND locked_until IS NOT NULL", Integer.class, EMAIL);
        assertTrue(lockedRows != null && lockedRows == 1, "The user must be locked in the database");
    }
}
