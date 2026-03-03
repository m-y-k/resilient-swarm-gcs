package com.rsgcs.service;

import com.rsgcs.model.DroneRole;
import com.rsgcs.model.DroneState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Background heartbeat monitor — sweeps droneRegistry every 200ms.
 * Implements the "3-strike rule": ACTIVE → STALE @500ms → LOST @1000ms.
 * Triggers leader election when the LEADER goes LOST.
 */
@Slf4j
@Service
public class HeartbeatMonitor {

    private final SwarmOrchestrator orchestrator;
    private final LeaderElectionService electionService;

    // Heartbeat counter for throttled log broadcasts
    private int heartbeatCycleCount = 0;

    public HeartbeatMonitor(@Lazy SwarmOrchestrator orchestrator,
            @Lazy LeaderElectionService electionService) {
        this.orchestrator = orchestrator;
        this.electionService = electionService;
    }

    /**
     * Runs every 200ms — checks all drones for heartbeat gaps.
     * Uses virtual threads (enabled in application.yml).
     */
    @Scheduled(fixedRate = 200)
    public void checkHeartbeats() {
        Map<Integer, DroneState> registry = orchestrator.getDroneRegistry();
        if (registry.isEmpty())
            return;

        Instant now = Instant.now();
        boolean leaderLost = false;
        int activeCount = 0;
        int staleCount = 0;

        for (DroneState drone : registry.values()) {
            if ("LOST".equals(drone.getStatus()))
                continue;

            long elapsed = Duration.between(drone.getLastHeartbeat(), now).toMillis();

            if (elapsed > 1000 && !"LOST".equals(drone.getStatus())) {
                // ── LOST: No heartbeat for >1 second ──
                drone.setStatus("LOST");
                boolean wasLeader = drone.getRole() == DroneRole.LEADER;
                drone.setRole(DroneRole.LOST);

                String msg = String.format("[CRITICAL] Drone_%d LOST — no heartbeat for %dms", drone.getDroneId(),
                        elapsed);
                log.warn(msg);
                orchestrator.broadcastLog("CRITICAL", msg);

                if (wasLeader) {
                    leaderLost = true;
                }

            } else if (elapsed > 500 && "ACTIVE".equals(drone.getStatus())) {
                // ── STALE: Heartbeat delayed >500ms ──
                drone.setStatus("STALE");
                staleCount++;

                String msg = String.format("[WARN] Drone_%d heartbeat stale (%dms)", drone.getDroneId(), elapsed);
                log.warn(msg);
                orchestrator.broadcastLog("WARN", msg);

            } else if (elapsed <= 500 && "STALE".equals(drone.getStatus())) {
                // ── RECOVERED from stale ──
                drone.setStatus("ACTIVE");
                activeCount++;
            } else if ("ACTIVE".equals(drone.getStatus())) {
                activeCount++;
            }
        }

        // Trigger election AFTER the sweep (not mid-loop) to avoid race conditions
        if (leaderLost && !electionService.isElectionInProgress()) {
            log.info("[HEARTBEAT_MONITOR] Leader lost detected — triggering election");
            // Run election on a virtual thread to not block the scheduled task
            Thread.startVirtualThread(() -> electionService.startElection());
        }

        // Throttled heartbeat summary — every 10th cycle (~2 seconds)
        heartbeatCycleCount++;
        if (heartbeatCycleCount >= 10) {
            heartbeatCycleCount = 0;
            int totalActive = (int) registry.values().stream()
                    .filter(d -> !"LOST".equals(d.getStatus())).count();
            if (totalActive > 0) {
                orchestrator.broadcastLog("HEARTBEAT",
                        String.format("[HEARTBEAT] %d drones reporting nominal", totalActive));
            }
        }

        // Periodic swarm state broadcast (~5 times per second via the 200ms interval)
        orchestrator.broadcastSwarmState();
    }
}
