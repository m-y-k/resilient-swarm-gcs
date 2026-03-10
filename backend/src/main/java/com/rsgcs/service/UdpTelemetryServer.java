package com.rsgcs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsgcs.model.TelemetryPacket;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/**
 * UDP telemetry receiver — replaces HTTP POST for drone heartbeats.
 * UDP is connectionless, zero-handshake, and ideal for high-frequency telemetry.
 * Runs on port 9090 by default.
 */
@Slf4j
@Service
public class UdpTelemetryServer {

    private final SwarmOrchestrator orchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rsgcs.udp-port:9090}")
    private int udpPort;

    private DatagramSocket socket;
    private volatile boolean running = true;

    public UdpTelemetryServer(SwarmOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostConstruct
    public void start() {
        Thread.startVirtualThread(this::listenLoop);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    private void listenLoop() {
        try {
            socket = new DatagramSocket(udpPort);
            byte[] buffer = new byte[2048];
            log.info("[UDP] Telemetry server listening on port {}", udpPort);

            while (running) {
                DatagramPacket dgram = new DatagramPacket(buffer, buffer.length);
                socket.receive(dgram);

                String json = new String(dgram.getData(), 0, dgram.getLength(), StandardCharsets.UTF_8);
                try {
                    TelemetryPacket packet = objectMapper.readValue(json, TelemetryPacket.class);
                    orchestrator.handleTelemetry(packet);
                } catch (Exception e) {
                    log.debug("[UDP] Bad packet: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            if (running) {
                log.error("[UDP] Server error: {}", e.getMessage());
            }
        }
    }
}
