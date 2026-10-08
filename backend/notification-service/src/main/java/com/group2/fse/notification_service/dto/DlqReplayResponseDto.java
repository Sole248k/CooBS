package com.group2.fse.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Task: Dev 3 Kafka DLQ & Admin Replay
 * Data Transfer Object for Kafka DLQ Replay execution results.
 * Strictly conforms with contract schema T7.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqReplayResponseDto {

    /**
     * Unique identifier of the replayed DLQ record.
     */
    private String recordId;

    /**
     * Replay status flag (true when successfully reenqueued).
     */
    private boolean replayed;

    /**
     * ISO-8601 timestamp when the record was replayed.
     */
    private String replayedAt;
}
