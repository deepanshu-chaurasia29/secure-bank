package com.deepu.securebank.config;

import com.deepu.securebank.model.User;
import com.deepu.securebank.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * FR-E7: creates the FIRST admin when the app starts.
 * An admin can never register from the public page, so this is the only way to get one.
 *
 * It reads two ENVIRONMENT VARIABLES: ADMIN_EMAIL and ADMIN_PASSWORD.
 * There is no default password in the code, on purpose.
 * If they are not set, nothing happens. If the admin already exists, nothing happens.
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    // Same password rule as registration: at least 8 characters, with a letter and a number
    private static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${ADMIN_EMAIL:}") String adminEmail,
                       @Value("${ADMIN_PASSWORD:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.info("Admin seeding skipped. Set ADMIN_EMAIL and ADMIN_PASSWORD to create the first admin.");
            return;
        }
        if (!adminPassword.matches(PASSWORD_RULE)) {
            log.warn("Admin seeding skipped: ADMIN_PASSWORD must be at least 8 characters with a letter and a number.");
            return;
        }
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin seeding skipped: a user with this email already exists.");
            return;
        }

        User admin = new User();
        admin.setFullName("Bank Admin");
        admin.setEmail(adminEmail.trim());
        admin.setPhone("0000000000");
        admin.setDateOfBirth(LocalDate.of(1990, 1, 1));
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        userRepository.save(admin);

        log.info("First admin created: {}", adminEmail.trim()); // the password is NEVER logged
    }
}
