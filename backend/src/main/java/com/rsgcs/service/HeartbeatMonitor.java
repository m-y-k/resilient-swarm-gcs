package com.rsgcs.service;

import com.rsgcs.model.DroneRole;
import com.rsgcs.model.DroneState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Background heartbeat monitor — sweeps droneRegistry on a fixed interval.
 * Uses configured thresholds to mark drones STALE/LOST and trigger elections.
 */
@Slf4j
@Service
public class HeartbeatMonitor {

    private final SwarmOrchestrator orchestrator;
    private final LeaderElectionService electionService;

    // Heartbeat counter for throttled log broadcasts
    private int heartbeatCycleCount = 0;

    // Thresholds loaded from application.yml (see RS-GCS_Tasks.md Phase 2)
    // heartbeat-timeout-ms: STALE threshold (e.g. 500ms), LOST is 2x that value.
    @Value("${rsgcs.heartbeat-timeout-ms:500}")
    private long heartbeatTimeoutMs;

    public HeartbeatMonitor(@Lazy SwarmOrchestrator orchestrator,
            @Lazy LeaderElectionService electionService) {
        this.orchestrator = orchestrator;
        this.electionService = electionService;
    }

    /**
     * Runs at interval from config — checks all drones for heartbeat gaps.
     * Uses virtual threads (enabled in application.yml).
     */
    @Scheduled(fixedRateString = "${rsgcs.heartbeat-check-interval-ms:200}")
    public void checkHeartbeats() {
        Map<Integer, DroneState> registry = orchestrator.getDroneRegistry();
        if (registry.isEmpty())
            return;

        Instant now = Instant.now();
        boolean leaderLost = false;
        int activeCount = 0;
        int staleCount = 0;

        // Derive thresholds from configuration:
        // STALE when elapsed > heartbeatTimeoutMs (e.g. >500ms)
        // LOST when elapsed > 2 * heartbeatTimeoutMs (e.g. >1000ms)
        long staleThresholdMs = heartbeatTimeoutMs;
        long lostThresholdMs = heartbeatTimeoutMs * 2;

        for (DroneState drone : registry.values()) {
            // Skip drones that are already confirmed LOST (killed or battery dead)
            if ("LOST".equals(drone.getStatus()))
                continue;

            long elapsed = Duration.between(drone.getLastHeartbeat(), now).toMillis();

            if (elapsed > lostThresholdMs) {
                // ── LOST: No heartbeat beyond configured timeout ──
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

            } else if (elapsed > staleThresholdMs && "ACTIVE".equals(drone.getStatus())) {
                // ── STALE: Heartbeat delayed beyond stale threshold ──
                drone.setStatus("STALE");
                staleCount++;

                String msg = String.format("[WARN] Drone_%d heartbeat stale (%dms)", drone.getDroneId(), elapsed);
                log.warn(msg);
                orchestrator.broadcastLog("WARN", msg);

            } else if (elapsed <= staleThresholdMs && "STALE".equals(drone.getStatus())) {
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

        // Throttled heartbeat summary — every 5th cycle
        heartbeatCycleCount++;
        if (heartbeatCycleCount >= 5) {
            heartbeatCycleCount = 0;
            int totalActive = (int) registry.values().stream()
                    .filter(d -> !"LOST".equals(d.getStatus())).count();
            if (totalActive > 0) {
                orchestrator.broadcastLog("HEARTBEAT",
                        String.format("[HEARTBEAT] %d drones reporting nominal", totalActive));
            }
        }
    }
}
