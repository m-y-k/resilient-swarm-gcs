package com.rsgcs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Incoming telemetry from Python drone simulators.
 * Mimics a MAVLink HEARTBEAT + GLOBAL_POSITION_INT message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryPacket {

    private int systemId; // which drone — maps to droneId
    private int componentId; // always 1 for flight controller
    private long sequenceNumber; // monotonically increasing per drone
    private String messageType; // "HEARTBEAT", "POSITION", "STATUS"
    private double latitude;
    private double longitude;
    private double altitude;
    private double heading;
    private double speed;
    private double batteryPercent;
    private int currentWaypointIndex; // index of the waypoint the drone is navigating to
    private long timestamp; // epoch millis
}
