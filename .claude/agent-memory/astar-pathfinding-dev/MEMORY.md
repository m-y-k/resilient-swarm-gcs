# RS-GCS A* Pathfinding Agent Memory

## Project Structure
- **Task tracker**: `D:\Drone Projects\RS GCS\RS-GCS_Tasks.md`
- **Python pathfinding**: `simulator/pathfinding.py` (A* grid-based, Phase 8)
- **Java pathfinding**: `backend/src/main/java/com/rsgcs/service/PathfindingService.java`
- **Drone simulator**: `simulator/drone.py` (calls `get_safe_path()`)
- **Swarm orchestrator**: `backend/src/main/java/com/rsgcs/service/SwarmOrchestrator.java` (calls `calculatePlannedPath()`)
- **Frontend path viz**: `frontend/src/components/PlannedPathLine.jsx`

## Key Architecture Decisions
- Grid resolution: 5m per cell, safety buffer: 15m around obstacles
- Grid auto-calculates bounds from obstacle extents + 200m margin
- Grid is cached by obstacle hash -- only rebuilt when obstacles change
- `latlon_to_cell(lat, lon)` returns `(col, row)` -- col=longitude, row=latitude
- `get_safe_path()` returns list of (lat, lon) detour points -- empty if clear
- Bresenham line-of-sight check as quick-exit before A*
- RDP simplification with epsilon=1.5 cells reduces path points

## Coordinate Convention GOTCHA
- `latlon_to_cell(lat, lon)` takes (lat, lon) order
- `get_safe_path(drone_lat, drone_lon, wp_lat, wp_lon, obstacles)` -- lat first
- Java and Python both follow same convention now

## Build Commands
- Java: `cd backend && mvn compile` (Maven on PATH at `/c/maven/apache-maven-3.9.12/bin/mvn`)
- Python: `cd simulator && python -c "import pathfinding"` for syntax check
- No Maven wrapper (mvnw) in project -- use system `mvn`

## Performance
- Python: ~10us per path query (cached grid), 94k cells for 8 obstacles
- Grid capping at 800x800 prevents memory explosion

## Phases 0-8 Complete
- All 8 phases marked complete in task tracker
- Phase 8 replaced tangent-point bypass with A* grid pathfinding
