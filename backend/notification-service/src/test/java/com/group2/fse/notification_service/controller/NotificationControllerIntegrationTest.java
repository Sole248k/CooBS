package com.group2.fse.notification_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.notification_service.domain.Notification;
import com.group2.fse.notification_service.domain.NotificationChannel;
import com.group2.fse.notification_service.repository.NotificationRepository;
import com.group2.fse.notification_service.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
@DisplayName("FSE-601: Notification REST Controller Integration Tests")
class NotificationControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;
    private String customerJwtToken;
    private Notification sampleNotification;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        notificationRepository.deleteAll();

        // Generate valid customer token
        customerJwtToken = jwtTokenProvider.generateToken("test_customer", 101L, List.of("CUSTOMER"));

        sampleNotification = Notification.builder()
                .customerId(101L)
                .title("Account Debited")
                .message("PHP 2,500.0000 was debited from account ACC_10000001. New balance: PHP 47,500.0000.")
                .channel(NotificationChannel.IN_APP)
                .isRead(false)
                .eventId("evt_test_001")
                .createdAt(Instant.now())
                .build();

        sampleNotification = notificationRepository.save(sampleNotification);
    }

    @Test
    @DisplayName("GET /api/v1/notifications/my-notifications - 200 OK with valid JWT")
    void shouldReturnCustomerNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/my-notifications")
                        .header("Authorization", "Bearer " + customerJwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].notificationId", is(sampleNotification.getId().intValue())))
                .andExpect(jsonPath("$.content[0].title", is("Account Debited")))
                .andExpect(jsonPath("$.content[0].message", containsString("PHP 2,500.0000 was debited")))
                .andExpect(jsonPath("$.content[0].channel", is("IN_APP")))
                .andExpect(jsonPath("$.content[0].isRead", is(false)))
                .andExpect(jsonPath("$.pageNumber", is(0)))
                .andExpect(jsonPath("$.pageSize", is(20)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.isLast", is(true)));
    }

    @Test
    @DisplayName("GET /api/v1/notifications (root alias) - 200 OK with valid JWT")
    void shouldReturnCustomerNotificationsFromRootEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + customerJwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].notificationId", is(sampleNotification.getId().intValue())));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/my-notifications - 403 Forbidden without Authorization header")
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/my-notifications")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read - 200 OK updates read status")
    void shouldMarkNotificationAsRead() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/" + sampleNotification.getId() + "/read")
                        .header("Authorization", "Bearer " + customerJwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId", is(sampleNotification.getId().intValue())))
                .andExpect(jsonPath("$.isRead", is(true)));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read - 404 NOT_FOUND for non-existent notification")
    void shouldReturn404WhenNotificationNotFound() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/9999/read")
                        .header("Authorization", "Bearer " + customerJwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("NOT_FOUND")))
                .andExpect(jsonPath("$.message", containsString("9999 not found")));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/unread-count - 200 OK returns correct count")
    void shouldReturnUnreadCount() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerJwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId", is(101)))
                .andExpect(jsonPath("$.unreadCount", is(1)));
    }
}
