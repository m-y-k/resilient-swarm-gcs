package com.rsgcs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Core drone data model — holds all state for a single drone in the swarm.
 * Stored in SwarmOrchestrator's ConcurrentHashMap.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroneState {

    private int droneId; // 1-10, unique
    private DroneType droneType; // SURVEILLANCE, LOGISTICS, STRIKE
    private DroneRole role; // LEADER, FOLLOWER, CANDIDATE, LOST
    private double latitude;
    private double longitude;
    private double altitude; // meters
    private double heading; // degrees 0-360
    private double speed; // m/s
    private double batteryPercent; // 0-100
    private Instant lastHeartbeat; // last received heartbeat timestamp
    private String status; // "ACTIVE", "STALE", "LOST"
    private long sequenceNumber; // increments with each packet

    // Pathfinding: intended route including obstacle detour points
    private List<double[]> plannedPath;

    // The index of the waypoint the drone is currently navigating to
    private int currentWaypointIndex;

    /**
     * Assigns DroneType based on ID per spec:
     * 1-3 → SURVEILLANCE, 4-6 → LOGISTICS, 7-10 → STRIKE
     */
    public static DroneType typeForId(int droneId) {
        if (droneId <= 3)
            return DroneType.SURVEILLANCE;
        if (droneId <= 6)
            return DroneType.LOGISTICS;
        return DroneType.STRIKE;
    }
}
