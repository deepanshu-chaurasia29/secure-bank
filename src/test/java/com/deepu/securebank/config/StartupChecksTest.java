package com.deepu.securebank.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StartupChecksTest {

    private static final String DEFAULT_SECRET = "change-this-local-only-secret-please-32chars-min";
    private static final String REAL_SECRET = "Xk9f2LpQ7vRt1ZsWm4NbHy6UcEd8AaGj3KoPqTn5";

    @Test
    void customSecret_isAlwaysFine() {
        assertDoesNotThrow(() -> new StartupChecks(REAL_SECRET, true).run());
        assertDoesNotThrow(() -> new StartupChecks(REAL_SECRET, false).run());
    }

    @Test
    void defaultSecret_withTheRequireFlag_stopsTheApp() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new StartupChecks(DEFAULT_SECRET, true).run());
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void defaultSecret_withoutTheFlag_onlyWarns() {
        assertDoesNotThrow(() -> new StartupChecks(DEFAULT_SECRET, false).run());
    }
}
