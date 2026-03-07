"""
Waypoint pattern generators for drone swarm search missions.
Centered on Greater Noida (28.4595, 77.5021) — near NRT's office.
Supports obstacle-aware waypoint generation to avoid spawning inside obstacles.
"""

import math

# 1 degree latitude ≈ 111,320 meters
METERS_PER_DEG_LAT = 111_320.0
SAFETY_BUFFER_METERS = 30.0


def _is_inside_obstacle(lat, lon, obstacles):
    """Check if a point is inside any obstacle circle (with safety buffer)."""
    for obs in obstacles:
        obs_radius_deg = (obs['radius'] + SAFETY_BUFFER_METERS) / METERS_PER_DEG_LAT
        cos_lat = math.cos(math.radians(lat))
        dlat = lat - obs['latitude']
        dlon = (lon - obs['longitude']) * cos_lat
        dist = math.sqrt(dlat ** 2 + dlon ** 2)
        if dist < obs_radius_deg:
            return True
    return False


def _push_outside_obstacles(lat, lon, obstacles):
    """If point is inside an obstacle, push it to the nearest edge + buffer."""
    for obs in obstacles:
        obs_radius_deg = (obs['radius'] + SAFETY_BUFFER_METERS) / METERS_PER_DEG_LAT
        cos_lat = math.cos(math.radians(lat))
        dlat = lat - obs['latitude']
        dlon = (lon - obs['longitude']) * cos_lat
        dist = math.sqrt(dlat ** 2 + dlon ** 2)
        if dist < obs_radius_deg:
            if dist == 0:
                # Exactly on center — push north
                lat = obs['latitude'] + obs_radius_deg * 1.1
            else:
                # Push outward along the vector from obstacle center
                scale = (obs_radius_deg * 1.1) / dist
                lat = obs['latitude'] + dlat * scale
                lon = obs['longitude'] + (dlon * scale) / cos_lat
    return lat, lon


def grid_search(center_lat, center_lon, num_drones, area_size_meters=500, obstacles=None):
    """
    Generate a grid search (lawnmower) pattern.
    Divides a square area into vertical strips — one per drone.
    Each drone flies a zigzag back-and-forth pattern in its strip.

    If obstacles are provided, waypoints landing inside obstacles are
    pushed to the nearest safe edge.

    Returns: dict of { drone_id: [(lat, lon), ...] }
    """
    if obstacles is None:
        obstacles = []

    area_deg_lat = area_size_meters / METERS_PER_DEG_LAT
    area_deg_lon = area_size_meters / (METERS_PER_DEG_LAT * math.cos(math.radians(center_lat)))

    half_lat = area_deg_lat / 2.0
    half_lon = area_deg_lon / 2.0

    min_lat = center_lat - half_lat
    max_lat = center_lat + half_lat
    min_lon = center_lon - half_lon

    strip_width = area_deg_lon / num_drones

    # Lateral offset at spawn so drones don't stack (≥5m gap between each)
    spawn_gap_meters = 5.0
    spawn_gap_deg_lon = spawn_gap_meters / (METERS_PER_DEG_LAT * math.cos(math.radians(center_lat)))

    waypoints = {}
    for i in range(1, num_drones + 1):
        # Drones start at the spawn center with a lateral offset for separation
        offset_index = i - (num_drones + 1) / 2.0  # center the offsets around 0
        spawn_lon = center_lon + offset_index * spawn_gap_deg_lon
        drone_waypoints = [(center_lat, spawn_lon)]
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
            wp_lat = lat
            wp_lon = strip_center_lon + lon_offset

            # Push waypoint outside obstacles if needed
            if obstacles:
                wp_lat, wp_lon = _push_outside_obstacles(wp_lat, wp_lon, obstacles)

            drone_waypoints.append((wp_lat, wp_lon))

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
