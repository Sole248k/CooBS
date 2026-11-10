package com.group2.fse.notification_service.controller;

import com.group2.fse.notification_service.dto.DlqMessageDto;
import com.group2.fse.notification_service.dto.DlqReplayResponseDto;
import com.group2.fse.notification_service.service.NotificationDlqService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Task: Dev 3 Kafka DLQ & Admin Replay
 * Admin REST Controller for Kafka Dead Letter Topic inspection and replay orchestration.
 * Strict conformance with API Contract T6 and T7 specifications.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications/admin/dlq")
@RequiredArgsConstructor
public class NotificationDlqController {

    private final NotificationDlqService notificationDlqService;

    /**
     * Inspects messages currently parked in the Dead Letter Topic.
     * Conforms with API Contract T6.
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('ROLE_ADMIN')")
    @GetMapping
    public ResponseEntity<List<DlqMessageDto>> getDlqMessages() {
        log.info("Admin request to inspect DLQ messages");
        List<DlqMessageDto> messages = notificationDlqService.getDlqMessages();
        return ResponseEntity.ok(messages);
    }

    /**
     * Replays a specific Dead Letter record back to its origin Kafka topic.
     * Conforms with API Contract T7.
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('ROLE_ADMIN')")
    @PostMapping("/{recordId}/replay")
    public ResponseEntity<DlqReplayResponseDto> replayRecord(@PathVariable("recordId") String recordId) {
        log.info("Admin request to replay DLQ record: {}", recordId);
        DlqReplayResponseDto response = notificationDlqService.replayMessage(recordId);
        return ResponseEntity.ok(response);
    }

    /**
     * Bulk replays all parked DLQ messages back to their origin Kafka topics.
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('ROLE_ADMIN')")
    @PostMapping("/replay")
    public ResponseEntity<List<DlqReplayResponseDto>> replayAll() {
        log.info("Admin request to replay all DLQ messages");
        List<DlqReplayResponseDto> responses = notificationDlqService.replayAll();
        return ResponseEntity.ok(responses);
    }
}
