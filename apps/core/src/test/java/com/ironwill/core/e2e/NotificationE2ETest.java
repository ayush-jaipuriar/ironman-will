package com.ironwill.core.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironwill.core.api.dto.AuthRequest;
import com.ironwill.core.api.dto.AuthResponse;
import com.ironwill.core.model.Notification;
import com.ironwill.core.model.Role;
import com.ironwill.core.model.RoleType;
import com.ironwill.core.model.User;
import com.ironwill.core.repository.NotificationRepository;
import com.ironwill.core.repository.RoleRepository;
import com.ironwill.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-End tests for Notification system.
 * Tests notification retrieval and mark-as-read functionality.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Notification E2E Tests")
public class NotificationE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String authToken;
    private String testEmail = "notif@example.com";

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        Role userRole = roleRepository.findByName(RoleType.ROLE_USER)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.ROLE_USER)));

        testUser = new User();
        testUser.setEmail(testEmail);
        testUser.setFullName("Notification Test User");
        testUser.setTimezone("America/New_York");
        testUser.setPasswordHash(passwordEncoder.encode("password123"));
        testUser.setAccountabilityScore(BigDecimal.valueOf(5.00));
        testUser.getRoles().add(userRole);
        testUser = userRepository.save(testUser);

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
    @DisplayName("Should retrieve all unread notifications")
    void testGetUnreadNotifications() throws Exception {
        // Create unread notifications
        Notification notif1 = new Notification();
        notif1.setUser(testUser);
        notif1.setMessage("You missed your goal deadline!");
        notif1.setRead(false);
        notificationRepository.save(notif1);

        Notification notif2 = new Notification();
        notif2.setUser(testUser);
        notif2.setMessage("Reminder: Submit your proof");
        notif2.setRead(false);
        notificationRepository.save(notif2);

        // Create a read notification (should not be returned)
        Notification readNotif = new Notification();
        readNotif.setUser(testUser);
        readNotif.setMessage("This was read");
        readNotif.setRead(true);
        notificationRepository.save(readNotif);

        mockMvc.perform(get("/api/notifications/unread")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].message").exists())
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[1].message").exists())
                .andExpect(jsonPath("$[1].read").value(false));
    }

    @Test
    @DisplayName("Should mark a single notification as read")
    void testMarkNotificationAsRead() throws Exception {
        Notification notification = new Notification();
        notification.setUser(testUser);
        notification.setMessage("Test notification");
        notification.setRead(false);
        notification = notificationRepository.save(notification);

        mockMvc.perform(post("/api/notifications/" + notification.getId() + "/read")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk());

        // Verify notification was marked as read
        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(updated.isRead()).isTrue();
    }

    @Test
    @DisplayName("Should mark all notifications as read")
    void testMarkAllNotificationsAsRead() throws Exception {
        // Create multiple unread notifications
        Notification notif1 = new Notification();
        notif1.setUser(testUser);
        notif1.setMessage("Notification 1");
        notif1.setRead(false);
        notificationRepository.save(notif1);

        Notification notif2 = new Notification();
        notif2.setUser(testUser);
        notif2.setMessage("Notification 2");
        notif2.setRead(false);
        notificationRepository.save(notif2);

        Notification notif3 = new Notification();
        notif3.setUser(testUser);
        notif3.setMessage("Notification 3");
        notif3.setRead(false);
        notificationRepository.save(notif3);

        mockMvc.perform(post("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk());

        // Verify all notifications were marked as read
        long unreadCount = notificationRepository.findByUserIdAndIsRead(testUser.getId(), false).size();
        assertThat(unreadCount).isZero();
    }

    @Test
    @DisplayName("Should return empty list when no unread notifications exist")
    void testGetUnreadNotifications_Empty() throws Exception {
        mockMvc.perform(get("/api/notifications/unread")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Should not access another user's notifications")
    void testGetNotifications_IsolatedByUser() throws Exception {
        // Create another user with notifications
        User anotherUser = new User();
        anotherUser.setEmail("another@example.com");
        anotherUser.setTimezone("UTC");
        anotherUser.setPasswordHash("hash");
        anotherUser = userRepository.save(anotherUser);

        Notification otherNotif = new Notification();
        otherNotif.setUser(anotherUser);
        otherNotif.setMessage("Another user's notification");
        otherNotif.setRead(false);
        notificationRepository.save(otherNotif);

        // Test user should not see another user's notifications
        mockMvc.perform(get("/api/notifications/unread")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Should fail to mark notification as read without authentication")
    void testMarkAsRead_Unauthorized() throws Exception {
        Notification notification = new Notification();
        notification.setUser(testUser);
        notification.setMessage("Test");
        notification.setRead(false);
        notification = notificationRepository.save(notification);

        mockMvc.perform(post("/api/notifications/" + notification.getId() + "/read"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Complete notification flow: create -> retrieve unread -> mark as read -> verify")
    void testCompleteNotificationFlow() throws Exception {
        // 1. Create notifications
        Notification notif1 = new Notification();
        notif1.setUser(testUser);
        notif1.setMessage("First notification");
        notif1.setRead(false);
        notif1 = notificationRepository.save(notif1);

        Notification notif2 = new Notification();
        notif2.setUser(testUser);
        notif2.setMessage("Second notification");
        notif2.setRead(false);
        notif2 = notificationRepository.save(notif2);

        // 2. Retrieve unread (should show 2)
        mockMvc.perform(get("/api/notifications/unread")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // 3. Mark first as read
        mockMvc.perform(post("/api/notifications/" + notif1.getId() + "/read")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk());

        // 4. Retrieve unread again (should show 1)
        mockMvc.perform(get("/api/notifications/unread")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].message").value("Second notification"));

        // 5. Mark all as read
        mockMvc.perform(post("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk());

        // 6. Verify no unread notifications remain
        mockMvc.perform(get("/api/notifications/unread")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}

