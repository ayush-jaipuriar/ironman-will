package com.ironwill.core.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironwill.core.api.dto.AuthRequest;
import com.ironwill.core.api.dto.AuthResponse;
import com.ironwill.core.api.dto.GoalRequest;
import com.ironwill.core.model.*;
import com.ironwill.core.repository.GoalRepository;
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
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-End tests for Goal Management operations.
 * Tests CRUD operations for goals including creation, retrieval, and updates.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Goal Management E2E Tests")
public class GoalManagementE2ETest {

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
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String authToken;
    private String testEmail = "goaltest@example.com";

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        goalRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        Role userRole = roleRepository.findByName(RoleType.ROLE_USER)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.ROLE_USER)));

        testUser = new User();
        testUser.setEmail(testEmail);
        testUser.setFullName("Goal Test User");
        testUser.setTimezone("America/Los_Angeles");
        testUser.setPasswordHash(passwordEncoder.encode("password123"));
        testUser.setAccountabilityScore(BigDecimal.valueOf(5.00));
        testUser.getRoles().add(userRole);
        testUser = userRepository.save(testUser);

        // Login to get token
        AuthRequest authRequest = new AuthRequest(testEmail, "password123");
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthResponse.class
        );
        authToken = authResponse.getToken();
    }

    @Test
    @DisplayName("Should create a new goal successfully")
    void testCreateGoal() throws Exception {
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("metric", "pages");
        criteria.put("operator", ">=");
        criteria.put("target", 10);

        GoalRequest request = new GoalRequest();
        request.setTitle("Read 10 pages daily");
        request.setReviewTime(LocalTime.of(21, 0)); // 9 PM
        request.setFrequencyType(FrequencyType.DAILY);
        request.setCriteriaConfig(criteria);

        mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Read 10 pages daily"))
                .andExpect(jsonPath("$.reviewTime").value("21:00:00"))
                .andExpect(jsonPath("$.frequencyType").value("DAILY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.criteriaConfig.metric").value("pages"))
                .andExpect(jsonPath("$.criteriaConfig.target").value(10));
    }

    @Test
    @DisplayName("Should retrieve all goals for authenticated user")
    void testGetAllGoals() throws Exception {
        // Create two goals
        Goal goal1 = new Goal();
        goal1.setUser(testUser);
        goal1.setTitle("Morning Exercise");
        goal1.setReviewTime(LocalTime.of(8, 0));
        goal1.setFrequencyType(FrequencyType.DAILY);
        goal1.setStatus(GoalStatus.ACTIVE);
        goal1.setCriteriaConfig(objectMapper.createObjectNode().put("metric", "duration"));
        goalRepository.save(goal1);

        Goal goal2 = new Goal();
        goal2.setUser(testUser);
        goal2.setTitle("Evening Reading");
        goal2.setReviewTime(LocalTime.of(21, 0));
        goal2.setFrequencyType(FrequencyType.DAILY);
        goal2.setStatus(GoalStatus.ACTIVE);
        goal2.setCriteriaConfig(objectMapper.createObjectNode().put("metric", "pages"));
        goalRepository.save(goal2);

        mockMvc.perform(get("/api/goals")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").exists())
                .andExpect(jsonPath("$[1].title").exists());
    }

    @Test
    @DisplayName("Should retrieve a specific goal by ID")
    void testGetGoalById() throws Exception {
        Goal goal = new Goal();
        goal.setUser(testUser);
        goal.setTitle("Meditation Practice");
        goal.setReviewTime(LocalTime.of(7, 0));
        goal.setFrequencyType(FrequencyType.DAILY);
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setCriteriaConfig(objectMapper.createObjectNode().put("metric", "duration"));
        goal = goalRepository.save(goal);

        mockMvc.perform(get("/api/goals/" + goal.getId())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(goal.getId().toString()))
                .andExpect(jsonPath("$.title").value("Meditation Practice"))
                .andExpect(jsonPath("$.reviewTime").value("07:00:00"));
    }

    @Test
    @DisplayName("Should update an existing goal")
    void testUpdateGoal() throws Exception {
        Goal goal = new Goal();
        goal.setUser(testUser);
        goal.setTitle("Old Title");
        goal.setReviewTime(LocalTime.of(10, 0));
        goal.setFrequencyType(FrequencyType.DAILY);
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setCriteriaConfig(objectMapper.createObjectNode().put("metric", "count"));
        goal = goalRepository.save(goal);

        Map<String, Object> updatedCriteria = new HashMap<>();
        updatedCriteria.put("metric", "pages");
        updatedCriteria.put("target", 20);

        GoalRequest updateRequest = new GoalRequest();
        updateRequest.setTitle("Updated Title");
        updateRequest.setReviewTime(LocalTime.of(15, 30));
        updateRequest.setFrequencyType(FrequencyType.WEEKLY);
        updateRequest.setCriteriaConfig(updatedCriteria);

        mockMvc.perform(put("/api/goals/" + goal.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.reviewTime").value("15:30:00"))
                .andExpect(jsonPath("$.frequencyType").value("WEEKLY"))
                .andExpect(jsonPath("$.criteriaConfig.target").value(20));
    }

    @Test
    @DisplayName("Should fail to create goal without authentication")
    void testCreateGoal_Unauthorized() throws Exception {
        GoalRequest request = new GoalRequest();
        request.setTitle("Should Fail");
        request.setReviewTime(LocalTime.of(21, 0));
        request.setFrequencyType(FrequencyType.DAILY);
        request.setCriteriaConfig(new HashMap<>());

        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should fail to access another user's goal")
    void testGetGoal_Forbidden() throws Exception {
        // Create another user and their goal
        User anotherUser = new User();
        anotherUser.setEmail("another@example.com");
        anotherUser.setTimezone("UTC");
        anotherUser.setPasswordHash("hash");
        anotherUser = userRepository.save(anotherUser);

        Goal anotherGoal = new Goal();
        anotherGoal.setUser(anotherUser);
        anotherGoal.setTitle("Another User's Goal");
        anotherGoal.setReviewTime(LocalTime.of(12, 0));
        anotherGoal.setFrequencyType(FrequencyType.DAILY);
        anotherGoal.setStatus(GoalStatus.ACTIVE);
        anotherGoal.setCriteriaConfig(objectMapper.createObjectNode());
        anotherGoal = goalRepository.save(anotherGoal);

        // Try to access with testUser's token
        mockMvc.perform(get("/api/goals/" + anotherGoal.getId())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Complete goal lifecycle: create -> retrieve -> update -> list")
    void testCompleteGoalLifecycle() throws Exception {
        // 1. Create goal
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("metric", "pages");
        criteria.put("target", 10);

        GoalRequest createRequest = new GoalRequest();
        createRequest.setTitle("Daily Reading");
        createRequest.setReviewTime(LocalTime.of(22, 0));
        createRequest.setFrequencyType(FrequencyType.DAILY);
        createRequest.setCriteriaConfig(criteria);

        MvcResult createResult = mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String goalId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. Retrieve goal
        mockMvc.perform(get("/api/goals/" + goalId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Daily Reading"));

        // 3. Update goal
        GoalRequest updateRequest = new GoalRequest();
        updateRequest.setTitle("Daily Reading - Updated");
        updateRequest.setReviewTime(LocalTime.of(23, 0));
        updateRequest.setFrequencyType(FrequencyType.DAILY);
        updateRequest.setCriteriaConfig(criteria);

        mockMvc.perform(put("/api/goals/" + goalId)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Daily Reading - Updated"));

        // 4. List all goals (should show updated version)
        mockMvc.perform(get("/api/goals")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Daily Reading - Updated"));
    }
}

