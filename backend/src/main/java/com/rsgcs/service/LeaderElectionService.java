package com.rsgcs.service;

import com.rsgcs.model.DroneRole;
import com.rsgcs.model.DroneState;
import com.rsgcs.model.ElectionEvent;
import com.rsgcs.model.MissionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * THE MONEY CLASS — Bully Algorithm implementation for leader election.
 * This is what gets the interview. Sub-500ms leader re-election.
 */
@Slf4j
@Service
public class LeaderElectionService {

    private final AtomicBoolean electionInProgress = new AtomicBoolean(false);
    private final SwarmOrchestrator orchestrator;

    public LeaderElectionService(@Lazy SwarmOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public boolean isElectionInProgress() {
        return electionInProgress.get();
    }

    /**
     * BULLY ALGORITHM — triggered when leader drone is detected as LOST.
     * 
     * Steps:
     * 1. Detection log
     * 2. Identify candidates (all non-LOST drones)
     * 3. Simulate election messages with dramatic delays
     * 4. Promote highest-ID active drone as new leader
     * 5. Quorum check for split-brain prevention
     * 6. Resume mission
     */
    public void startElection() {
        // Prevent concurrent elections
        if (!electionInProgress.compareAndSet(false, true)) {
            log.info("[ELECTION] Election already in progress — skipping duplicate trigger");
            return;
        }

        MissionState mission = orchestrator.getMissionState();
        int oldLeaderId = mission.getCurrentLeaderId();

        try {
            // ── STEP 1: DETECTION LOG ──
            String detectMsg = String.format("[CRITICAL] LEADER_TIMEOUT: Drone_%d — initiating election protocol",
                    oldLeaderId);
            log.warn(detectMsg);
            orchestrator.broadcastLog("CRITICAL", detectMsg);
            broadcastElectionEvent("LEADER_TIMEOUT", oldLeaderId, 0, detectMsg);

            mission.setStatus("RECOVERING");
            orchestrator.broadcastLog("ELECTION", "[SYSTEM] Mission status → RECOVERING");

            // Small delay for dramatic effect
            sleep(80);

            // ── STEP 2: IDENTIFY CANDIDATES ──
            List<DroneState> candidates = orchestrator.getDroneRegistry().values().stream()
                    .filter(d -> !"LOST".equals(d.getStatus()) && d.getRole() != DroneRole.LOST)
                    .sorted(Comparator.comparingInt(DroneState::getDroneId).reversed())
                    .collect(Collectors.toList());

            if (candidates.isEmpty()) {
                String noNodes = "[CRITICAL] ELECTION_FAIL: No active drones available for election";
                log.error(noNodes);
                orchestrator.broadcastLog("CRITICAL", noNodes);
                mission.setStatus("PAUSED");
                return;
            }

            // ── STEP 3: QUORUM CHECK (Raft-lite) ──
            int totalDrones = mission.getTotalDroneCount();
            if (candidates.size() <= totalDrones / 2) {
                String quorumFail = String.format(
                        "[CRITICAL] QUORUM_FAIL: Only %d/%d drones reachable. Election aborted. Entering SAFE MODE.",
                        candidates.size(), totalDrones);
                log.error(quorumFail);
                orchestrator.broadcastLog("CRITICAL", quorumFail);
                broadcastElectionEvent("QUORUM_FAIL", 0, 0, quorumFail);
                mission.setStatus("PAUSED");
                return;
            }

            // ── STEP 4: BULLY ELECTION (with dramatic messages) ──
            // The second-highest active drone "initiates" the election
            DroneState highestDrone = candidates.get(0);
            int initiatorId = candidates.size() > 1 ? candidates.get(1).getDroneId() : highestDrone.getDroneId();

            // Mark all candidates as CANDIDATE during election
            candidates.forEach(d -> d.setRole(DroneRole.CANDIDATE));

            String startMsg = String.format("[INFO] ELECTION_START: Initiated by Drone_%d", initiatorId);
            log.info(startMsg);
            orchestrator.broadcastLog("ELECTION", startMsg);
            broadcastElectionEvent("ELECTION_START", initiatorId, 0, startMsg);

            sleep(60);

            // Send ELECTION messages to all higher-ID drones
            for (DroneState target : candidates) {
                if (target.getDroneId() > initiatorId) {
                    String electionMsg = String.format("[INFO] ELECTION_MSG: Drone_%d → Drone_%d", initiatorId,
                            target.getDroneId());
                    log.info(electionMsg);
                    orchestrator.broadcastLog("ELECTION", electionMsg);
                    broadcastElectionEvent("ELECTION_MSG", initiatorId, target.getDroneId(), electionMsg);
                    sleep(40);
                }
            }

            // Highest-ID drone responds
            String responseMsg = String.format("[INFO] ELECTION_RESPONSE: Drone_%d asserts leadership",
                    highestDrone.getDroneId());
            log.info(responseMsg);
            orchestrator.broadcastLog("ELECTION", responseMsg);
            broadcastElectionEvent("ELECTION_RESPONSE", highestDrone.getDroneId(), 0, responseMsg);

            sleep(60);

            // Coordinator broadcast
            String coordinatorMsg = String.format("[SUCCESS] NEW_LEADER: Drone_%d confirmed as Swarm Leader",
                    highestDrone.getDroneId());
            log.info(coordinatorMsg);
            orchestrator.broadcastLog("SUCCESS", coordinatorMsg);
            broadcastElectionEvent("NEW_LEADER", highestDrone.getDroneId(), 0, coordinatorMsg);

            // ── STEP 5: PROMOTE NEW LEADER ──
            highestDrone.setRole(DroneRole.LEADER);
            candidates.stream()
                    .filter(d -> d.getDroneId() != highestDrone.getDroneId())
                    .forEach(d -> d.setRole(DroneRole.FOLLOWER));

            mission.setCurrentLeaderId(highestDrone.getDroneId());
            mission.setStatus("ACTIVE");
            mission.setLastElectionTime(Instant.now());
            mission.setElectionCount(mission.getElectionCount() + 1);

            // ── STEP 6: MISSION CONTINUITY ──
            String syncMsg = String.format("[INFO] MISSION_SYNC: Swarm pivoting to Drone_%d command vector",
                    highestDrone.getDroneId());
            log.info(syncMsg);
            orchestrator.broadcastLog("INFO", syncMsg);

            // Broadcast full updated state
            orchestrator.broadcastSwarmState();

        } finally {
            electionInProgress.set(false);
        }
    }

    private void broadcastElectionEvent(String eventType, int initiatorId, int targetId, String message) {
        ElectionEvent event = ElectionEvent.builder()
                .timestamp(Instant.now())
                .eventType(eventType)
                .initiatorId(initiatorId)
                .targetId(targetId)
                .message(message)
                .build();
        orchestrator.broadcastEvent(event);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
