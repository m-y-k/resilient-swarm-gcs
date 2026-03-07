package com.rsgcs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Overall swarm mission status — broadcast to frontend status bar.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionState {

    private String missionId; // UUID
    private String status; // "IDLE", "ACTIVE", "PAUSED", "RECOVERING"
    private int currentLeaderId;
    private int activeDroneCount;
    private int totalDroneCount;
    private Instant missionStartTime;
    private Instant lastElectionTime; // nullable
    private int electionCount;

    // Mission planning fields
    @Builder.Default
    private List<Waypoint> missionWaypoints = new ArrayList<>();
    private String missionPattern; // "CUSTOM" (operator waypoints) or "GRID_SEARCH" (auto)

    /**
     * Creates a fresh IDLE mission state.
     */
    public static MissionState createIdle(int totalDrones) {
        return MissionState.builder()
                .missionId(UUID.randomUUID().toString())
                .status("IDLE")
                .currentLeaderId(0)
                .activeDroneCount(0)
                .totalDroneCount(totalDrones)
                .missionStartTime(null)
                .lastElectionTime(null)
                .electionCount(0)
                .build();
    }
}
