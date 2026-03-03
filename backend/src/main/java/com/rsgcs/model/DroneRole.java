package com.rsgcs.model;

/**
 * Drone role in the swarm hierarchy.
 * Drives the Bully Algorithm leader election.
 */
public enum DroneRole {
    LEADER, // Currently commanding the swarm
    FOLLOWER, // Executing orders from leader
    CANDIDATE, // Participating in an election
    LOST // Heartbeat timeout — presumed dead
}
