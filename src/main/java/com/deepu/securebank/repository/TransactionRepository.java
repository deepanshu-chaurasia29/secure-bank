package com.deepu.securebank.repository;

import com.deepu.securebank.model.Transaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class TransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    public TransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Transaction> TXN_ROW_MAPPER = (rs, rowNum) -> {
        Transaction t = new Transaction();
        t.setId(rs.getLong("id"));
        t.setReferenceId(rs.getString("reference_id"));
        t.setAccountId(rs.getLong("account_id"));
        t.setType(rs.getString("type"));
        t.setAmount(rs.getBigDecimal("amount"));
        t.setBalanceAfter(rs.getBigDecimal("balance_after"));
        long related = rs.getLong("related_account_id");
        t.setRelatedAccountId(rs.wasNull() ? null : related);
        t.setRelatedAccountNumber(rs.getString("related_account_number"));
        t.setStatus(rs.getString("status"));
        t.setRemark(rs.getString("remark"));
        t.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return t;
    };

    public void save(Transaction t) {
        jdbcTemplate.update(
                "INSERT INTO transactions " +
                        "(reference_id, account_id, type, amount, balance_after, related_account_id, status, remark, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                t.getReferenceId(), t.getAccountId(), t.getType(), t.getAmount(), t.getBalanceAfter(),
                t.getRelatedAccountId(), t.getStatus(), t.getRemark(),
                Timestamp.valueOf(LocalDateTime.now()));
    }

    /**
     * One page of an account's history, newest first, with optional filters.
     * The SQL text is built from fixed pieces only; every value goes in through "?".
     */
    public List<Transaction> findPage(Long accountId, String type, LocalDateTime from, LocalDateTime toExclusive,
                                      int page, int size) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT t.*, ra.account_number AS related_account_number " +
                        "FROM transactions t LEFT JOIN accounts ra ON t.related_account_id = ra.id " +
                        "WHERE t.account_id = ?");
        params.add(accountId);
        appendFilters(sql, params, type, from, toExclusive);
        sql.append(" ORDER BY t.created_at DESC, t.id DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);
        return jdbcTemplate.query(sql.toString(), TXN_ROW_MAPPER, params.toArray());
    }

    /** Total rows for the same filters, so the page can say "page 2 of 7". */
    public long count(Long accountId, String type, LocalDateTime from, LocalDateTime toExclusive) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM transactions t WHERE t.account_id = ?");
        params.add(accountId);
        appendFilters(sql, params, type, from, toExclusive);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0 : count;
    }

    private void appendFilters(StringBuilder sql, List<Object> params, String type,
                               LocalDateTime from, LocalDateTime toExclusive) {
        if (type != null) {
            sql.append(" AND t.type = ?");
            params.add(type);
        }
        if (from != null) {
            sql.append(" AND t.created_at >= ?");
            params.add(Timestamp.valueOf(from));
        }
        if (toExclusive != null) {
            sql.append(" AND t.created_at < ?");
            params.add(Timestamp.valueOf(toExclusive));
        }
    }

    /**
     * How much money this account has already SENT by transfer in a time window
     * (used for the daily transfer limit, BR-4).
     */
    public BigDecimal sumTransferOut(Long accountId, LocalDateTime from, LocalDateTime toExclusive) {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
                        "WHERE account_id = ? AND type = 'TRANSFER_OUT' AND status = 'SUCCESS' " +
                        "AND created_at >= ? AND created_at < ?",
                BigDecimal.class, accountId, Timestamp.valueOf(from), Timestamp.valueOf(toExclusive));
        return sum == null ? BigDecimal.ZERO : sum;
    }
}
