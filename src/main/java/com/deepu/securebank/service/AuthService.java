package com.deepu.securebank.service;

import com.deepu.securebank.dto.AuthResponse;
import com.deepu.securebank.dto.LoginRequest;
import com.deepu.securebank.dto.RegisterRequest;
import com.deepu.securebank.exception.ApiException;
import com.deepu.securebank.model.Account;
import com.deepu.securebank.model.User;
import com.deepu.securebank.repository.AccountRepository;
import com.deepu.securebank.repository.UserRepository;
import com.deepu.securebank.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Period;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 3; // BR-7
    private static final int LOCK_MINUTES = 15;        // BR-7
    private static final int MIN_AGE_YEARS = 18;        // FR-A2

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepository,
                        AccountRepository accountRepository,
                        PasswordEncoder passwordEncoder,
                        JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        // FR-A2: age check (Bean Validation already checked format/pattern rules)
        int age = Period.between(request.getDateOfBirth(), java.time.LocalDate.now()).getYears();
        if (age < MIN_AGE_YEARS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You must be at least 18 years old to register");
        }

        // FR-A2: unique email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        // FR-A4: BCrypt hash, never store plain password
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("CUSTOMER"); // FR-E7: nobody can self-register as ADMIN

        Long userId = userRepository.save(user);

        // FR-B1: create exactly one account for the new user
        Account account = new Account();
        account.setAccountNumber(generateUniqueAccountNumber());
        account.setUserId(userId);
        account.setAccountType(request.getAccountType());
        accountRepository.save(account);

        String token = jwtUtil.generateToken(userId, "CUSTOMER");
        return new AuthResponse(token, account.getAccountNumber(), user.getFullName(), "CUSTOMER");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        // FR-A5: check lockout first
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.LOCKED,
                    "Account is locked due to too many failed attempts. Try again after "
                            + user.getLockedUntil());
        }

        boolean matches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());

        if (!matches) {
            int attempts = user.getFailedAttempts() + 1;
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                userRepository.lockUser(user.getId(), LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                throw new ApiException(HttpStatus.LOCKED,
                        "Too many failed attempts. Account locked for " + LOCK_MINUTES + " minutes");
            }
            userRepository.incrementFailedAttempts(user.getId(), attempts);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Successful login: reset the failed-attempt counter (FR-A5)
        userRepository.resetFailedAttempts(user.getId());

        Account account = accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Account not found for user"));

        String token = jwtUtil.generateToken(user.getId(), user.getRole());
        return new AuthResponse(token, account.getAccountNumber(), user.getFullName(), user.getRole());
    }

    /**
     * FR-B1: unique 12-digit account number.
     * Retries on the rare chance of a collision.
     */
    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                sb.append(random.nextInt(10));
            }
            accountNumber = sb.toString();
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}
