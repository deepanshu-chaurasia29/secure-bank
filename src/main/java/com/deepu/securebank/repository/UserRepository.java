package com.deepu.securebank.repository;

import com.deepu.securebank.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<User> USER_ROW_MAPPER = (rs, rowNum) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setDateOfBirth(rs.getDate("date_of_birth").toLocalDate());
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getString("role"));
        u.setFailedAttempts(rs.getInt("failed_attempts"));
        Timestamp lockedUntil = rs.getTimestamp("locked_until");
        u.setLockedUntil(lockedUntil != null ? lockedUntil.toLocalDateTime() : null);
        u.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return u;
    };

    public boolean existsByEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        return count != null && count > 0;
    }

    public Optional<User> findByEmail(String email) {
        return jdbcTemplate.query(
                "SELECT * FROM users WHERE email = ?", USER_ROW_MAPPER, email
        ).stream().findFirst();
    }

    public Optional<User> findById(Long id) {
        return jdbcTemplate.query(
                "SELECT * FROM users WHERE id = ?", USER_ROW_MAPPER, id
        ).stream().findFirst();
    }

    /**
     * Inserts a new user and returns the generated id.
     */
    public Long save(User user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users (full_name, email, phone, date_of_birth, password_hash, role, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setDate(4, java.sql.Date.valueOf(user.getDateOfBirth()));
            ps.setString(5, user.getPasswordHash());
            ps.setString(6, user.getRole());
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void incrementFailedAttempts(Long userId, int newCount) {
        jdbcTemplate.update(
                "UPDATE users SET failed_attempts = ? WHERE id = ?", newCount, userId);
    }

    public void lockUser(Long userId, LocalDateTime lockedUntil) {
        jdbcTemplate.update(
                "UPDATE users SET locked_until = ?, failed_attempts = 0 WHERE id = ?",
                Timestamp.valueOf(lockedUntil), userId);
    }

    public void resetFailedAttempts(Long userId) {
        jdbcTemplate.update(
                "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id = ?", userId);
    }

    public void updatePassword(Long userId, String newPasswordHash) {
        jdbcTemplate.update(
                "UPDATE users SET password_hash = ? WHERE id = ?", newPasswordHash, userId);
    }
}
