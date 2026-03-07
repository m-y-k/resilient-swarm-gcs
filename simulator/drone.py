"""
Single drone simulation coroutine.
Each instance moves toward waypoints, drains battery, and POSTs telemetry to the backend.
"""

import asyncio
import math
import time
import logging

from pathfinding import get_safe_path

logger = logging.getLogger(__name__)

# Movement parameters by drone type
DRONE_PARAMS = {
    "SURVEILLANCE": {"speed": 50.0, "altitude": 120.0},
    "LOGISTICS":    {"speed": 45.0, "altitude": 80.0},
    "STRIKE":       {"speed": 50.0, "altitude": 150.0},
}

# 1 degree latitude ≈ 111,320 meters
METERS_PER_DEG_LAT = 111_320.0

# Distance threshold to consider a point "reached" (meters)
WAYPOINT_REACH_THRESHOLD = 15.0
DETOUR_REACH_THRESHOLD = 25.0


def get_drone_type(drone_id):
    """Assign type based on ID: 1-3 SURVEILLANCE, 4-6 LOGISTICS, 7-10 STRIKE."""
    if drone_id <= 3:
        return "SURVEILLANCE"
    elif drone_id <= 6:
        return "LOGISTICS"
    else:
        return "STRIKE"


class SimulatedDrone:
    def __init__(self, drone_id, waypoints, session, backend_url, heartbeat_interval_ms=100):
        self.drone_id = drone_id
        self.drone_type = get_drone_type(drone_id)
        self.params = DRONE_PARAMS[self.drone_type]

        # Position — start at first waypoint
        self.lat = waypoints[0][0] if waypoints else 28.4595
        self.lon = waypoints[0][1] if waypoints else 77.5021
        self.altitude = self.params["altitude"]
        self.heading = 0.0
        self.speed = self.params["speed"]
        self.battery = 100.0
        self.sequence_number = 0
        self.is_alive = True

        self.waypoints = waypoints
        self.current_waypoint_index = 0

        # Obstacle avoidance
        self.obstacles = []

        # A* detour path — full list of intermediate waypoints to follow
        self._detour_path = []
        self._detour_index = 0
        self._is_calculating_path = False
        self._direct_path_clear = False  # True when A* confirms no detour needed

        self.session = session
        self.backend_url = backend_url
        self.heartbeat_interval = heartbeat_interval_ms / 1000.0  # Convert to seconds
        self.is_mission_active = True

    async def run(self):
        """Main simulation loop — move, drain battery, send telemetry."""
        logger.info(f"Drone_{self.drone_id} ({self.drone_type}) started at ({self.lat:.6f}, {self.lon:.6f})")

        while self.is_alive:
            try:
                # 1. Move toward current waypoint ONLY if mission is active
                if self.is_mission_active:
                    self._move_toward_waypoint()

                # 2. Drain battery
                self.battery -= 0.01
                if self.battery < 10:
                    self.speed = self.params["speed"] * 0.5
                if self.battery <= 0:
                    self.battery = 0
                    self.is_alive = False
                    logger.warning(f"Drone_{self.drone_id} battery depleted — crash!")
                    break

                # 3. Build and send telemetry packet
                packet = {
                    "systemId": self.drone_id,
                    "componentId": 1,
                    "sequenceNumber": self.sequence_number,
                    "messageType": "HEARTBEAT",
                    "latitude": self.lat,
                    "longitude": self.lon,
                    "altitude": self.altitude,
                    "heading": self.heading,
                    "speed": self.speed,
                    "batteryPercent": round(self.battery, 2),
                    "currentWaypointIndex": self.current_waypoint_index,
                    "timestamp": int(time.time() * 1000)
                }

                # 4. POST to backend (fire-and-forget style)
                await self._send_telemetry(packet)

                # 5. Increment sequence
                self.sequence_number += 1

                # 6. Wait for next heartbeat
                await asyncio.sleep(self.heartbeat_interval)

            except asyncio.CancelledError:
                logger.info(f"Drone_{self.drone_id} coroutine cancelled")
                break
            except Exception as e:
                logger.error(f"Drone_{self.drone_id} error: {e}")
                await asyncio.sleep(self.heartbeat_interval)

        logger.info(f"Drone_{self.drone_id} stopped (alive={self.is_alive}, battery={self.battery:.1f}%)")

    def _distance_meters(self, lat1, lon1, lat2, lon2):
        """Calculate distance in meters between two lat/lon points."""
        dlat = (lat2 - lat1) * METERS_PER_DEG_LAT
        dlon = (lon2 - lon1) * METERS_PER_DEG_LAT * math.cos(math.radians(lat1))
        return math.sqrt(dlat ** 2 + dlon ** 2)

    def _move_toward_point(self, target_lat, target_lon):
        """Move one step toward a target point. Returns distance to target."""
        dlat = (target_lat - self.lat) * METERS_PER_DEG_LAT
        dlon = (target_lon - self.lon) * METERS_PER_DEG_LAT * math.cos(math.radians(self.lat))
        distance = math.sqrt(dlat ** 2 + dlon ** 2)

        if distance < 1.0:
            return distance

        # Calculate bearing
        bearing = math.atan2(dlon, dlat)
        self.heading = math.degrees(bearing) % 360

        # Move by (speed * interval) in bearing direction, but don't overshoot
        move_distance = min(self.speed * self.heartbeat_interval, distance)
        move_lat = (move_distance * math.cos(bearing)) / METERS_PER_DEG_LAT
        move_lon = (move_distance * math.sin(bearing)) / (METERS_PER_DEG_LAT * math.cos(math.radians(self.lat)))

        self.lat += move_lat
        self.lon += move_lon

        return distance

    def _move_toward_waypoint(self):
        """Move toward current waypoint with A* obstacle avoidance. Advance to next when close enough."""
        if not self.waypoints:
            return

        target_lat, target_lon = self.waypoints[self.current_waypoint_index]

        # --- A* detour path following ---
        if self._detour_path and self._detour_index < len(self._detour_path):
            det_lat, det_lon = self._detour_path[self._detour_index]
            dist_to_det = self._move_toward_point(det_lat, det_lon)

            if dist_to_det < DETOUR_REACH_THRESHOLD:
                # Reached this detour point — advance to next
                self._detour_index += 1
                if self._detour_index >= len(self._detour_path):
                    # Finished all detour points — clear and recalculate
                    self._detour_path = []
                    self._detour_index = 0
            return

        # No active detour — check if we need a new A* path
        if self.obstacles and not self._direct_path_clear:
            if not self._is_calculating_path:
                self._is_calculating_path = True
                
                async def calc_task():
                    try:
                        points = await asyncio.to_thread(
                            get_safe_path, self.lat, self.lon, target_lat, target_lon, self.obstacles
                        )
                        if points:
                            self._detour_path = points
                            self._detour_index = 0
                            self._direct_path_clear = False
                        else:
                            # No detour needed — direct path is clear of obstacles
                            self._direct_path_clear = True
                    except Exception as e:
                        logger.error(f"Pathfinding error: {e}")
                    finally:
                        self._is_calculating_path = False

                asyncio.create_task(calc_task())

            # Hover while calculating
            if self._is_calculating_path:
                return

        # No obstacles in the way — fly directly to waypoint
        distance = self._move_toward_point(target_lat, target_lon)

        # If within threshold of waypoint, advance to next
        if distance < WAYPOINT_REACH_THRESHOLD:
            self.current_waypoint_index = (self.current_waypoint_index + 1) % len(self.waypoints)
            self._detour_path = []
            self._detour_index = 0
            self._direct_path_clear = False  # Re-check path for next waypoint leg

    async def _send_telemetry(self, packet):
        """POST telemetry to backend. Resilient to backend hiccups."""
        try:
            async with self.session.post(
                f"{self.backend_url}/api/telemetry",
                json=packet
            ) as resp:
                pass  # Fire and forget
        except Exception:
            pass  # Resilient — don't crash if backend is down

    def kill(self):
        """Simulate drone destruction."""
        self.is_alive = False
        logger.warning(f"Drone_{self.drone_id} KILLED")

    def update_waypoints(self, new_waypoints):
        """Replace waypoints at runtime (from operator mission planning)."""
        self.waypoints = new_waypoints
        self.current_waypoint_index = 0
        self._detour_path = []
        self._detour_index = 0
        self._direct_path_clear = False
        logger.info(f"Drone_{self.drone_id} waypoints updated — {len(new_waypoints)} points")

    def update_obstacles(self, obstacle_list):
        """Receive obstacle list from backend for pathfinding."""
        self.obstacles = obstacle_list
        # Reset detour so it gets recalculated with new obstacles
        self._detour_path = []
        self._detour_index = 0
        self._is_calculating_path = False
        self._direct_path_clear = False
