package com.group2.fse.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Task: Dev 3 Kafka DLQ & Admin Replay
 * Data Transfer Object for Kafka Dead-Letter Topic (DLT) items.
 * Strictly conforms with contract schema T6.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqMessageDto {

    /**
     * Unique identifier for the DLQ item in format: <topic>-<partition>-<offset>
     * Example: notification.events.DLT-0-42
     */
    private String recordId;

    /**
     * The dead-letter topic name, e.g. notification.events.DLT
     */
    private String topic;

    /**
     * Original topic before error routing, e.g. ledger.mutation.completed.v1
     */
    private String originalTopic;

    /**
     * Kafka message key
     */
    private String key;

    /**
     * Exception message from failure
     */
    private String errorMessage;

    /**
     * Total delivery attempts executed prior to dead-lettering
     */
    private Integer attempts;

    /**
     * Timestamp when the message failed and was routed to DLT
     */
    private String failedAt;

    /**
     * Raw message payload content
     */
    private String payload;
}
