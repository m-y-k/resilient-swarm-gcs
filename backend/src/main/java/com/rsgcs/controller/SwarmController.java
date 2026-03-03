package com.rsgcs.controller;

import com.rsgcs.service.SwarmOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Swarm snapshot endpoint for frontend initial load.
 */
@RestController
@RequestMapping("/api/swarm")
@RequiredArgsConstructor
public class SwarmController {

    private final SwarmOrchestrator orchestrator;

    /**
     * Full swarm state snapshot — drones, mission, trails.
     * Called by frontend on initial page load.
     */
    @GetMapping("/snapshot")
    public ResponseEntity<Map<String, Object>> getSnapshot() {
        return ResponseEntity.ok(orchestrator.getSwarmSnapshot());
    }
}
