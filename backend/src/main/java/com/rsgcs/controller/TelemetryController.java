package com.rsgcs.controller;

import com.rsgcs.model.TelemetryPacket;
import com.rsgcs.service.SwarmOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoint for Python drone simulators to POST telemetry data.
 */
@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final SwarmOrchestrator orchestrator;

    /**
     * Single telemetry packet from one drone.
     */
    @PostMapping
    public ResponseEntity<String> receiveTelemetry(@RequestBody TelemetryPacket packet) {
        orchestrator.handleTelemetry(packet);
        return ResponseEntity.ok("OK");
    }

    /**
     * Batch telemetry — multiple packets at once for efficiency.
     */
    @PostMapping("/batch")
    public ResponseEntity<String> receiveBatch(@RequestBody List<TelemetryPacket> packets) {
        packets.forEach(orchestrator::handleTelemetry);
        return ResponseEntity.ok("OK — " + packets.size() + " packets processed");
    }
}
