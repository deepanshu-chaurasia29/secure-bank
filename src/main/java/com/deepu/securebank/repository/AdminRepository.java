package com.deepu.securebank.repository;

import com.deepu.securebank.dto.AdminAccountResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read-only queries for the admin panel. They JOIN users and accounts into one row,
 * which does not fit the User or Account model, so this repository returns the
 * AdminAccountResponse directly.
 */
@Repository
public class AdminRepository {

    private static final String SELECT_PART =
            "SELECT a.id AS account_id, a.account_number, a.account_type, a.balance, " +
                    "a.status AS account_status, a.created_at AS account_created_at, " +
                    "u.id AS user_id, u.full_name, u.email, u.phone, u.locked_until " +
                    "FROM accounts a JOIN users u ON a.user_id = u.id";

    private static final String SEARCH_WHERE =
            " WHERE (u.full_name LIKE ? OR u.email LIKE ? OR a.account_number LIKE ?)";

    private final JdbcTemplate jdbcTemplate;

    public AdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AdminAccountResponse> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp lockedTs = rs.getTimestamp("locked_until");
        LocalDateTime lockedUntil = (lockedTs == null) ? null : lockedTs.toLocalDateTime();
        boolean locked = lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
        return new AdminAccountResponse(
                rs.getLong("account_id"),
                rs.getString("account_number"),
                rs.getString("account_type"),
                rs.getString("account_status"),
                rs.getBigDecimal("balance"),
                rs.getTimestamp("account_created_at").toLocalDateTime(),
                rs.getLong("user_id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone"),
                locked,
                lockedUntil);
    };

    /** One page of accounts. "search" is optional (name, email or account number). */
    public List<AdminAccountResponse> search(String search, int page, int size) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_PART);
        if (search != null) {
            sql.append(SEARCH_WHERE);
            addSearchParams(params, search);
        }
        sql.append(" ORDER BY a.id DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public long count(String search) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM accounts a JOIN users u ON a.user_id = u.id");
        if (search != null) {
            sql.append(SEARCH_WHERE);
            addSearchParams(params, search);
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0 : count;
    }

    public Optional<AdminAccountResponse> findByAccountId(Long accountId) {
        return jdbcTemplate.query(SELECT_PART + " WHERE a.id = ?", ROW_MAPPER, accountId)
                .stream().findFirst();
    }

    // The search text goes in as a parameter ("?"), so there is no SQL injection.
    private void addSearchParams(List<Object> params, String search) {
        String like = "%" + search + "%";
        params.add(like);
        params.add(like);
        params.add(like);
    }
}
