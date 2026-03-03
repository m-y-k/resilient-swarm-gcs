"""
Waypoint pattern generators for drone swarm search missions.
Centered on Greater Noida (28.4595, 77.5021) — near NRT's office.
"""

import math

# 1 degree latitude ≈ 111,320 meters
METERS_PER_DEG_LAT = 111_320.0


def grid_search(center_lat, center_lon, num_drones, area_size_meters=500):
    """
    Generate a grid search (lawnmower) pattern.
    Divides a square area into vertical strips — one per drone.
    Each drone flies a zigzag back-and-forth pattern in its strip.

    Returns: dict of { drone_id: [(lat, lon), ...] }
    """
    area_deg_lat = area_size_meters / METERS_PER_DEG_LAT
    area_deg_lon = area_size_meters / (METERS_PER_DEG_LAT * math.cos(math.radians(center_lat)))

    half_lat = area_deg_lat / 2.0
    half_lon = area_deg_lon / 2.0

    min_lat = center_lat - half_lat
    max_lat = center_lat + half_lat
    min_lon = center_lon - half_lon

    strip_width = area_deg_lon / num_drones

    waypoints = {}
    for i in range(1, num_drones + 1):
        drone_waypoints = []
        strip_center_lon = min_lon + (i - 0.5) * strip_width

        # 6 waypoints per strip — zigzag lawnmower
        num_legs = 6
        for leg in range(num_legs):
            lat_fraction = leg / (num_legs - 1)

            if leg % 2 == 0:
                lat = min_lat + lat_fraction * (max_lat - min_lat)
            else:
                lat = max_lat - lat_fraction * (max_lat - min_lat)

            # Slight lateral offset for natural movement
            lon_offset = (-1 if leg % 2 == 0 else 1) * strip_width * 0.1
            drone_waypoints.append((lat, strip_center_lon + lon_offset))

        waypoints[i] = drone_waypoints

    return waypoints


def circular_orbit(center_lat, center_lon, radius_meters, num_drones):
    """
    Generate concentric circular orbit paths for drones.
    Each drone gets a slightly different radius and angular offset.

    Returns: dict of { drone_id: [(lat, lon), ...] }
    """
    radius_deg_lat = radius_meters / METERS_PER_DEG_LAT
    radius_deg_lon = radius_meters / (METERS_PER_DEG_LAT * math.cos(math.radians(center_lat)))

    waypoints = {}
    points_per_circle = 12

    for i in range(1, num_drones + 1):
        drone_waypoints = []
        scale = 0.5 + (0.5 * i / num_drones)
        angle_offset = (2 * math.pi * i) / num_drones

        for p in range(points_per_circle):
            angle = angle_offset + (2 * math.pi * p / points_per_circle)
            lat = center_lat + radius_deg_lat * scale * math.sin(angle)
            lon = center_lon + radius_deg_lon * scale * math.cos(angle)
            drone_waypoints.append((lat, lon))

        waypoints[i] = drone_waypoints

    return waypoints
