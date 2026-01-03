package com.ironwill.core.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironwill.core.api.dto.AuthRequest;
import com.ironwill.core.api.dto.AuthResponse;
import com.ironwill.core.client.AgentClient;
import com.ironwill.core.client.AgentResponse;
import com.ironwill.core.model.*;
import com.ironwill.core.repository.AuditLogRepository;
import com.ironwill.core.repository.GoalRepository;
import com.ironwill.core.repository.RoleRepository;
import com.ironwill.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-End tests for Audit Submission flow.
 * Tests proof upload, validation, score updates, and lockout scenarios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Audit Submission E2E Tests")
public class AuditSubmissionE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AgentClient agentClient;

    private User testUser;
    private Goal testGoal;
    private String authToken;
    private String testEmail = "audit@example.com";

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        auditLogRepository.deleteAll();
        goalRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        Role userRole = roleRepository.findByName(RoleType.ROLE_USER)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.ROLE_USER)));

        testUser = new User();
        testUser.setEmail(testEmail);
        testUser.setFullName("Audit Test User");
        testUser.setTimezone("America/New_York");
        testUser.setPasswordHash(passwordEncoder.encode("password123"));
        testUser.setAccountabilityScore(BigDecimal.valueOf(5.00));
        testUser.getRoles().add(userRole);
        testUser = userRepository.save(testUser);

        // Create test goal
        testGoal = new Goal();
        testGoal.setUser(testUser);
        testGoal.setTitle("Test Goal");
        testGoal.setReviewTime(LocalTime.of(21, 0));
        testGoal.setFrequencyType(FrequencyType.DAILY);
        testGoal.setStatus(GoalStatus.ACTIVE);
        testGoal.setCriteriaConfig(objectMapper.createObjectNode().put("metric", "pages"));
        testGoal = goalRepository.save(testGoal);

        // Login to get token
        AuthRequest authRequest = new AuthRequest(testEmail, "password123");
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthResponse.class
        );
        authToken = authResponse.getToken();
    }

    @Test
    @DisplayName("Should successfully submit audit with valid proof and receive PASS verdict")
    void testAuditSubmission_Pass() throws Exception {
        // Mock agent response
        AgentResponse agentResponse = new AgentResponse();
        agentResponse.setVerdict("PASS");
        agentResponse.setRemarks("Great work! Goal achieved.");
        agentResponse.setScoreImpact(BigDecimal.valueOf(0.50));
        when(agentClient.judgeAudit(any())).thenReturn(agentResponse);

        // Create mock proof image (JPEG)
        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile proofFile = new MockMultipartFile(
                "proof",
                "test-proof.jpg",
                "image/jpeg",
                jpegHeader
        );

        mockMvc.perform(multipart("/api/goals/" + testGoal.getId() + "/audit")
                        .file(proofFile)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("PASS"))
                .andExpect(jsonPath("$.remarks").value("Great work! Goal achieved."))
                .andExpect(jsonPath("$.scoreDelta").value(0.50))
                .andExpect(jsonPath("$.newScore").value(5.50));

        // Verify user score was updated
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getAccountabilityScore()).isEqualByComparingTo(BigDecimal.valueOf(5.50));

        // Verify audit log was created
        AuditLog auditLog = auditLogRepository.findByGoalIdAndAuditDate(
                testGoal.getId(),
                LocalDate.now()
        ).orElseThrow();
        assertThat(auditLog.getStatus()).isEqualTo(AuditStatus.VERIFIED);
        assertThat(auditLog.getScoreImpact()).isEqualByComparingTo(BigDecimal.valueOf(0.50));
    }

    @Test
    @DisplayName("Should submit audit with FAIL verdict and decrease score")
    void testAuditSubmission_Fail() throws Exception {
        // Mock agent response
        AgentResponse agentResponse = new AgentResponse();
        agentResponse.setVerdict("FAIL");
        agentResponse.setRemarks("Proof does not meet criteria.");
        agentResponse.setScoreImpact(BigDecimal.valueOf(-0.20));
        when(agentClient.judgeAudit(any())).thenReturn(agentResponse);

        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile proofFile = new MockMultipartFile(
                "proof",
                "test-proof.jpg",
                "image/jpeg",
                jpegHeader
        );

        mockMvc.perform(multipart("/api/goals/" + testGoal.getId() + "/audit")
                        .file(proofFile)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("FAIL"))
                .andExpect(jsonPath("$.scoreDelta").value(-0.20))
                .andExpect(jsonPath("$.newScore").value(4.80));

        // Verify user score was decreased
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getAccountabilityScore()).isEqualByComparingTo(BigDecimal.valueOf(4.80));
    }

    @Test
    @DisplayName("Should reject audit submission with invalid file type")
    void testAuditSubmission_InvalidFileType() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "proof",
                "test.txt",
                "text/plain",
                "This is not an image".getBytes()
        );

        mockMvc.perform(multipart("/api/goals/" + testGoal.getId() + "/audit")
                        .file(invalidFile)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject audit submission with file exceeding size limit")
    void testAuditSubmission_FileTooLarge() throws Exception {
        // Create a file larger than 5MB
        byte[] largeFileContent = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile largeFile = new MockMultipartFile(
                "proof",
                "large.jpg",
                "image/jpeg",
                largeFileContent
        );

        mockMvc.perform(multipart("/api/goals/" + testGoal.getId() + "/audit")
                        .file(largeFile)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should lock user goals when score drops below threshold")
    void testAuditSubmission_LockoutTriggered() throws Exception {
        // Set user score just above threshold
        testUser.setAccountabilityScore(BigDecimal.valueOf(3.10));
        testUser = userRepository.save(testUser);

        // Mock agent response with large penalty
        AgentResponse agentResponse = new AgentResponse();
        agentResponse.setVerdict("FAIL");
        agentResponse.setRemarks("Severe violation");
        agentResponse.setScoreImpact(BigDecimal.valueOf(-0.50)); // Will drop to 2.60
        when(agentClient.judgeAudit(any())).thenReturn(agentResponse);

        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile proofFile = new MockMultipartFile(
                "proof",
                "test-proof.jpg",
                "image/jpeg",
                jpegHeader
        );

        mockMvc.perform(multipart("/api/goals/" + testGoal.getId() + "/audit")
                        .file(proofFile)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newScore").value(2.60));

        // Verify goals are locked
        Goal updatedGoal = goalRepository.findById(testGoal.getId()).orElseThrow();
        assertThat(updatedGoal.getStatus()).isEqualTo(GoalStatus.LOCKED);
        assertThat(updatedGoal.getLockedUntil()).isNotNull();
    }

    @Test
    @DisplayName("Should reject audit submission when user is locked out")
    void testAuditSubmission_UserLockedOut() throws Exception {
        // Lock the user
        testUser.setAccountabilityScore(BigDecimal.valueOf(2.50));
        testUser = userRepository.save(testUser);
        
        testGoal.setStatus(GoalStatus.LOCKED);
        testGoal.setLockedUntil(java.time.LocalDateTime.now().plusHours(24));
        testGoal = goalRepository.save(testGoal);

        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile proofFile = new MockMultipartFile(
                "proof",
                "test-proof.jpg",
                "image/jpeg",
                jpegHeader
        );

        mockMvc.perform(multipart("/api/goals/" + testGoal.getId() + "/audit")
                        .file(proofFile)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isLocked());
    }

    @Test
    @DisplayName("Should prevent duplicate audit submission for same day")
    void testAuditSubmission_DuplicatePrevention() throws Exception {
        // Create existing audit for today
        AuditLog existingAudit = new AuditLog();
        existingAudit.setGoal(testGoal);
        existingAudit.setAuditDate(LocalDate.now());
        existingAudit.setStatus(AuditStatus.VERIFIED);
        existingAudit.setScoreImpact(BigDecimal.valueOf(0.50));
        auditLogRepository.save(existingAudit);

        // Mock agent response
        AgentResponse agentResponse = new AgentResponse();
        agentResponse.setVerdict("PASS");
        agentResponse.setScoreImpact(BigDecimal.valueOf(0.50));
        when(agentClient.judgeAudit(any())).thenReturn(agentResponse);

        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile proofFile = new MockMultipartFile(
                "proof",
                "test-proof.jpg",
                "image/jpeg",
                jpegHeader
        );

        mockMvc.perform(multipart("/api/goals/" + testGoal.getId() + "/audit")
                        .file(proofFile)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Should handle agent failure gracefully with TECHNICAL_DIFFICULTY")
    void testAuditSubmission_AgentFailure() throws Exception {
        // Mock agent failure
        when(agentClient.judgeAudit(any())).thenThrow(new RuntimeException("Agent service unavailable"));

        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile proofFile = new MockMultipartFile(
                "proof",
                "test-proof.jpg",
                "image/jpeg",
                jpegHeader
        );

        mockMvc.perform(multipart("/api/goals/" + testGoal.getId() + "/audit")
                        .file(proofFile)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("TECHNICAL_DIFFICULTY"))
                .andExpect(jsonPath("$.scoreDelta").value(0.00));

        // Verify score was NOT changed (no penalty for technical issues)
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getAccountabilityScore()).isEqualByComparingTo(BigDecimal.valueOf(5.00));
    }
}

