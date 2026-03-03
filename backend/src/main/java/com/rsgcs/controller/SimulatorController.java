package com.rsgcs.controller;

import com.rsgcs.service.SwarmOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Endpoint for the Python simulator to check for kill commands.
 */
@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final SwarmOrchestrator orchestrator;

    /**
     * Returns list of drone IDs that have been killed.
     * The simulator polls this to stop sending telemetry for killed drones.
     */
    @GetMapping("/commands")
    public ResponseEntity<List<Map<String, Object>>> getCommands() {
        Set<Integer> killed = orchestrator.getKilledDroneIds();
        List<Map<String, Object>> commands = killed.stream()
                .map(id -> Map.<String, Object>of("droneId", id, "action", "KILL"))
                .toList();
        return ResponseEntity.ok(commands);
    }
}
