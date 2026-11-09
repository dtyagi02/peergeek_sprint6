package com.peergeek.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.notification.dto.CreateNotificationRequest;
import com.peergeek.notification.entity.NotificationType;
import com.peergeek.notification.repository.NotificationRepository;
import com.peergeek.notification.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerIntegrationTest {

    private static final String GATEWAY_SECRET = "pg-internal-gateway-secret-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Test
    void unauthenticatedRequestReturns4xx() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void spoofedUserIdWithoutGatewaySecretIsRejected() throws Exception {
        // Direct caller attempting to spoof X-User-Id without valid X-Gateway-Secret or JWT
        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void authenticatedRequestWithJwtSucceeds() throws Exception {
        Long testUserId = 42L;
        String token = jwtService.generateToken(testUserId, "STUDENT");

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void endToEndNotificationLifecycleWithGatewaySecret() throws Exception {
        Long testUserId = 42L;

        // 1. Create notification via internal endpoint
        CreateNotificationRequest createRequest = new CreateNotificationRequest(
                testUserId,
                NotificationType.COURSE_PUBLISHED,
                "Course Published",
                "Your course is now live!",
                "COURSE",
                101L,
                "/instructor/courses/101",
                "TEST_EVENT_1"
        );

        mockMvc.perform(post("/api/v1/notifications/internal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.userId").value(testUserId))
                .andExpect(jsonPath("$.isRead").value(false));

        // 2. Fetch notifications as user 42 via gateway header
        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", String.valueOf(testUserId))
                        .header("X-User-Role", "INSTRUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Course Published"))
                .andExpect(jsonPath("$.content[0].isRead").value(false));

        // 3. Check unread count
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", String.valueOf(testUserId))
                        .header("X-User-Role", "INSTRUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        Long notifId = notificationRepository.findAll().get(0).getId();

        // 4. Mark as read
        mockMvc.perform(patch("/api/v1/notifications/" + notifId + "/read")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", String.valueOf(testUserId))
                        .header("X-User-Role", "INSTRUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));

        // 5. Verify unread count is now 0
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", String.valueOf(testUserId))
                        .header("X-User-Role", "INSTRUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void userCannotMarkAnotherUsersNotificationAsRead() throws Exception {
        Long ownerId = 42L;
        Long attackerId = 99L;

        CreateNotificationRequest req = new CreateNotificationRequest(
                ownerId, NotificationType.COURSE_PUBLISHED, "Private", "Secret", null, null, null, "OWN_1");

        mockMvc.perform(post("/api/v1/notifications/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());

        Long notifId = notificationRepository.findAll().get(0).getId();

        // Attacker attempts to mark owner's notification as read -> 404 not found / access denied
        mockMvc.perform(patch("/api/v1/notifications/" + notifId + "/read")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", String.valueOf(attackerId))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAllAsReadSucceeds() throws Exception {
        Long testUserId = 77L;

        CreateNotificationRequest n1 = new CreateNotificationRequest(
                testUserId, NotificationType.SYSTEM_ANNOUNCEMENT, "Alert 1", "Message 1", null, null, null, "ALERT_1");
        CreateNotificationRequest n2 = new CreateNotificationRequest(
                testUserId, NotificationType.SYSTEM_ANNOUNCEMENT, "Alert 2", "Message 2", null, null, null, "ALERT_2");

        mockMvc.perform(post("/api/v1/notifications/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(n1))).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/notifications/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(n2))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(post("/api/v1/notifications/mark-all-read")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedCount").value(2));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", String.valueOf(testUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }
}
