package com.rsgcs.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Generates waypoint lists for drone search patterns.
 * Centered on Greater Noida (28.4595, 77.5021) — near NRT's office.
 */
@Slf4j
@Service
public class MissionCoordinator {

    // 1 degree latitude ≈ 111,320 meters
    private static final double METERS_PER_DEG_LAT = 111_320.0;

    /**
     * Generate a grid search ("lawnmower") pattern.
     * Divides a square area into vertical strips — one per drone.
     *
     * @param centerLat      center latitude (default: 28.4595)
     * @param centerLon      center longitude (default: 77.5021)
     * @param droneCount     number of drones in the swarm
     * @param areaSizeMeters side length of the search area (default: 500m)
     * @return droneId → list of [lat, lon] waypoints
     */
    public Map<Integer, List<double[]>> generateGridSearchPattern(
            double centerLat, double centerLon, int droneCount, double areaSizeMeters) {

        // Convert meters to approximate degrees
        double areaDegreesLat = areaSizeMeters / METERS_PER_DEG_LAT;
        double areaDegreesLon = areaSizeMeters / (METERS_PER_DEG_LAT * Math.cos(Math.toRadians(centerLat)));

        double halfLat = areaDegreesLat / 2.0;
        double halfLon = areaDegreesLon / 2.0;

        // Area bounds
        double minLat = centerLat - halfLat;
        double maxLat = centerLat + halfLat;
        double minLon = centerLon - halfLon;

        // Strip width in longitude
        double stripWidth = areaDegreesLon / droneCount;

        Map<Integer, List<double[]>> waypoints = new HashMap<>();

        for (int i = 1; i <= droneCount; i++) {
            List<double[]> droneWaypoints = new ArrayList<>();
            double stripCenterLon = minLon + (i - 0.5) * stripWidth;

            // Create zigzag lawnmower pattern (6 waypoints per strip)
            int numLegs = 6;
            for (int leg = 0; leg < numLegs; leg++) {
                double latFraction = (double) leg / (numLegs - 1);
                double lat;

                // Zigzag: even legs go south→north, odd legs go north→south
                if (leg % 2 == 0) {
                    lat = minLat + latFraction * (maxLat - minLat);
                } else {
                    lat = maxLat - latFraction * (maxLat - minLat);
                }

                // Slight lateral offset for more natural movement
                double lonOffset = (leg % 2 == 0 ? -1 : 1) * stripWidth * 0.1;
                droneWaypoints.add(new double[] { lat, stripCenterLon + lonOffset });
            }

            waypoints.put(i, droneWaypoints);
        }

        log.info("[MISSION] Generated grid search pattern: {}m² area, {} drones, {} waypoints each",
                (int) areaSizeMeters, droneCount, 6);

        return waypoints;
    }

    /**
     * Generate a circular orbit pattern for drones.
     */
    public Map<Integer, List<double[]>> generateCircularOrbit(
            double centerLat, double centerLon, double radiusMeters, int droneCount) {

        double radiusDegLat = radiusMeters / METERS_PER_DEG_LAT;
        double radiusDegLon = radiusMeters / (METERS_PER_DEG_LAT * Math.cos(Math.toRadians(centerLat)));

        Map<Integer, List<double[]>> waypoints = new HashMap<>();
        int pointsPerCircle = 12;

        for (int i = 1; i <= droneCount; i++) {
            List<double[]> droneWaypoints = new ArrayList<>();
            // Each drone gets a slightly different radius for concentric circles
            double scale = 0.5 + (0.5 * i / droneCount);
            double angleOffset = (2 * Math.PI * i) / droneCount;

            for (int p = 0; p < pointsPerCircle; p++) {
                double angle = angleOffset + (2 * Math.PI * p / pointsPerCircle);
                double lat = centerLat + radiusDegLat * scale * Math.sin(angle);
                double lon = centerLon + radiusDegLon * scale * Math.cos(angle);
                droneWaypoints.add(new double[] { lat, lon });
            }

            waypoints.put(i, droneWaypoints);
        }

        return waypoints;
    }
}
