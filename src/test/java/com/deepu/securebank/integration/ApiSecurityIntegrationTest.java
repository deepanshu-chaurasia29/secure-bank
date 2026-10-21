package com.deepu.securebank.integration;

import com.deepu.securebank.model.User;
import com.deepu.securebank.repository.UserRepository;
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

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * INTEGRATION test through the real HTTP layer (MockMvc) and the real security filters.
 * It checks the Sprint 5 features (health URL, Swagger JSON) and the access rules end to end:
 * no token = 401, customer on an admin URL = 403, admin on an admin URL = 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class  ApiSecurityIntegrationTest {

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
    void healthUrl_isPublic_andSaysUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void swaggerJson_isPublic_andDescribesTheApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SecureBank API")))
                .andExpect(content().string(containsString("bearerAuth")))
                .andExpect(content().string(containsString("/api/v1/transactions/transfer")))
                .andExpect(content().string(containsString("/api/v1/admin/accounts")));
    }

    @Test
    void protectedUrl_withoutToken_gives401InTheStandardJsonFormat() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Please log in first"));
    }

    @Test
    void customerToken_onAnAdminUrl_gives403() throws Exception {
        String token = registerAndLogin("customer@test.com");

        mockMvc.perform(get("/api/v1/admin/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminToken_onAnAdminUrl_gives200_andListsTheAccounts() throws Exception {
        registerAndLogin("customer@test.com");
        String adminToken = createAdminAndLogin("admin@test.com", "AdminPass1");

        mockMvc.perform(get("/api/v1/admin/accounts").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("customer@test.com"));
    }

    // ---------------- helpers ----------------
    private String registerAndLogin(String email) throws Exception {
        String body = "{\"fullName\":\"Test User\",\"email\":\"" + email + "\",\"phone\":\"9876543210\","
                + "\"dateOfBirth\":\"2000-01-01\",\"password\":\"Pass12345\",\"accountType\":\"SAVINGS\"}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return login(email, "Pass12345");
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
        return login(email, password);
    }

    private String login(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        String json = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }
}
