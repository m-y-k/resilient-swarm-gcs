package com.rsgcs.service;

import com.rsgcs.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central brain of the swarm — holds ALL drone state in a ConcurrentHashMap.
 * Receives telemetry, manages kills, and broadcasts updates to the frontend via
 * STOMP.
 */
@Slf4j
@Service
public class SwarmOrchestrator {

    private final Map<Integer, DroneState> droneRegistry = new ConcurrentHashMap<>();
    private final MissionState missionState = MissionState.createIdle(10);
    private final SimpMessagingTemplate messagingTemplate;
    private final List<ElectionEvent> eventLog = Collections.synchronizedList(new ArrayList<>());

    // Track explicitly killed drones — ignore their telemetry
    private final Set<Integer> killedDrones = ConcurrentHashMap.newKeySet();

    // Track last N positions for path trails on the map
    private final Map<Integer, List<double[]>> positionHistory = new ConcurrentHashMap<>();
    private static final int MAX_TRAIL_LENGTH = 10;

    public SwarmOrchestrator(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Process incoming telemetry from a Python drone simulator.
     */
    public void handleTelemetry(TelemetryPacket packet) {
        int id = packet.getSystemId();

        // Ignore telemetry from explicitly killed drones
        if (killedDrones.contains(id)) {
            return;
        }

        DroneState drone = droneRegistry.computeIfAbsent(id, droneId -> {
            DroneState newDrone = DroneState.builder()
                    .droneId(droneId)
                    .droneType(DroneState.typeForId(droneId))
                    .role(droneId == getHighestActiveId() || droneRegistry.isEmpty() ? DroneRole.LEADER
                            : DroneRole.FOLLOWER)
                    .status("ACTIVE")
                    .batteryPercent(100.0)
                    .lastHeartbeat(Instant.now())
                    .build();
            log.info("[REGISTER] Drone_{} registered as {} ({})", droneId, newDrone.getRole(), newDrone.getDroneType());
            return newDrone;
        });

        // Check for sequence gaps
        if (packet.getSequenceNumber() > drone.getSequenceNumber() + 1 && drone.getSequenceNumber() > 0) {
            long gap = packet.getSequenceNumber() - drone.getSequenceNumber() - 1;
            log.warn("[PACKET_LOSS] Drone_{} — {} packets lost (seq {} → {})", id, gap, drone.getSequenceNumber(),
                    packet.getSequenceNumber());
        }

        // If drone was LOST and is now sending again
        if ("LOST".equals(drone.getStatus()) || drone.getRole() == DroneRole.LOST) {
            drone.setRole(DroneRole.FOLLOWER);
            drone.setStatus("ACTIVE");
            String msg = String.format("[RECONNECT] Drone_%d RECONNECTED — resuming as FOLLOWER", id);
            log.info(msg);
            broadcastLog("INFO", msg);
            missionState.setActiveDroneCount((int) droneRegistry.values().stream()
                    .filter(d -> !"LOST".equals(d.getStatus())).count());
        }

        // Update all fields
        drone.setLatitude(packet.getLatitude());
        drone.setLongitude(packet.getLongitude());
        drone.setAltitude(packet.getAltitude());
        drone.setHeading(packet.getHeading());
        drone.setSpeed(packet.getSpeed());
        drone.setBatteryPercent(packet.getBatteryPercent());
        drone.setLastHeartbeat(Instant.now());
        drone.setSequenceNumber(packet.getSequenceNumber());
        if (!"LOST".equals(drone.getStatus())) {
            drone.setStatus("ACTIVE");
        }

        // Track position history for trail rendering
        positionHistory.computeIfAbsent(id, k -> Collections.synchronizedList(new ArrayList<>()));
        List<double[]> trail = positionHistory.get(id);
        trail.add(new double[] { packet.getLatitude(), packet.getLongitude() });
        if (trail.size() > MAX_TRAIL_LENGTH) {
            trail.remove(0);
        }

        // Update active drone count
        missionState.setActiveDroneCount((int) droneRegistry.values().stream()
                .filter(d -> !"LOST".equals(d.getStatus())).count());

        // Broadcast to frontend (throttled — only via scheduled broadcast, not per
        // packet)
    }

    /**
     * Kill a specific drone — mark as LOST, trigger election if it was the leader.
     */
    public String killDrone(int droneId) {
        DroneState drone = droneRegistry.get(droneId);
        if (drone == null) {
            return "Drone " + droneId + " not found";
        }

        boolean wasLeader = drone.getRole() == DroneRole.LEADER;
        drone.setStatus("LOST");
        drone.setRole(DroneRole.LOST);
        killedDrones.add(droneId);

        String msg = String.format("[KILL] Drone_%d TERMINATED%s", droneId, wasLeader ? " (WAS LEADER)" : "");
        log.warn(msg);
        broadcastLog("CRITICAL", msg);

        // Update active count
        missionState.setActiveDroneCount((int) droneRegistry.values().stream()
                .filter(d -> !"LOST".equals(d.getStatus())).count());

        // Broadcast the kill event
        messagingTemplate.convertAndSend("/topic/drones", getSwarmSnapshot());
        messagingTemplate.convertAndSend("/topic/mission", missionState);

        return wasLeader ? "LEADER_KILLED" : "DRONE_KILLED";
    }

    /**
     * Returns the current leader's drone ID, or -1 if no leader.
     */
    public int getCurrentLeaderId() {
        return droneRegistry.values().stream()
                .filter(d -> d.getRole() == DroneRole.LEADER)
                .mapToInt(DroneState::getDroneId)
                .findFirst()
                .orElse(-1);
    }

    /**
     * Get the highest active drone ID (for initial leader assignment).
     */
    public int getHighestActiveId() {
        return droneRegistry.values().stream()
                .filter(d -> !"LOST".equals(d.getStatus()))
                .mapToInt(DroneState::getDroneId)
                .max()
                .orElse(0);
    }

    /**
     * Full snapshot for frontend initial load and periodic broadcast.
     */
    public Map<String, Object> getSwarmSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("drones", new ArrayList<>(droneRegistry.values()));
        snapshot.put("mission", missionState);
        snapshot.put("trails", positionHistory);
        return snapshot;
    }

