"""
RS-GCS Drone Simulator — Entry Point
Spawns N drone coroutines that send telemetry to the Spring Boot backend.
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
HEARTBEAT_INTERVAL_MS = int(os.environ.get("HEARTBEAT_INTERVAL_MS", "100"))
CENTER_LAT = float(os.environ.get("CENTER_LAT", "28.4595"))
CENTER_LON = float(os.environ.get("CENTER_LON", "77.5021"))

# Track all drones for kill commands
drones: dict[int, SimulatedDrone] = {}


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
                        if action == "KILL" and drone_id in drones:
                            drones[drone_id].kill()
                            logger.warning(f"[CMD] Kill order executed for Drone_{drone_id}")
        except Exception:
            pass  # Backend might not have this endpoint yet — that's fine

        await asyncio.sleep(0.5)


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

        # Generate waypoints for all drones
        waypoints = grid_search(CENTER_LAT, CENTER_LON, NUM_DRONES, area_size_meters=500)
        logger.info(f"Generated grid search pattern for {NUM_DRONES} drones")

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
                heartbeat_interval_ms=HEARTBEAT_INTERVAL_MS
            )
            drones[drone_id] = drone
            tasks.append(asyncio.create_task(drone.run()))
            logger.info(f"  → Drone_{drone_id} ({drone_type}) spawned with {len(drone_waypoints)} waypoints")

        # Start command listener
        tasks.append(asyncio.create_task(command_listener(session)))

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
