package com.group2.fse.notification_service.controller;

import com.group2.fse.notification_service.dto.NotificationPageResponseDto;
import com.group2.fse.notification_service.dto.NotificationReadResponseDto;
import com.group2.fse.notification_service.service.NotificationService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Task: FSE-601
 * REST Controller for customer notification feeds and alert management.
 * Strict conformance with API_DESIGN_SPECIFICATION.md Section 6.1.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ROLE_CUSTOMER')")
    @GetMapping({"", "/my-notifications"})
    public ResponseEntity<NotificationPageResponseDto> getMyNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Long customerId = resolveCustomerId(authentication);
        log.info("Fetching notifications for Customer [{}] (unreadOnly={}, page={}, size={})",
                customerId, unreadOnly, page, size);

        NotificationPageResponseDto response = notificationService.getCustomerNotifications(customerId, unreadOnly, page, size);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ROLE_CUSTOMER')")
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationReadResponseDto> markAsRead(
            @PathVariable("id") Long id,
            Authentication authentication) {

        Long customerId = resolveCustomerId(authentication);
        log.info("Marking notification [{}] as read for Customer [{}]", id, customerId);

        NotificationReadResponseDto response = notificationService.markAsRead(id, customerId);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ROLE_CUSTOMER')")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication authentication) {
        Long customerId = resolveCustomerId(authentication);
        long count = notificationService.getUnreadCount(customerId);
        return ResponseEntity.ok(Map.of("customerId", customerId, "unreadCount", count));
    }

    private Long resolveCustomerId(Authentication authentication) {
        if (authentication == null) {
            return 1L; // fallback default
        }
        Object credentials = authentication.getCredentials();
        if (credentials instanceof Claims claims) {
            Object custIdObj = claims.get("customerId");
            if (custIdObj == null) {
                custIdObj = claims.get("userId");
            }
            if (custIdObj instanceof Number n) {
                return n.longValue();
            } else if (custIdObj instanceof String s) {
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException ignored) {}
            }
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            return 1L; // default fallback
        }
    }
}