    public DroneState getDroneById(int id) {
        return droneRegistry.get(id);
    }

    public Map<Integer, DroneState> getDroneRegistry() {
        return droneRegistry;
    }

    public MissionState getMissionState() {
        return missionState;
    }

    public List<ElectionEvent> getEventLog() {
        return eventLog;
    }

    public Set<Integer> getKilledDroneIds() {
        return killedDrones;
    }

    /**
     * Broadcast a log line to the frontend terminal.
     */
    public void broadcastLog(String level, String message) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("level", level);
        logEntry.put("message", message);
        logEntry.put("timestamp", Instant.now().toString());
        messagingTemplate.convertAndSend("/topic/logs", logEntry);
    }

    /**
     * Broadcast an election event to the frontend.
     */
    public void broadcastEvent(ElectionEvent event) {
        eventLog.add(event);
        // Keep event log bounded
        if (eventLog.size() > 200) {
            eventLog.remove(0);
        }
        messagingTemplate.convertAndSend("/topic/events", event);
    }

    /**
     * Periodic broadcast of full swarm state to all frontend clients.
     * Called by a scheduled task to avoid flooding WebSocket.
     */
    public void broadcastSwarmState() {
        if (!droneRegistry.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/drones", getSwarmSnapshot());
            messagingTemplate.convertAndSend("/topic/mission", missionState);
        }
    }

    /**
     * Reset all drones and mission state.
     */
    public void resetAll() {
        droneRegistry.clear();
        positionHistory.clear();
        eventLog.clear();
        killedDrones.clear();
        missionState.setStatus("IDLE");
        missionState.setCurrentLeaderId(0);
        missionState.setActiveDroneCount(0);
        missionState.setElectionCount(0);
        missionState.setMissionStartTime(null);
        missionState.setLastElectionTime(null);
        log.info("[RESET] All swarm state cleared");
        broadcastLog("INFO", "[RESET] Swarm state reset — awaiting drone connections");
        messagingTemplate.convertAndSend("/topic/mission", missionState);
    }

    /**
     * Start the mission — set status to ACTIVE, assign leader.
     */
    public void startMission() {
        if (droneRegistry.isEmpty()) {
            log.warn("[MISSION] Cannot start — no drones registered");
            return;
        }

        // Assign the highest-ID drone as leader
        int leaderId = getHighestActiveId();
        droneRegistry.values().forEach(d -> d.setRole(DroneRole.FOLLOWER));
        DroneState leader = droneRegistry.get(leaderId);
        if (leader != null) {
            leader.setRole(DroneRole.LEADER);
        }

        missionState.setStatus("ACTIVE");
        missionState.setCurrentLeaderId(leaderId);
        missionState.setMissionStartTime(Instant.now());
        missionState.setActiveDroneCount((int) droneRegistry.values().stream()
                .filter(d -> !"LOST".equals(d.getStatus())).count());

        String msg = String.format("[MISSION] Mission ACTIVE — Leader: Drone_%d — %d drones online",
                leaderId, missionState.getActiveDroneCount());
        log.info(msg);
        broadcastLog("SUCCESS", msg);
        messagingTemplate.convertAndSend("/topic/mission", missionState);
        broadcastSwarmState();
    }
}
