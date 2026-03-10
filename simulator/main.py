"""
RS-GCS Drone Simulator — Entry Point
Spawns N drone coroutines that send telemetry to the Spring Boot backend.
Supports configurable spawn center point via environment variables or backend API.
"""

import asyncio
import os
import signal
import sys
import logging

import aiohttp

from drone import SimulatedDrone, get_drone_type
from swarm_patterns import grid_search

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger("simulator")

# Environment configuration
BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8080")
NUM_DRONES = int(os.environ.get("NUM_DRONES", "10"))
HEARTBEAT_INTERVAL_MS = int(os.environ.get("HEARTBEAT_INTERVAL_MS", "200"))
CENTER_LAT = float(os.environ.get("CENTER_LAT", "28.4595"))
CENTER_LON = float(os.environ.get("CENTER_LON", "77.5021"))
UDP_HOST = os.environ.get("UDP_HOST", "127.0.0.1")
UDP_PORT = int(os.environ.get("UDP_PORT", "9090"))

# Track all drones for kill commands
drones: dict[int, SimulatedDrone] = {}

# Shared state for spawn point listener
cached_obstacle_dicts: list[dict] = []
current_spawn_lat: float = CENTER_LAT
current_spawn_lon: float = CENTER_LON
mission_active: bool = True


async def fetch_obstacles(session):
    """Fetch current obstacle list from the backend."""
    try:
        async with session.get(f"{BACKEND_URL}/api/simulator/obstacles") as resp:
            if resp.status == 200:
                obstacles = await resp.json()
                if obstacles:
                    logger.info(f"Fetched {len(obstacles)} obstacles from backend")
                    return obstacles
    except Exception as e:
        logger.warning(f"Could not fetch obstacles: {e}")
    return []


async def fetch_spawn_point(session):
    """Fetch operator-configured spawn point from backend, if set."""
    try:
        async with session.get(f"{BACKEND_URL}/api/simulator/spawn-point") as resp:
            if resp.status == 200:
                data = await resp.json()
                if data and 'latitude' in data and 'longitude' in data:
                    lat, lon = data['latitude'], data['longitude']
                    logger.info(f"Using operator spawn point: ({lat}, {lon})")
                    return lat, lon
    except Exception:
        pass
    return None


async def command_listener(session):
    """
    Poll the backend for kill commands.
    Simple polling approach — checks every 500ms.
    """
    while True:
        try:
            async with session.get(f"{BACKEND_URL}/api/simulator/commands") as resp:
                if resp.status == 200:
                    commands = await resp.json()
                    for cmd in commands:
                        drone_id = cmd.get("droneId")
                        action = cmd.get("action")
                        if action == "KILL" and drone_id in drones and drones[drone_id].is_alive:
                            drones[drone_id].kill()
                            logger.warning(f"[CMD] Kill order executed for Drone_{drone_id}")
        except Exception:
            pass  # Backend might not have this endpoint yet — that's fine

        await asyncio.sleep(0.5)


async def waypoint_listener(session):
    """
    Poll the backend for operator-placed waypoints.
    Updates drone waypoints when new mission is deployed.
    """
    while True:
        try:
            async with session.get(f"{BACKEND_URL}/api/simulator/waypoints") as resp:
                if resp.status == 200:
                    waypoint_data = await resp.json()
                    if waypoint_data:
                        for drone_id_str, wps in waypoint_data.items():
                            drone_id = int(drone_id_str)
                            if drone_id in drones and drones[drone_id].is_alive:
                                # Only update if waypoints actually changed
                                wp_tuples = [(wp[0], wp[1]) for wp in wps]
                                if wp_tuples != drones[drone_id].waypoints:
                                    drones[drone_id].update_waypoints(wp_tuples)
        except Exception:
            pass

        await asyncio.sleep(2)


