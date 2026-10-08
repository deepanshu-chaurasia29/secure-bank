package com.deepu.securebank.repository;

import com.deepu.securebank.model.Account;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class AccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Account> ACCOUNT_ROW_MAPPER = (rs, rowNum) -> {
        Account a = new Account();
        a.setId(rs.getLong("id"));
        a.setAccountNumber(rs.getString("account_number"));
        a.setUserId(rs.getLong("user_id"));
        a.setAccountType(rs.getString("account_type"));
        a.setBalance(rs.getBigDecimal("balance"));
        a.setStatus(rs.getString("status"));
        a.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return a;
    };

    public boolean existsByAccountNumber(String accountNumber) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE account_number = ?", Integer.class, accountNumber);
        return count != null && count > 0;
    }

    public Optional<Account> findByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM accounts WHERE user_id = ?", ACCOUNT_ROW_MAPPER, userId
        ).stream().findFirst();
    }

    public Optional<Account> findByAccountNumber(String accountNumber) {
        return jdbcTemplate.query(
                "SELECT * FROM accounts WHERE account_number = ?", ACCOUNT_ROW_MAPPER, accountNumber
        ).stream().findFirst();
    }

    /**
     * Inserts a new account (balance 0.00, status ACTIVE) and returns the generated id.
     */
    public Long save(Account account) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO accounts (account_number, user_id, account_type, balance, status, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, account.getAccountNumber());
            ps.setLong(2, account.getUserId());
            ps.setString(3, account.getAccountType());
            ps.setBigDecimal(4, BigDecimal.ZERO.setScale(2));
            ps.setString(5, "ACTIVE");
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * Reads the account AND locks its row until the current transaction ends.
     * Must be called inside a @Transactional method. Other requests that try to
     * lock the same row will wait here, so two withdrawals can never both
     * read the same old balance (FR-C5).
     */
    public Optional<Account> findByUserIdForUpdate(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM accounts WHERE user_id = ? FOR UPDATE", ACCOUNT_ROW_MAPPER, userId
        ).stream().findFirst();
    }

    public void updateBalance(Long accountId, BigDecimal newBalance) {
        jdbcTemplate.update(
                "UPDATE accounts SET balance = ? WHERE id = ?", newBalance, accountId);
    }

    /**
     * Locks ONE account row by its id (used by transfers, which must lock two rows).
     * Must be called inside a @Transactional method.
     */
    public Optional<Account> findByIdForUpdate(Long accountId) {
        return jdbcTemplate.query(
                "SELECT * FROM accounts WHERE id = ? FOR UPDATE", ACCOUNT_ROW_MAPPER, accountId
        ).stream().findFirst();
    }

    public Optional<Account> findById(Long accountId) {
        return jdbcTemplate.query(
                "SELECT * FROM accounts WHERE id = ?", ACCOUNT_ROW_MAPPER, accountId
        ).stream().findFirst();
    }

    /** Used by the admin to FREEZE or unfreeze (set ACTIVE) an account. */
    public void updateStatus(Long accountId, String status) {
        jdbcTemplate.update("UPDATE accounts SET status = ? WHERE id = ?", status, accountId);
    }
}
