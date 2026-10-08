package com.deepu.securebank.integration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Small helper for integration tests: wipes the TEST database, and refuses to touch any other one. */
final class TestDatabase {

    private TestDatabase() {
    }

    static void cleanOrRefuse(JdbcTemplate jdbcTemplate) throws Exception {
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
}
