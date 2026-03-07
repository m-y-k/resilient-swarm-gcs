"""
A* grid-based pathfinding for drone obstacle avoidance.

Phase 8 replacement for the tangent-point bypass approach.
Rasterizes obstacles onto a walkable/blocked grid, runs 8-directional A*,
then simplifies the result with Ramer-Douglas-Peucker.

Grid resolution: 5 meters per cell.
Safety buffer: 15 meters around every obstacle edge.
"""

import math
import heapq
import time
import logging

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
METERS_PER_DEG_LAT = 111_320.0
GRID_RESOLUTION_M = 5.0          # meters per grid cell
SAFETY_BUFFER_M = 15.0           # extra clearance around obstacles
RDP_EPSILON_CELLS = 1.5          # Ramer-Douglas-Peucker tolerance (in cells)

# Directions: 8-connected grid (dx, dy, cost)
_SQRT2 = math.sqrt(2)
DIRECTIONS = [
    ( 0,  1, 1.0),   # N
    ( 1,  1, _SQRT2), # NE
    ( 1,  0, 1.0),   # E
    ( 1, -1, _SQRT2), # SE
    ( 0, -1, 1.0),   # S
    (-1, -1, _SQRT2), # SW
    (-1,  0, 1.0),   # W
    (-1,  1, _SQRT2), # NW
]


# ---------------------------------------------------------------------------
# PathfindingGrid — rasterizes obstacles into a blocked/walkable bitmap
# ---------------------------------------------------------------------------
class PathfindingGrid:
    """
    Converts a geographic bounding box + obstacle list into a 2D boolean grid
    where True = blocked, False = walkable.

    Coordinate mapping:
        col = (lon - min_lon) / lon_step   (x-axis, east)
        row = (lat - min_lat) / lat_step   (y-axis, north)
    """

    def __init__(self, obstacles, margin_m=200.0):
        """
        Build the grid from obstacles.

        Args:
            obstacles: list of dicts with latitude, longitude, radius (meters).
            margin_m: extra meters around the obstacle bounding box.
        """
        if not obstacles:
            self.valid = False
            return

        # --- Auto-calculate bounds from obstacle extents ---
        lats = [o['latitude'] for o in obstacles]
        lons = [o['longitude'] for o in obstacles]
        radii_deg = [(o['radius'] + SAFETY_BUFFER_M + margin_m) / METERS_PER_DEG_LAT
                     for o in obstacles]

        self.min_lat = min(la - r for la, r in zip(lats, radii_deg))
        self.max_lat = max(la + r for la, r in zip(lats, radii_deg))

        cos_center = math.cos(math.radians(sum(lats) / len(lats)))
        radii_deg_lon = [(o['radius'] + SAFETY_BUFFER_M + margin_m)
                         / (METERS_PER_DEG_LAT * cos_center)
                         for o in obstacles]
        self.min_lon = min(lo - r for lo, r in zip(lons, radii_deg_lon))
        self.max_lon = max(lo + r for lo, r in zip(lons, radii_deg_lon))

        # --- Grid dimensions ---
        self.cos_lat = cos_center
        self.lat_step = GRID_RESOLUTION_M / METERS_PER_DEG_LAT
        self.lon_step = GRID_RESOLUTION_M / (METERS_PER_DEG_LAT * self.cos_lat)

        self.rows = max(1, int((self.max_lat - self.min_lat) / self.lat_step) + 1)
        self.cols = max(1, int((self.max_lon - self.min_lon) / self.lon_step) + 1)

        # Cap grid size to prevent memory explosion (800x800 = 640k cells max)
        if self.rows > 800 or self.cols > 800:
            logger.warning("Grid too large (%dx%d), increasing resolution", self.rows, self.cols)
            scale = max(self.rows, self.cols) / 800.0
            self.lat_step *= scale
            self.lon_step *= scale
            self.rows = max(1, int((self.max_lat - self.min_lat) / self.lat_step) + 1)
            self.cols = max(1, int((self.max_lon - self.min_lon) / self.lon_step) + 1)

        # --- Rasterize obstacles ---
        # Flat list: blocked[row * cols + col] = True if blocked
        self.blocked = bytearray(self.rows * self.cols)
        self._rasterize(obstacles)
        self.valid = True
        self._build_time = time.monotonic()
        logger.debug("Grid built: %dx%d (%d cells), %d blocked",
                     self.cols, self.rows, self.rows * self.cols,
                     sum(self.blocked))

    # -- Rasterization -------------------------------------------------------

    def _rasterize(self, obstacles):
        """Mark cells within (obstacle_radius + safety_buffer) as blocked."""
        for obs in obstacles:
            radius_m = obs['radius'] + SAFETY_BUFFER_M
            radius_cells = radius_m / GRID_RESOLUTION_M
            radius_sq = radius_cells * radius_cells

            # Obstacle center in grid coords
            oc = self.latlon_to_cell(obs['latitude'], obs['longitude'])
            if oc is None:
                continue
            oc_col, oc_row = oc

            # Bounding box in cell coords
            r_int = int(math.ceil(radius_cells)) + 1
            row_lo = max(0, oc_row - r_int)
            row_hi = min(self.rows - 1, oc_row + r_int)
            col_lo = max(0, oc_col - r_int)
            col_hi = min(self.cols - 1, oc_col + r_int)

            for r in range(row_lo, row_hi + 1):
                for c in range(col_lo, col_hi + 1):
                    dx = c - oc_col
                    dy = r - oc_row
                    if dx * dx + dy * dy <= radius_sq:
                        self.blocked[r * self.cols + c] = 1

    # -- Coordinate conversion -----------------------------------------------

    def latlon_to_cell(self, lat, lon):
        """Convert lat/lon to (col, row). Returns None if out of bounds."""
        col = int((lon - self.min_lon) / self.lon_step)
        row = int((lat - self.min_lat) / self.lat_step)
        if 0 <= col < self.cols and 0 <= row < self.rows:
            return (col, row)
        return None

    def latlon_to_cell_clamped(self, lat, lon):
        """Convert lat/lon to (col, row), clamped to grid bounds."""
        col = int((lon - self.min_lon) / self.lon_step)
        row = int((lat - self.min_lat) / self.lat_step)
        col = max(0, min(self.cols - 1, col))
        row = max(0, min(self.rows - 1, row))
        return (col, row)

    def cell_to_latlon(self, col, row):
        """Convert (col, row) back to (lat, lon)."""
        lat = self.min_lat + (row + 0.5) * self.lat_step
        lon = self.min_lon + (col + 0.5) * self.lon_step
        return (lat, lon)

    def is_walkable(self, col, row):
        """Check if a cell is within bounds and not blocked."""
        if 0 <= col < self.cols and 0 <= row < self.rows:
            return self.blocked[row * self.cols + col] == 0
        return False

    def find_nearest_walkable(self, col, row, max_radius=50):
        """Spiral outward from (col, row) to find the nearest walkable cell."""
        if self.is_walkable(col, row):
            return (col, row)
        for r in range(1, max_radius + 1):
            for dx in range(-r, r + 1):
                for dy in [-r, r]:
                    nc, nr = col + dx, row + dy
                    if self.is_walkable(nc, nr):
                        return (nc, nr)
            for dy in range(-r + 1, r):
                for dx in [-r, r]:
                    nc, nr = col + dx, row + dy
                    if self.is_walkable(nc, nr):
                        return (nc, nr)
        return None