async def obstacle_listener(session):
    """
    Poll the backend for obstacle list changes.
    Updates drone obstacle lists for pathfinding.
    """
    global cached_obstacle_dicts
    while True:
        try:
            async with session.get(f"{BACKEND_URL}/api/simulator/obstacles") as resp:
                if resp.status == 200:
                    obstacles = await resp.json()
                    if obstacles:
                        cached_obstacle_dicts = [
                            {'latitude': o.get('latitude', 0),
                             'longitude': o.get('longitude', 0),
                             'radius': o.get('radius', 0)}
                            for o in obstacles
                        ]
                        for drone in drones.values():
                            if drone.is_alive:
                                drone.update_obstacles(cached_obstacle_dicts)
        except Exception:
            pass

        await asyncio.sleep(5)


async def spawn_point_listener(session):
    """
    Poll the backend for spawn point changes.
    When the operator sets a new spawn point, regenerate waypoints
    and reposition all drones immediately — no restart needed.
    """
    global current_spawn_lat, current_spawn_lon
    while True:
        try:
            spawn_point = await fetch_spawn_point(session)
            if spawn_point:
                new_lat, new_lon = spawn_point
                # Check if spawn point actually changed
                if (abs(new_lat - current_spawn_lat) > 0.00001 or
                        abs(new_lon - current_spawn_lon) > 0.00001):
                    current_spawn_lat = new_lat
                    current_spawn_lon = new_lon
                    logger.info(f"[SPAWN] New spawn point detected: ({new_lat:.6f}, {new_lon:.6f})")
                    logger.info(f"[SPAWN] Regenerating waypoints and repositioning drones...")

                    # Regenerate waypoints centered on new spawn point
                    new_waypoints = grid_search(
                        new_lat, new_lon, NUM_DRONES,
                        area_size_meters=500, obstacles=cached_obstacle_dicts
                    )

                    # Update each drone with new waypoints and reposition to first waypoint
                    for drone_id, drone in drones.items():
                        if drone.is_alive:
                            wps = new_waypoints.get(drone_id, [])
                            if wps:
                                drone.update_waypoints(wps)
                                # Teleport drone to its new first waypoint
                                drone.lat = wps[0][0]
                                drone.lon = wps[0][1]
                                logger.info(f"  → Drone_{drone_id} repositioned to ({wps[0][0]:.6f}, {wps[0][1]:.6f})")
        except Exception as e:
            logger.warning(f"[SPAWN] Error checking spawn point: {e}")

        await asyncio.sleep(3)


async def mission_status_listener(session):
    """
    Poll the backend for the current mission status.
    Drones should only navigate waypoints if the mission is ACTIVE.
    """
    global mission_active
    while True:
        try:
            async with session.get(f"{BACKEND_URL}/api/simulator/mission") as resp:
                if resp.status == 200:
                    data = await resp.json()
                    status = data.get("status")
                    is_active = (status == "ACTIVE")
                    
                    if is_active != mission_active:
                        mission_active = is_active
                        logger.info(f"[MISSION] Status changed to: {status} (Active: {is_active})")
                        for drone in drones.values():
                            drone.is_mission_active = mission_active
        except Exception:
            pass
        
        await asyncio.sleep(1)


async def wait_for_backend(session, max_retries=30):
    """Wait for the backend to be ready before starting drones."""
    logger.info(f"Waiting for backend at {BACKEND_URL}...")
    for attempt in range(max_retries):
        try:
            async with session.get(f"{BACKEND_URL}/actuator/health") as resp:
                if resp.status == 200:
                    logger.info(f"Backend is UP (attempt {attempt + 1})")
                    return True
        except Exception:
            pass
        logger.info(f"Backend not ready (attempt {attempt + 1}/{max_retries})...")
        await asyncio.sleep(2)

    logger.error("Backend did not become ready in time!")
    return False


