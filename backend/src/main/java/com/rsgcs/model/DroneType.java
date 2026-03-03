package com.rsgcs.model;

/**
 * Heterogeneous drone types — demonstrates mixed-asset swarm management.
 * Type assignment: Drones 1-3 SURVEILLANCE, 4-6 LOGISTICS, 7-10 STRIKE.
 */
public enum DroneType {
    SURVEILLANCE, // Fast, low battery, cameras
    LOGISTICS, // Slow, high battery, heavy payload
    STRIKE // Medium speed, medium battery, armed
}
