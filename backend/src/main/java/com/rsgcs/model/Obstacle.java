package com.rsgcs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An obstacle / no-fly zone on the tactical map.
 * Pre-loaded with real Greater Noida landmarks + operator-added obstacles.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Obstacle {

    private String id;
    private String name;
    private String type; // BUILDING, RESTRICTED_ZONE, INFRASTRUCTURE, RF_HAZARD, VEGETATION
    private double latitude;
    private double longitude;
    private double radius; // meters — the no-fly buffer zone
    private double height; // meters — structure height
}