async def main():
    """Entry point — spawn drone coroutines and run until interrupted."""
    logger.info("=" * 60)
    logger.info("RS-GCS DRONE SIMULATOR")
    logger.info(f"Backend URL:  {BACKEND_URL}")
    logger.info(f"Drones:       {NUM_DRONES}")
    logger.info(f"Heartbeat:    {HEARTBEAT_INTERVAL_MS}ms")
    logger.info(f"Center:       ({CENTER_LAT}, {CENTER_LON})")
    logger.info("=" * 60)

    # Create shared HTTP session (reuse for all drones)
    timeout = aiohttp.ClientTimeout(total=5)
    async with aiohttp.ClientSession(timeout=timeout) as session:

        # Wait for backend
        if not await wait_for_backend(session):
            logger.error("Aborting — backend unreachable")
            return

        # Fetch obstacles BEFORE generating waypoints (so drones avoid spawning inside them)
        obstacles = await fetch_obstacles(session)

        # Check for operator-configured spawn point
        spawn_point = await fetch_spawn_point(session)
        spawn_lat = spawn_point[0] if spawn_point else CENTER_LAT
        spawn_lon = spawn_point[1] if spawn_point else CENTER_LON
        logger.info(f"Spawn center: ({spawn_lat}, {spawn_lon})")

        # Convert obstacles to the format expected by grid_search
        obstacle_dicts = []
        for obs in obstacles:
            obstacle_dicts.append({
                'latitude': obs.get('latitude', 0),
                'longitude': obs.get('longitude', 0),
                'radius': obs.get('radius', 0),
            })

        # Generate waypoints for all drones (obstacle-aware)
        waypoints = grid_search(spawn_lat, spawn_lon, NUM_DRONES,
                                area_size_meters=500, obstacles=obstacle_dicts)
        logger.info(f"Generated obstacle-aware grid search pattern for {NUM_DRONES} drones")

        # Give initial obstacles to each drone
        # Create drone instances
        tasks = []
        for drone_id in range(1, NUM_DRONES + 1):
            drone_type = get_drone_type(drone_id)
            drone_waypoints = waypoints.get(drone_id, [])

            drone = SimulatedDrone(
                drone_id=drone_id,
                waypoints=drone_waypoints,
                session=session,
                backend_url=BACKEND_URL,
                heartbeat_interval_ms=HEARTBEAT_INTERVAL_MS,
                udp_host=UDP_HOST,
                udp_port=UDP_PORT
            )
            # Pre-load obstacles so pathfinding works from the start
            drone.update_obstacles(obstacle_dicts)
            drones[drone_id] = drone
            tasks.append(asyncio.create_task(drone.run()))
            logger.info(f"  → Drone_{drone_id} ({drone_type}) spawned with {len(drone_waypoints)} waypoints")

        # Start command listener + waypoint/obstacle/spawn/mission listeners
        tasks.append(asyncio.create_task(command_listener(session)))
        tasks.append(asyncio.create_task(waypoint_listener(session)))
        tasks.append(asyncio.create_task(obstacle_listener(session)))
        tasks.append(asyncio.create_task(spawn_point_listener(session)))
        tasks.append(asyncio.create_task(mission_status_listener(session)))

        logger.info(f"\n{'=' * 60}")
        logger.info(f"ALL {NUM_DRONES} DRONES ACTIVE — sending telemetry to {BACKEND_URL}")
        logger.info(f"{'=' * 60}\n")

        # Run until all drones stop or we get interrupted
        try:
            await asyncio.gather(*tasks)
        except asyncio.CancelledError:
            logger.info("Simulator shutting down...")


def handle_shutdown(signum, frame):
    """Graceful shutdown on SIGINT/SIGTERM."""
    logger.info(f"\nReceived signal {signum} — shutting down all drones...")
    for drone in drones.values():
        drone.kill()
    sys.exit(0)


if __name__ == "__main__":
    # Register signal handlers for graceful shutdown
    signal.signal(signal.SIGINT, handle_shutdown)
    if hasattr(signal, 'SIGTERM'):
        signal.signal(signal.SIGTERM, handle_shutdown)

    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        logger.info("Simulator stopped by user")
