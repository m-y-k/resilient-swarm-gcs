"""
Single drone simulation coroutine.
Each instance moves toward waypoints, drains battery, and POSTs telemetry to the backend.
"""

import asyncio
import math
import time
import logging

logger = logging.getLogger(__name__)

# Movement parameters by drone type
DRONE_PARAMS = {
    "SURVEILLANCE": {"speed": 15.0, "altitude": 120.0},  # Fast, low alt
    "LOGISTICS":    {"speed": 5.0,  "altitude": 80.0},   # Slow, heavy
    "STRIKE":       {"speed": 10.0, "altitude": 150.0},  # Medium
}

# 1 degree latitude ≈ 111,320 meters
METERS_PER_DEG_LAT = 111_320.0


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

        self.session = session
        self.backend_url = backend_url
        self.heartbeat_interval = heartbeat_interval_ms / 1000.0  # Convert to seconds

    async def run(self):
        """Main simulation loop — move, drain battery, send telemetry."""
        logger.info(f"Drone_{self.drone_id} ({self.drone_type}) started at ({self.lat:.6f}, {self.lon:.6f})")

        while self.is_alive:
            try:
                # 1. Move toward current waypoint
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

    def _move_toward_waypoint(self):
        """Move toward current waypoint. Advance to next when close enough."""
        if not self.waypoints:
            return

        target_lat, target_lon = self.waypoints[self.current_waypoint_index]

        # Calculate distance in meters
        dlat = (target_lat - self.lat) * METERS_PER_DEG_LAT
        dlon = (target_lon - self.lon) * METERS_PER_DEG_LAT * math.cos(math.radians(self.lat))
        distance = math.sqrt(dlat ** 2 + dlon ** 2)

        # If within 10m of waypoint, advance to next
        if distance < 10:
            self.current_waypoint_index = (self.current_waypoint_index + 1) % len(self.waypoints)
            return

        # Calculate bearing
        bearing = math.atan2(dlon, dlat)
        self.heading = math.degrees(bearing) % 360

        # Move by (speed * interval) in bearing direction
        move_distance = self.speed * self.heartbeat_interval
        move_lat = (move_distance * math.cos(bearing)) / METERS_PER_DEG_LAT
        move_lon = (move_distance * math.sin(bearing)) / (METERS_PER_DEG_LAT * math.cos(math.radians(self.lat)))

        self.lat += move_lat
        self.lon += move_lon

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
