package com.rsgcs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Election event log entry — broadcast to frontend for the terminal display.
 * Each step of the Bully Algorithm generates one of these.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElectionEvent {

    private Instant timestamp;
    private String eventType; // "LEADER_TIMEOUT", "ELECTION_START", "ELECTION_MSG", "COORDINATOR",
                              // "NEW_LEADER"
    private int initiatorId;
    private int targetId; // optional — 0 if not applicable
    private String message; // human-readable log line
}