# ---------------------------------------------------------------------------
# Bresenham line walk — quick check if straight line is clear
# ---------------------------------------------------------------------------
def _bresenham_clear(grid, c0, r0, c1, r1):
    """Walk a Bresenham line from (c0,r0) to (c1,r1). Returns True if all clear."""
    dc = abs(c1 - c0)
    dr = abs(r1 - r0)
    sc = 1 if c0 < c1 else -1
    sr = 1 if r0 < r1 else -1
    err = dc - dr
    c, r = c0, r0
    while True:
        if not grid.is_walkable(c, r):
            return False
        if c == c1 and r == r1:
            break
        e2 = 2 * err
        if e2 > -dr:
            err -= dr
            c += sc
        if e2 < dc:
            err += dc
            r += sr
    return True


# ---------------------------------------------------------------------------
# A* search — 8-directional with Euclidean heuristic
# ---------------------------------------------------------------------------
def astar(grid, start, goal):
    """
    A* from start (col, row) to goal (col, row) on the grid.
    Returns list of (col, row) from start to goal inclusive, or None.
    """
    sc, sr = start
    gc, gr = goal

    # Heuristic: Euclidean distance (admissible for 8-dir movement)
    def h(c, r):
        dc = abs(c - gc)
        dr = abs(r - gr)
        return 1.01 * math.sqrt(dc * dc + dr * dr)

    # Priority queue: (f, tiebreaker, col, row)
    open_heap = [(h(sc, sr), 0, sc, sr)]
    g_score = {(sc, sr): 0.0}
    came_from = {}
    counter = 1
    cols = grid.cols

    while open_heap:
        f, _, c, r = heapq.heappop(open_heap)

        if c == gc and r == gr:
            # Reconstruct path
            path = [(gc, gr)]
            node = (gc, gr)
            while node in came_from:
                node = came_from[node]
                path.append(node)
            path.reverse()
            return path

        current_g = g_score.get((c, r))
        if current_g is None:
            continue
        # Skip stale entries (we may have pushed duplicates)
        if f - h(c, r) > current_g + 1e-6:
            continue

        for dc, dr, cost in DIRECTIONS:
            nc, nr = c + dc, r + dr
            if not grid.is_walkable(nc, nr):
                continue
            new_g = current_g + cost
            key = (nc, nr)
            old_g = g_score.get(key)
            if old_g is None or new_g < old_g - 1e-6:
                g_score[key] = new_g
                came_from[key] = (c, r)
                heapq.heappush(open_heap, (new_g + h(nc, nr), counter, nc, nr))
                counter += 1

    return None  # No path found


