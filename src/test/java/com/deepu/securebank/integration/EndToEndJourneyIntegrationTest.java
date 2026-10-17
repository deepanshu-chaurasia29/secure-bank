package com.deepu.securebank.integration;

import com.deepu.securebank.model.User;
import com.deepu.securebank.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole product in ONE test, through the real HTTP layer and a real database:
 * register -> deposit -> transfer -> balances -> history -> admin freeze -> blocked -> unfreeze -> works again.
 * Run it before your demo and before every deployment: if it is green, the main flow works.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndToEndJourneyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanTestDatabase() throws Exception {
        TestDatabase.cleanOrRefuse(jdbcTemplate);
    }

    @Test
    void fullCustomerAndAdminJourney() throws Exception {
        // 1. two customers register
        JsonNode alice = register("alice@test.com");
        JsonNode bob = register("bob@test.com");
        String aliceToken = alice.get("token").asText();
        String bobToken = bob.get("token").asText();
        String aliceNumber = alice.get("accountNumber").asText();
        String bobNumber = bob.get("accountNumber").asText();

        // 2. Alice deposits 1000
        money(aliceToken, "/api/v1/transactions/deposit", "{\"amount\":1000.00,\"remark\":\"salary\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.balanceAfter").value(1000.0));

        // 3. Alice sends 300 to Bob
        money(aliceToken, "/api/v1/transactions/transfer",
                "{\"toAccountNumber\":\"" + bobNumber + "\",\"amount\":300.00,\"remark\":\"rent\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.balanceAfter").value(700.0))
                .andExpect(jsonPath("$.relatedAccount").value("XXXXXXXX" + last4(bobNumber)));

        // 4. balances
        mockMvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(700.0));
        mockMvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(300.0));

        // 5. history: newest first, other account masked
        mockMvc.perform(get("/api/v1/transactions/me").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.content[1].type").value("DEPOSIT"));
        mockMvc.perform(get("/api/v1/transactions/me").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.content[0].relatedAccount").value("XXXXXXXX" + last4(aliceNumber)));

        // 6. the admin finds Alice and freezes her account
        String adminToken = createAdminAndLogin("admin@test.com", "AdminPass1");
        String found = mockMvc.perform(get("/api/v1/admin/accounts?search=alice@test.com")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();
        long aliceAccountId = objectMapper.readTree(found).get("content").get(0).get("accountId").asLong();

        mockMvc.perform(put("/api/v1/admin/accounts/" + aliceAccountId + "/freeze")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        // 7. frozen: Alice cannot withdraw, and Bob cannot send money to her
        money(aliceToken, "/api/v1/transactions/withdraw", "{\"amount\":50.00}")
                .andExpect(status().isForbidden());
        money(bobToken, "/api/v1/transactions/transfer",
                "{\"toAccountNumber\":\"" + aliceNumber + "\",\"amount\":10.00}")
                .andExpect(status().isBadRequest());

        // 8. unfreeze: Alice can withdraw again
        mockMvc.perform(put("/api/v1/admin/accounts/" + aliceAccountId + "/unfreeze")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        money(aliceToken, "/api/v1/transactions/withdraw", "{\"amount\":100.00}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAfter").value(600.0));

        // 9. the audit log kept the admin's actions
        assertEquals(1, auditCount("FREEZE_ACCOUNT"));
        assertEquals(1, auditCount("UNFREEZE_ACCOUNT"));
    }

    // ---------------- helpers ----------------
    private ResultActions money(String token, String url, String json) throws Exception {
        return mockMvc.perform(post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private JsonNode register(String email) throws Exception {
        String body = "{\"fullName\":\"Test User\",\"email\":\"" + email + "\",\"phone\":\"9876543210\","
                + "\"dateOfBirth\":\"2000-01-01\",\"password\":\"Pass12345\",\"accountType\":\"SAVINGS\"}";
        String json = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private String createAdminAndLogin(String email, String password) throws Exception {
        User admin = new User();
        admin.setFullName("Test Admin");
        admin.setEmail(email);
        admin.setPhone("0000000000");
        admin.setDateOfBirth(LocalDate.of(1990, 1, 1));
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole("ADMIN");
        userRepository.save(admin);

        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        String json = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }

    private String last4(String accountNumber) {
        return accountNumber.substring(accountNumber.length() - 4);
    }

    private int auditCount(String action) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ?", Integer.class, action);
    }
}
