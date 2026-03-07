package com.rsgcs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single mission waypoint placed by the operator on the tactical map.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Waypoint {

    private String id;
    private double latitude;
    private double longitude;
    private double altitude; // meters — default 100m
    private int order; // sequence position in the route (1-based)
}
