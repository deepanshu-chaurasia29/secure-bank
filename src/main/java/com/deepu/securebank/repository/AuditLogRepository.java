package com.deepu.securebank.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
public class AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Writes one line to the audit log: WHO did WHAT. Never put passwords or tokens in "details".
     */
    public void log(Long actorUserId, String action, String details) {
        jdbcTemplate.update(
                "INSERT INTO audit_log (actor_user_id, action, details, created_at) VALUES (?, ?, ?, ?)",
                actorUserId, action, details, Timestamp.valueOf(LocalDateTime.now()));
    }
}
