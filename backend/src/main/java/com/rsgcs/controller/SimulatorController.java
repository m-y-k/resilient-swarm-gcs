package com.rsgcs.controller;

import com.rsgcs.model.Obstacle;
import com.rsgcs.service.SwarmOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Endpoints for the Python simulator to poll for kill commands, waypoints, and
 * obstacles.
 */
@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final SwarmOrchestrator orchestrator;

    /**
     * Returns list of drone IDs that have been killed.
     */
    @GetMapping("/commands")
    public ResponseEntity<List<Map<String, Object>>> getCommands() {
        Set<Integer> killed = orchestrator.getKilledDroneIds();
        List<Map<String, Object>> commands = killed.stream()
                .map(id -> Map.<String, Object>of("droneId", id, "action", "KILL"))
                .toList();
        return ResponseEntity.ok(commands);
    }

    /**
     * Returns per-drone waypoints for the simulator to follow.
     */
    @GetMapping("/waypoints")
    public ResponseEntity<Map<Integer, List<double[]>>> getWaypoints() {
        return ResponseEntity.ok(orchestrator.getDroneWaypoints());
    }

    /**
     * Returns current obstacle list for the simulator pathfinding.
     */
    @GetMapping("/obstacles")
    public ResponseEntity<List<Obstacle>> getObstacles() {
        return ResponseEntity.ok(orchestrator.getObstacles());
    }

    /**
     * Returns operator-configured spawn point, if any.
     */
    @GetMapping("/spawn-point")
    public ResponseEntity<Map<String, Double>> getSpawnPoint() {
        double[] sp = orchestrator.getSpawnPoint();
        if (sp == null) {
            return ResponseEntity.ok(Map.of());
        }
        return ResponseEntity.ok(Map.of("latitude", sp[0], "longitude", sp[1]));
    }

    /**
     * Returns the current mission status (IDLE, ACTIVE, PAUSED, etc.).
     */
    @GetMapping("/mission")
    public ResponseEntity<Map<String, String>> getMissionStatus() {
        return ResponseEntity.ok(Map.of("status", orchestrator.getMissionState().getStatus()));
    }
}
