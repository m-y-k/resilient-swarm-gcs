package com.rsgcs.controller;

import com.rsgcs.service.SwarmOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Random;

/**
 * REST endpoints for frontend command buttons.
 * Kill drones, start missions, reset state.
 */
@Slf4j
@RestController
@RequestMapping("/api/command")
@RequiredArgsConstructor
public class CommandController {

    private final SwarmOrchestrator orchestrator;
    private final Random random = new Random();

    /**
     * Kill a specific drone by ID.
     */
    @PostMapping("/kill/{droneId}")
    public ResponseEntity<Map<String, String>> killDrone(@PathVariable int droneId) {
        log.info("[COMMAND] Kill order received for Drone_{}", droneId);
        String result = orchestrator.killDrone(droneId);
        return ResponseEntity.ok(Map.of(
                "status", result,
                "message", "Drone " + droneId + " terminated"));
    }

    /**
     * Kill the current leader — triggers emergency election.
     */
    @PostMapping("/kill-leader")
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

    /**
     * Kill a random non-leader active drone.
     */
    @PostMapping("/kill-random")
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

    /**
     * Start the swarm mission.
     */
    @PostMapping("/start-mission")
    public ResponseEntity<Map<String, String>> startMission() {
        log.info("[COMMAND] Mission start order received");
        orchestrator.startMission();
        return ResponseEntity.ok(Map.of(
                "status", "MISSION_STARTED",
                "message", "Swarm mission activated"));
    }

    /**
     * Reset all swarm state.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        log.info("[COMMAND] Full swarm reset");
        orchestrator.resetAll();
        return ResponseEntity.ok(Map.of(
                "status", "RESET",
                "message", "Swarm state cleared — awaiting reconnection"));
    }
}
