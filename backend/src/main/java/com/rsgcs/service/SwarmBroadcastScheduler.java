package com.rsgcs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * High-frequency swarm broadcast scheduler.
 * Calls SwarmOrchestrator.broadcastSwarmState() at a tighter interval
 * so the frontend receives smoother position updates, without changing
 * the underlying UDP or STOMP architecture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SwarmBroadcastScheduler {

    private final SwarmOrchestrator orchestrator;

    /**
     * Broadcast full swarm state at a high frequency for smooth map motion.
     * Interval is configurable via rsgcs.broadcast-interval-ms (default 100ms).
     */
    @Scheduled(fixedRateString = "${rsgcs.broadcast-interval-ms:100}")
    public void broadcastSwarm() {
        orchestrator.broadcastSwarmState();
    }
}

