package com.deepu.securebank.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Checks the JWT secret at startup.
 * With the local-only default secret, anyone who has read your code can make valid tokens.
 *
 *  - Default secret + REQUIRE_CUSTOM_JWT_SECRET=true  -> the app REFUSES to start (used in Docker / on Render)
 *  - Default secret + flag not set                    -> only a WARNING (fine on your laptop)
 */
@Component
public class StartupChecks implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupChecks.class);
    private static final String LOCAL_DEFAULT_PREFIX = "change-this-local-only";

    private final String jwtSecret;
    private final boolean requireCustomSecret;

    public StartupChecks(@Value("${app.jwt.secret}") String jwtSecret,
                         @Value("${REQUIRE_CUSTOM_JWT_SECRET:false}") boolean requireCustomSecret) {
        this.jwtSecret = jwtSecret;
        this.requireCustomSecret = requireCustomSecret;
    }

    @Override
    public void run(String... args) {
        if (!jwtSecret.startsWith(LOCAL_DEFAULT_PREFIX)) {
            return; // a custom secret is in use: all good
        }
        if (requireCustomSecret) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Refusing to start with the local-only default secret. "
                            + "Set the JWT_SECRET environment variable (at least 32 random characters).");
        }
        log.warn("JWT_SECRET is not set: using the local-only default secret. "
                + "This is fine on your laptop, but NEVER deploy like this.");
    }
}
