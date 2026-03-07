package com.rsgcs.service;

import com.rsgcs.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final LeaderElectionService electionService;
    private final PathfindingService pathfindingService;
    private final List<ElectionEvent> eventLog = Collections.synchronizedList(new ArrayList<>());

    // Track explicitly killed drones — ignore their telemetry
    private final Set<Integer> killedDrones = ConcurrentHashMap.newKeySet();

    // Track last N positions for path trails on the map
    private final Map<Integer, List<double[]>> positionHistory = new ConcurrentHashMap<>();
    private static final int MAX_TRAIL_LENGTH = 10;

    // Obstacles — pre-loaded with Greater Noida landmarks
    private final List<Obstacle> obstacles = new CopyOnWriteArrayList<>(createDefaultObstacles());

    // Per-drone waypoints (calculated from operator mission waypoints)
    private final Map<Integer, List<double[]>> droneWaypoints = new ConcurrentHashMap<>();

    // Track last planned path calculation to prevent STOMP thread pool exhaustion
    private final Map<Integer, Instant> lastPathCalc = new ConcurrentHashMap<>();

    // Operator-configurable spawn point for drone formation center
    private volatile double[] spawnPoint = null;

    private static final double METERS_PER_DEG_LAT = 111_320.0;

    public SwarmOrchestrator(SimpMessagingTemplate messagingTemplate,
            @Lazy LeaderElectionService electionService,
            PathfindingService pathfindingService) {
        this.messagingTemplate = messagingTemplate;
        this.electionService = electionService;
        this.pathfindingService = pathfindingService;
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
        drone.setCurrentWaypointIndex(packet.getCurrentWaypointIndex());
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

        // Compute planned path through obstacles using A* grid pathfinding
        if (!obstacles.isEmpty() && drone.getStatus().equals("ACTIVE")) {
            List<double[]> myWaypoints = droneWaypoints.get(id);
            if (myWaypoints != null && !myWaypoints.isEmpty()
                    && packet.getCurrentWaypointIndex() < myWaypoints.size()) {
                double[] nextWp = myWaypoints.get(packet.getCurrentWaypointIndex());

                Instant lastCalc = lastPathCalc.get(id);
                Instant now = Instant.now();
                if (lastCalc == null || java.time.Duration.between(lastCalc, now).toMillis() > 2000) {
                    lastPathCalc.put(id, now);
                    Thread.startVirtualThread(() -> {
                        try {
                            List<double[]> plannedPath = pathfindingService.calculatePlannedPath(
                                    drone.getLatitude(), drone.getLongitude(),
                                    nextWp[0], nextWp[1], obstacles);
                            drone.setPlannedPath(plannedPath);
                        } catch (Exception e) {
                            log.error("Pathfinding error for Drone_{}", id, e);
                        }
                    });
                }
            } else {
                drone.setPlannedPath(null);
            }
        }

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

        // Trigger election if the killed drone was the leader
        if (wasLeader && !electionService.isElectionInProgress()) {
            log.warn("[KILL] Leader killed — triggering emergency election");
            Thread.startVirtualThread(() -> electionService.startElection());
        }

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

    // ── Spawn Point Management ─────────────────────────────────

    public double[] getSpawnPoint() {
        return spawnPoint;
    }

    public void setSpawnPoint(double latitude, double longitude) {
        this.spawnPoint = new double[] { latitude, longitude };
        String msg = String.format("[SPAWN] Spawn point set to (%.6f, %.6f)", latitude, longitude);
        log.info(msg);
        broadcastLog("INFO", msg);
    }

    // ── Obstacle Management ────────────────────────────────────

    public List<Obstacle> getObstacles() {
        return obstacles;
    }

    public void addObstacle(Obstacle obstacle) {
        obstacles.add(obstacle);
        log.info("[OBSTACLE] Added: {} ({}m radius) at ({}, {})",
                obstacle.getName(), obstacle.getRadius(), obstacle.getLatitude(), obstacle.getLongitude());
        broadcastLog("INFO", String.format("[OBSTACLE] Added: %s — %s", obstacle.getName(), obstacle.getType()));
        messagingTemplate.convertAndSend("/topic/mission", missionState);
    }

    public boolean removeObstacle(String obstacleId) {
        boolean removed = obstacles.removeIf(o -> o.getId().equals(obstacleId));
        if (removed) {
            log.info("[OBSTACLE] Removed: {}", obstacleId);
            broadcastLog("INFO", String.format("[OBSTACLE] Removed obstacle %s", obstacleId));
        }
        return removed;
    }

    // ── Waypoint / Mission Planning ────────────────────────────

    public Map<Integer, List<double[]>> getDroneWaypoints() {
        return droneWaypoints;
    }

    /**
     * Set mission waypoints from operator click-to-place planning.
     * Distributes offset paths to each drone for formation flying.
     */
    public void setMissionWaypoints(List<Waypoint> waypoints) {
        missionState.setMissionWaypoints(waypoints);
        missionState.setMissionPattern("CUSTOM");

        // Build the base path from waypoints (ordered)
        waypoints.sort(Comparator.comparingInt(Waypoint::getOrder));
        List<double[]> basePath = waypoints.stream()
                .map(w -> new double[] { w.getLatitude(), w.getLongitude() })
                .collect(Collectors.toList());

        if (basePath.isEmpty())
            return;

        // Distribute to drones with formation offsets
        int leaderId = getCurrentLeaderId();
        List<DroneState> activeDrones = droneRegistry.values().stream()
                .filter(d -> !"LOST".equals(d.getStatus()))
                .sorted(Comparator.comparingInt(DroneState::getDroneId))
                .collect(Collectors.toList());

        int index = 0;
        for (DroneState drone : activeDrones) {
            if (drone.getDroneId() == leaderId) {
                // Leader flies the exact path
                droneWaypoints.put(drone.getDroneId(), new ArrayList<>(basePath));
            } else {
                // Followers get offset paths: ±15m, ±30m, ±45m (ensure gaps > 5m)
                index++;
                int side = (index % 2 == 0) ? 1 : -1;
                double offsetMeters = 15.0 * ((index + 1) / 2);
                List<double[]> offsetPath = calculateOffsetPath(basePath, offsetMeters * side);
                droneWaypoints.put(drone.getDroneId(), offsetPath);
            }
        }

        String msg = String.format("[MISSION] Operator waypoints deployed — %d waypoints, %d drones in formation",
                waypoints.size(), activeDrones.size());
        log.info(msg);
        broadcastLog("SUCCESS", msg);
        messagingTemplate.convertAndSend("/topic/mission", missionState);
        broadcastSwarmState();
    }

    /**
     * Calculate a parallel offset path for formation flying.
     */
    private List<double[]> calculateOffsetPath(List<double[]> basePath, double offsetMeters) {
        List<double[]> offsetPath = new ArrayList<>();
        for (int i = 0; i < basePath.size(); i++) {
            double[] current = basePath.get(i);
            double[] next = (i < basePath.size() - 1) ? basePath.get(i + 1) : basePath.get(i);
            double[] prev = (i > 0) ? basePath.get(i - 1) : basePath.get(i);

            // Direction vector
            double dx = next[1] - prev[1];
            double dy = next[0] - prev[0];
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len == 0) {
                offsetPath.add(current.clone());
                continue;
            }

            // Perpendicular vector
            double perpX = -dy / len;
            double perpY = dx / len;

            // Convert meters to degrees
            double offsetLat = (offsetMeters * perpY) / METERS_PER_DEG_LAT;
            double offsetLon = (offsetMeters * perpX) / (METERS_PER_DEG_LAT * Math.cos(Math.toRadians(current[0])));

            offsetPath.add(new double[] {
                    current[0] + offsetLat,
                    current[1] + offsetLon
            });
        }
        return offsetPath;
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
        droneWaypoints.clear();
        missionState.setStatus("IDLE");
        missionState.setCurrentLeaderId(0);
        missionState.setActiveDroneCount(0);
        missionState.setElectionCount(0);
        missionState.setMissionStartTime(null);
        missionState.setLastElectionTime(null);
        missionState.setMissionWaypoints(new ArrayList<>());
        missionState.setMissionPattern(null);
        // Reset obstacles to defaults
        obstacles.clear();
        obstacles.addAll(createDefaultObstacles());
        // Reset spawn point
        spawnPoint = null;
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

    // ── Default Greater Noida Obstacles ────────────────────────

    private static List<Obstacle> createDefaultObstacles() {
        return List.of(
                Obstacle.builder().id("obs_1").name("Gaur City Mall").type("BUILDING")
                        .latitude(28.4585).longitude(77.5005).radius(80).height(45).build(),
                Obstacle.builder().id("obs_2").name("Pari Chowk").type("RESTRICTED_ZONE")
                        .latitude(28.4650).longitude(77.5040).radius(150).height(0).build(),
                Obstacle.builder().id("obs_3").name("Gaur World SmartStreet Mall").type("BUILDING")
                        .latitude(28.4563).longitude(77.4982).radius(60).height(35).build(),
                Obstacle.builder().id("obs_4").name("JP Aman Society Tower").type("BUILDING")
                        .latitude(28.4620).longitude(77.5060).radius(50).height(60).build(),
                Obstacle.builder().id("obs_5").name("Noida-Greater Noida Expressway Overpass").type("INFRASTRUCTURE")
                        .latitude(28.4600).longitude(77.5025).radius(40).height(15).build(),
                Obstacle.builder().id("obs_6").name("Cell Tower Cluster (Sector 16)").type("RF_HAZARD")
                        .latitude(28.4575).longitude(77.5050).radius(60).height(50).build(),
                Obstacle.builder().id("obs_7").name("Water Tank (Sector 12)").type("STRUCTURE")
                        .latitude(28.4640).longitude(77.4990).radius(25).height(30).build(),
                Obstacle.builder().id("obs_8").name("Tree Line (Park Belt)").type("VEGETATION")
                        .latitude(28.4610).longitude(77.4970).radius(100).height(12).build());
    }
}
