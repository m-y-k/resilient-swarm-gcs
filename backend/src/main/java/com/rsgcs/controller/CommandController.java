package com.rsgcs.controller;

import com.rsgcs.model.Obstacle;
import com.rsgcs.model.Waypoint;
import com.rsgcs.service.SwarmOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * REST endpoints for frontend command buttons.
 * Kill drones, start missions, reset state, manage waypoints and obstacles.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommandController {

    private final SwarmOrchestrator orchestrator;
    private final Random random = new Random();

    // ── Drone Commands ─────────────────────────────────────

    @PostMapping("/command/kill/{droneId}")
    public ResponseEntity<Map<String, String>> killDrone(@PathVariable int droneId) {
        log.info("[COMMAND] Kill order received for Drone_{}", droneId);
        String result = orchestrator.killDrone(droneId);
        return ResponseEntity.ok(Map.of(
                "status", result,
                "message", "Drone " + droneId + " terminated"));
    }

    @PostMapping("/command/kill-leader")
    public ResponseEntity<Map<String, String>> killLeader() {
        int leaderId = orchestrator.getCurrentLeaderId();
        if (leaderId <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "NO_LEADER",
                    "message", "No active leader to kill"));
        }
        log.warn("[COMMAND] KILL LEADER — Drone_{}", leaderId);
        String result = orchestrator.killDrone(leaderId);
        return ResponseEntity.ok(Map.of(
                "status", result,
                "message", "Leader Drone " + leaderId + " terminated — election triggered"));
    }

    @PostMapping("/command/kill-random")
    public ResponseEntity<Map<String, String>> killRandom() {
        var activeDrones = orchestrator.getDroneRegistry().values().stream()
                .filter(d -> !"LOST".equals(d.getStatus()))
                .filter(d -> d.getRole() != com.rsgcs.model.DroneRole.LEADER)
                .toList();

        if (activeDrones.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "NO_TARGET",
                    "message", "No active non-leader drones to kill"));
        }

        var target = activeDrones.get(random.nextInt(activeDrones.size()));
        log.info("[COMMAND] Kill random — targeting Drone_{}", target.getDroneId());
        String result = orchestrator.killDrone(target.getDroneId());
        return ResponseEntity.ok(Map.of(
                "status", result,
                "message", "Drone " + target.getDroneId() + " terminated (random)"));
    }

    @PostMapping("/command/start-mission")
    public ResponseEntity<Map<String, String>> startMission() {
        log.info("[COMMAND] Mission start order received");
        orchestrator.startMission();
        return ResponseEntity.ok(Map.of(
                "status", "MISSION_STARTED",
                "message", "Swarm mission activated"));
    }

    @PostMapping("/command/reset")
    public ResponseEntity<Map<String, String>> reset() {
        log.info("[COMMAND] Full swarm reset");
        orchestrator.resetAll();
        return ResponseEntity.ok(Map.of(
                "status", "RESET",
                "message", "Swarm state cleared — awaiting reconnection"));
    }

    // ── Waypoint Planning ──────────────────────────────────

    @PostMapping("/mission/waypoints")
    public ResponseEntity<Map<String, String>> deployMissionWaypoints(@RequestBody Map<String, List<Waypoint>> body) {
        List<Waypoint> waypoints = body.get("waypoints");
        if (waypoints == null || waypoints.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "INVALID",
                    "message", "No waypoints provided"));
        }
        log.info("[COMMAND] Deploying {} operator waypoints", waypoints.size());
        orchestrator.setMissionWaypoints(waypoints);
        return ResponseEntity.ok(Map.of(
                "status", "WAYPOINTS_DEPLOYED",
                "message", waypoints.size() + " waypoints deployed to swarm"));
    }

    // ── Obstacle Management ────────────────────────────────

    @GetMapping("/obstacles")
    public ResponseEntity<List<Obstacle>> getObstacles() {
        return ResponseEntity.ok(orchestrator.getObstacles());
    }

    @PostMapping("/obstacles")
    public ResponseEntity<Map<String, String>> addObstacle(@RequestBody Obstacle obstacle) {
        orchestrator.addObstacle(obstacle);
        return ResponseEntity.ok(Map.of(
                "status", "OBSTACLE_ADDED",
                "message", "Obstacle '" + obstacle.getName() + "' added"));
    }

    @DeleteMapping("/obstacles/{id}")
    public ResponseEntity<Map<String, String>> removeObstacle(@PathVariable String id) {
        boolean removed = orchestrator.removeObstacle(id);
        if (!removed) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "status", "OBSTACLE_REMOVED",
                "message", "Obstacle " + id + " removed"));
    }

    // ── Spawn Point ───────────────────────────────────────────

    @GetMapping("/spawn-point")
    public ResponseEntity<Map<String, Object>> getSpawnPoint() {
        double[] sp = orchestrator.getSpawnPoint();
        if (sp == null) {
            return ResponseEntity.ok(Map.of("set", false));
        }
        return ResponseEntity.ok(Map.of(
                "set", true,
                "latitude", sp[0],
                "longitude", sp[1]));
    }

    @PostMapping("/spawn-point")
    public ResponseEntity<Map<String, String>> setSpawnPoint(@RequestBody Map<String, Double> body) {
        Double lat = body.get("latitude");
        Double lon = body.get("longitude");
        if (lat == null || lon == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "INVALID",
                    "message", "latitude and longitude required"));
        }
        orchestrator.setSpawnPoint(lat, lon);
        return ResponseEntity.ok(Map.of(
                "status", "SPAWN_POINT_SET",
                "message", String.format("Spawn point set to (%.6f, %.6f)", lat, lon)));
    }
}