# ---------------------------------------------------------------------------
# Ramer-Douglas-Peucker line simplification
# ---------------------------------------------------------------------------
def _perpendicular_distance(px, py, ax, ay, bx, by):
    """Perpendicular distance from point P to line A-B."""
    dx = bx - ax
    dy = by - ay
    length_sq = dx * dx + dy * dy
    if length_sq == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / length_sq))
    proj_x = ax + t * dx
    proj_y = ay + t * dy
    return math.hypot(px - proj_x, py - proj_y)


def simplify_path(path, epsilon=RDP_EPSILON_CELLS):
    """
    Ramer-Douglas-Peucker simplification on a list of (col, row) tuples.
    Removes intermediate points that lie within epsilon of the straight line.
    """
    if len(path) <= 2:
        return path

    # Find the point with the maximum distance from the line start->end
    ax, ay = path[0]
    bx, by = path[-1]
    max_dist = 0.0
    max_idx = 0
    for i in range(1, len(path) - 1):
        d = _perpendicular_distance(path[i][0], path[i][1], ax, ay, bx, by)
        if d > max_dist:
            max_dist = d
            max_idx = i

    if max_dist > epsilon:
        left = simplify_path(path[:max_idx + 1], epsilon)
        right = simplify_path(path[max_idx:], epsilon)
        return left[:-1] + right
    else:
        return [path[0], path[-1]]


# ---------------------------------------------------------------------------
# Grid cache — avoid rebuilding every tick
# ---------------------------------------------------------------------------
_grid_cache = {
    'grid': None,
    'obstacle_hash': None,
}


def _obstacle_hash(obstacles):
    """Quick hash of obstacle list to detect changes."""
    parts = []
    for o in sorted(obstacles, key=lambda x: x.get('id', '')):
        parts.append(f"{o['latitude']:.6f},{o['longitude']:.6f},{o['radius']:.1f}")
    return '|'.join(parts)


def _get_or_build_grid(obstacles):
    """Return cached grid or build a new one if obstacles changed."""
    h = _obstacle_hash(obstacles)
    if _grid_cache['obstacle_hash'] == h and _grid_cache['grid'] is not None:
        return _grid_cache['grid']
    grid = PathfindingGrid(obstacles)
    _grid_cache['grid'] = grid
    _grid_cache['obstacle_hash'] = h
    return grid


# ---------------------------------------------------------------------------
# Public API — drop-in replacement for the old get_safe_path()
# ---------------------------------------------------------------------------
def point_to_line_distance(px, py, ax, ay, bx, by):
    """Perpendicular distance from point (px, py) to line segment (ax,ay)->(bx,by)."""
    dx, dy = bx - ax, by - ay
    if dx == 0 and dy == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    proj_x, proj_y = ax + t * dx, ay + t * dy
    return math.hypot(px - proj_x, py - proj_y)


def get_safe_path(drone_lat, drone_lon, waypoint_lat, waypoint_lon, obstacles):
    """
    A*-based drop-in replacement for the old tangent-point avoidance.

    Returns a list of (lat, lon) intermediate waypoints that route around obstacles.
    Empty list if the straight-line path is clear.
    """
    if not obstacles:
        return []

    grid = _get_or_build_grid(obstacles)
    if not grid.valid:
        return []

    # Convert start/goal to grid cells
    start_cell = grid.latlon_to_cell(drone_lat, drone_lon)
    clamped_s = False
    if start_cell is None:
        sc, sr = grid.latlon_to_cell_clamped(drone_lat, drone_lon)
        clamped_s = True
    else:
        sc, sr = start_cell

    goal_cell = grid.latlon_to_cell(waypoint_lat, waypoint_lon)
    clamped_g = False
    if goal_cell is None:
        gc, gr = grid.latlon_to_cell_clamped(waypoint_lat, waypoint_lon)
        clamped_g = True
    else:
        gc, gr = goal_cell

    # Quick exit: Bresenham line-of-sight check — if clear, no detour needed
    if _bresenham_clear(grid, sc, sr, gc, gr):
        return []

    # If start or goal is inside a blocked cell, find nearest walkable
    if not grid.is_walkable(sc, sr):
        nearest = grid.find_nearest_walkable(sc, sr)
        if nearest is None:
            return []  # completely surrounded — give up
        sc, sr = nearest

    if not grid.is_walkable(gc, gr):
        nearest = grid.find_nearest_walkable(gc, gr)
        if nearest is None:
            return []
        gc, gr = nearest

    # Run A*
    raw_path = astar(grid, (sc, sr), (gc, gr))
    if raw_path is None:
        return []

    # Simplify with RDP
    simplified = simplify_path(raw_path)

    # Convert back to lat/lon, skip first (drone position) and last (waypoint) unless clamped
    result = []
    for i, (col, row) in enumerate(simplified):
        if i == 0 and not clamped_s and len(simplified) > 1:
            continue
        if i == len(simplified) - 1 and not clamped_g and len(simplified) > 1:
            continue
        lat, lon = grid.cell_to_latlon(col, row)
        result.append((lat, lon))

    return result
