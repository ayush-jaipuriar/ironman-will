package com.ironwill.core.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironwill.core.api.dto.AuthRequest;
import com.ironwill.core.api.dto.AuthResponse;
import com.ironwill.core.model.Role;
import com.ironwill.core.model.RoleType;
import com.ironwill.core.model.User;
import com.ironwill.core.repository.RoleRepository;
import com.ironwill.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-End tests for Authentication flows.
 * Tests the complete authentication journey from login to accessing protected endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Authentication E2E Tests")
public class AuthenticationE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String testEmail = "test@example.com";
    private String testPassword = "SecurePassword123!";

    @BeforeEach
    void setUp() {
        // Clean up and create test user with role
        userRepository.deleteAll();
        
        Role userRole = roleRepository.findByName(RoleType.ROLE_USER)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.ROLE_USER)));

        testUser = new User();
        testUser.setEmail(testEmail);
        testUser.setFullName("Test User");
        testUser.setTimezone("America/New_York");
        testUser.setPasswordHash(passwordEncoder.encode(testPassword));
        testUser.setAccountabilityScore(BigDecimal.valueOf(5.00));
        testUser.getRoles().add(userRole);
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Should successfully login with valid credentials and receive JWT token")
    void testLoginSuccess() throws Exception {
        AuthRequest request = new AuthRequest(testEmail, testPassword);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(responseBody, AuthResponse.class);

        assertThat(authResponse.getToken()).isNotBlank();
        assertThat(authResponse.getToken().split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    @DisplayName("Should fail login with invalid password")
    void testLoginFailure_InvalidPassword() throws Exception {
        AuthRequest request = new AuthRequest(testEmail, "WrongPassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should fail login with non-existent email")
    void testLoginFailure_UserNotFound() throws Exception {
        AuthRequest request = new AuthRequest("nonexistent@example.com", testPassword);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should access /auth/me with valid JWT token")
    void testGetCurrentUser_WithValidToken() throws Exception {
        // First, login to get token
        AuthRequest request = new AuthRequest(testEmail, testPassword);
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(responseBody, AuthResponse.class);
        String token = authResponse.getToken();

        // Then, access protected endpoint
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(testEmail))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.accountabilityScore").value(5.00))
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());
    }

    @Test
    @DisplayName("Should deny access to /auth/me without token")
    void testGetCurrentUser_WithoutToken() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should deny access to /auth/me with invalid token")
    void testGetCurrentUser_WithInvalidToken() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should fail login with missing credentials")
    void testLoginFailure_MissingCredentials() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Complete authentication flow: login -> access protected resource -> verify user data")
    void testCompleteAuthenticationFlow() throws Exception {
        // Step 1: Login
        AuthRequest request = new AuthRequest(testEmail, testPassword);
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String token = authResponse.getToken();

        // Step 2: Verify token works for protected endpoint
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(testEmail));

        // Step 3: Verify token is stateless (works on subsequent requests)
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(testEmail));
    }
}

