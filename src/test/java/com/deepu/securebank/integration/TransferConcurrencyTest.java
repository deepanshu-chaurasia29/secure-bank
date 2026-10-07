package com.deepu.securebank.integration;

import com.deepu.securebank.dto.TransferRequest;
import com.deepu.securebank.exception.ApiException;
import com.deepu.securebank.model.Account;
import com.deepu.securebank.model.User;
import com.deepu.securebank.repository.AccountRepository;
import com.deepu.securebank.repository.UserRepository;
import com.deepu.securebank.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INTEGRATION test: uses the real TransferService and a REAL MySQL database (securebank_test).
 * It proves that row locking works when many transfers run at the same time.
 *
 * Needs: MySQL running, and the DB_PASSWORD environment variable set (same as for the app).
 * WARNING: this test DELETES all rows of the test database. It refuses to run on any other database.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferConcurrencyTest {

    @Autowired private TransferService transferService;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** A small holder for one test customer. */
    private record Customer(Long userId, Long accountId, String accountNumber) {
    }

    /** How many tasks succeeded, were rejected by a business rule, or failed for an unexpected reason. */
    private record Counts(int ok, int rejected, int unexpected) {
    }

    @BeforeEach
    void cleanTestDatabase() throws Exception {
        // SAFETY: never delete data from a database that is not the test database.
        try (Connection c = jdbcTemplate.getDataSource().getConnection()) {
            String url = c.getMetaData().getURL();
            assertTrue(url.contains("securebank_test"),
                    "Refusing to run: this is not the test database. URL = " + url);
        }
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update("DELETE FROM users");
    }

    // ------------------------------------------------------------------
    // TEST 1: 50 people try to send Rs.100 from an account that has only Rs.1000
    // ------------------------------------------------------------------
    @Test
    void fiftyParallelTransfers_onlyAsManyAsTheBalanceAllows() throws Exception {
        Customer a = createCustomer("a@test.com", "111111111111", "1000.00");
        Customer b = createCustomer("b@test.com", "222222222222", "0.00");

        Counts counts = runParallel(50, i -> transferService.transfer(a.userId(), request(b.accountNumber(), "100.00")));

        assertEquals(0, counts.unexpected(), "No unexpected errors (like a MySQL deadlock) are allowed");
        assertEquals(10, counts.ok(), "Only 10 x Rs.100 fit into Rs.1000");
        assertEquals(40, counts.rejected(), "The other 40 must be rejected with 'Insufficient balance'");
        assertMoney("0.00", balanceOf(a));
        assertMoney("1000.00", balanceOf(b));
        // each successful transfer writes exactly 2 history rows (OUT + IN)
        assertEquals(20, countRows("transactions"));
    }

    // ------------------------------------------------------------------
    // TEST 2: A sends to B while B sends to A, at the same time (the classic deadlock situation)
    // ------------------------------------------------------------------
    @Test
    void oppositeTransfersAtTheSameTime_doNotDeadlock() throws Exception {
        Customer a = createCustomer("a@test.com", "111111111111", "1000.00");
        Customer b = createCustomer("b@test.com", "222222222222", "1000.00");

        Counts counts = runParallel(40, i -> {
            if (i % 2 == 0) {
                return transferService.transfer(a.userId(), request(b.accountNumber(), "10.00"));
            }
            return transferService.transfer(b.userId(), request(a.accountNumber(), "10.00"));
        });

        assertEquals(0, counts.unexpected(), "A deadlock would show up here as an unexpected error");
        assertEquals(40, counts.ok());
        assertMoney("1000.00", balanceOf(a)); // 20 x 10 out, 20 x 10 in
        assertMoney("1000.00", balanceOf(b));
        // total money in the bank never changes
        assertMoney("2000.00", balanceOf(a).add(balanceOf(b)));
    }

    // ------------------------------------------------------------------
    // TEST 3: the DAILY limit (Rs.2,00,000) must hold even when requests arrive together
    // ------------------------------------------------------------------
    @Test
    void parallelTransfers_cannotBreakTheDailyLimit() throws Exception {
        Customer a = createCustomer("a@test.com", "111111111111", "1000000.00");
        Customer b = createCustomer("b@test.com", "222222222222", "0.00");

        // 5 x Rs.50,000 = Rs.2,50,000, but the daily limit is Rs.2,00,000 -> only 4 can succeed
        Counts counts = runParallel(5, i -> transferService.transfer(a.userId(), request(b.accountNumber(), "50000.00")));

        assertEquals(0, counts.unexpected());
        assertEquals(4, counts.ok());
        assertEquals(1, counts.rejected());
        assertMoney("200000.00", balanceOf(b));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------
    private Customer createCustomer(String email, String accountNumber, String balance) {
        User u = new User();
        u.setFullName("Test " + email);
        u.setEmail(email);
        u.setPhone("9876543210");
        u.setDateOfBirth(LocalDate.of(2000, 1, 1));
        u.setPasswordHash("not-a-real-hash");
        u.setRole("CUSTOMER");
        Long userId = userRepository.save(u);

        Account acc = new Account();
        acc.setAccountNumber(accountNumber);
        acc.setUserId(userId);
        acc.setAccountType("SAVINGS");
        Long accountId = accountRepository.save(acc);
        accountRepository.updateBalance(accountId, new BigDecimal(balance));
        return new Customer(userId, accountId, accountNumber);
    }

    private TransferRequest request(String toAccountNumber, String amount) {
        TransferRequest r = new TransferRequest();
        r.setToAccountNumber(toAccountNumber);
        r.setAmount(new BigDecimal(amount));
        return r;
    }

    /** Starts n threads that all wait at a "starting line" and then run the task at the same moment. */
    private Counts runParallel(int n, TaskWithIndex task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            final int index = i;
            Callable<Object> job = () -> {
                ready.countDown();
                go.await();
                try {
                    task.run(index);
                    ok.incrementAndGet();
                } catch (ApiException e) {
                    rejected.incrementAndGet();      // a business rule said no (expected)
                } catch (Exception e) {
                    unexpected.incrementAndGet();    // anything else (for example a deadlock)
                    e.printStackTrace();
                }
                return null;
            };
            futures.add(pool.submit(job));
        }
        ready.await();
        go.countDown();                               // ready, steady, GO
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);              // if a deadlock hangs a thread, the test fails here
        }
        pool.shutdown();
        return new Counts(ok.get(), rejected.get(), unexpected.get());
    }

    @FunctionalInterface
    private interface TaskWithIndex {
        Object run(int index) throws Exception;
    }

    private BigDecimal balanceOf(Customer c) {
        return jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, c.accountId());
    }

    private int countRows(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Expected " + expected + " but was " + actual);
    }
}
