package com.deepu.securebank.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Rejects the known local-only JWT key in Docker/Render deployments. */
@Component
public class StartupChecks implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(StartupChecks.class);
  private static final String LOCAL_DEFAULT_PREFIX = "change-this-local-only";
  private static final int MIN_SECRET_LENGTH = 32;

  private final String jwtSecret;
  private final boolean requireCustomSecret;

  public StartupChecks(@Value("${app.jwt.secret}") String jwtSecret,
                       @Value("${REQUIRE_CUSTOM_JWT_SECRET:false}") boolean requireCustomSecret) {
    this.jwtSecret = jwtSecret;
    this.requireCustomSecret = requireCustomSecret;
  }

  @Override
  public void run(String... args) {
    boolean missingOrWeak = jwtSecret == null
        || jwtSecret.isBlank()
        || jwtSecret.startsWith(LOCAL_DEFAULT_PREFIX)
        || jwtSecret.length() < MIN_SECRET_LENGTH;

    if (!missingOrWeak) {
      return;
    }
    if (requireCustomSecret) {
      throw new IllegalStateException(
          "JWT_SECRET is missing, uses the local-only default, or is shorter than 32 characters. "
              + "Refusing to start. Set a unique random JWT_SECRET of at least 32 characters.");
    }
    log.warn("JWT_SECRET is missing, short, or uses the local-only default. "
        + "This is only acceptable for local development; never deploy with this setting.");
  }
}
