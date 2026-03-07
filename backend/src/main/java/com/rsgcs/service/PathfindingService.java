package com.rsgcs.service;

import com.rsgcs.model.Obstacle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * A* grid-based pathfinding for drone obstacle avoidance.
 *
 * Phase 8 replacement for the tangent-point bypass approach.
 * Rasterizes obstacles onto a walkable/blocked grid, runs 8-directional A*,
 * then simplifies the result with Ramer-Douglas-Peucker.
 *
 * Grid resolution: 5 meters per cell.
 * Safety buffer: 15 meters around every obstacle edge.
 */
@Slf4j
@Service
public class PathfindingService {

    private static final double METERS_PER_DEG_LAT = 111_320.0;
    private static final double GRID_RESOLUTION_M = 5.0;
    private static final double SAFETY_BUFFER_M = 15.0;
    private static final double RDP_EPSILON_CELLS = 1.5;
    private static final double SQRT2 = Math.sqrt(2.0);
    private static final int MAX_GRID_DIM = 800;

    // 8-directional movement: {dc, dr, cost}
    private static final double[][] DIRECTIONS = {
            { 0, 1, 1.0 }, // N
            { 1, 1, SQRT2 }, // NE
            { 1, 0, 1.0 }, // E
            { 1, -1, SQRT2 }, // SE
            { 0, -1, 1.0 }, // S
            { -1, -1, SQRT2 }, // SW
            { -1, 0, 1.0 }, // W
            { -1, 1, SQRT2 }, // NW
    };

    // --- Grid cache ---
    private PathfindingGrid cachedGrid = null;
    private String cachedObstacleHash = null;

    // ========================================================================
    // PathfindingGrid — inner class
    // ========================================================================
    private static class PathfindingGrid {
        final double minLat, maxLat, minLon, maxLon;
        final double latStep, lonStep, cosLat;
        final int rows, cols;
        final byte[] blocked; // 1 = blocked, 0 = walkable
        final boolean valid;

        PathfindingGrid(List<Obstacle> obstacles, double marginM) {
            if (obstacles == null || obstacles.isEmpty()) {
                this.minLat = this.maxLat = this.minLon = this.maxLon = 0;
                this.latStep = this.lonStep = this.cosLat = 1;
                this.rows = this.cols = 0;
                this.blocked = new byte[0];
                this.valid = false;
                return;
            }

            // Auto-calculate bounds from obstacle extents
            double centerLat = obstacles.stream()
                    .mapToDouble(Obstacle::getLatitude).average().orElse(28.46);
            this.cosLat = Math.cos(Math.toRadians(centerLat));

            double tmpMinLat = Double.MAX_VALUE, tmpMaxLat = -Double.MAX_VALUE;
            double tmpMinLon = Double.MAX_VALUE, tmpMaxLon = -Double.MAX_VALUE;

            for (Obstacle obs : obstacles) {
                double radiusDeg = (obs.getRadius() + SAFETY_BUFFER_M + marginM)
                        / METERS_PER_DEG_LAT;
                double radiusDegLon = (obs.getRadius() + SAFETY_BUFFER_M + marginM)
                        / (METERS_PER_DEG_LAT * cosLat);
                tmpMinLat = Math.min(tmpMinLat, obs.getLatitude() - radiusDeg);
                tmpMaxLat = Math.max(tmpMaxLat, obs.getLatitude() + radiusDeg);
                tmpMinLon = Math.min(tmpMinLon, obs.getLongitude() - radiusDegLon);
                tmpMaxLon = Math.max(tmpMaxLon, obs.getLongitude() + radiusDegLon);
            }

            this.minLat = tmpMinLat;
            this.maxLat = tmpMaxLat;
            this.minLon = tmpMinLon;
            this.maxLon = tmpMaxLon;

            // Grid dimensions
            double ls = GRID_RESOLUTION_M / METERS_PER_DEG_LAT;
            double lns = GRID_RESOLUTION_M / (METERS_PER_DEG_LAT * cosLat);

            int tmpRows = Math.max(1, (int) ((maxLat - minLat) / ls) + 1);
            int tmpCols = Math.max(1, (int) ((maxLon - minLon) / lns) + 1);

            // Cap grid size
            if (tmpRows > MAX_GRID_DIM || tmpCols > MAX_GRID_DIM) {
                double scale = Math.max(tmpRows, tmpCols) / (double) MAX_GRID_DIM;
                ls *= scale;
                lns *= scale;
                tmpRows = Math.max(1, (int) ((maxLat - minLat) / ls) + 1);
                tmpCols = Math.max(1, (int) ((maxLon - minLon) / lns) + 1);
            }

            this.latStep = ls;
            this.lonStep = lns;
            this.rows = tmpRows;
            this.cols = tmpCols;
            this.blocked = new byte[rows * cols];

            // Rasterize obstacles
            rasterize(obstacles);
            this.valid = true;

            int blockedCount = 0;
            for (byte b : blocked)
                if (b == 1)
                    blockedCount++;
            log.debug("Grid built: {}x{} ({} cells), {} blocked",
                    cols, rows, rows * cols, blockedCount);
        }

        private void rasterize(List<Obstacle> obstacles) {
            for (Obstacle obs : obstacles) {
                double radiusM = obs.getRadius() + SAFETY_BUFFER_M;
                double radiusCells = radiusM / GRID_RESOLUTION_M;
                double radiusSq = radiusCells * radiusCells;

                int[] oc = latlonToCell(obs.getLatitude(), obs.getLongitude());
                if (oc == null)
                    continue;
                int ocCol = oc[0], ocRow = oc[1];

                int rInt = (int) Math.ceil(radiusCells) + 1;
                int rowLo = Math.max(0, ocRow - rInt);
                int rowHi = Math.min(rows - 1, ocRow + rInt);
                int colLo = Math.max(0, ocCol - rInt);
                int colHi = Math.min(cols - 1, ocCol + rInt);

                for (int r = rowLo; r <= rowHi; r++) {
                    for (int c = colLo; c <= colHi; c++) {
                        double dx = c - ocCol;
                        double dy = r - ocRow;
                        if (dx * dx + dy * dy <= radiusSq) {
                            blocked[r * cols + c] = 1;
                        }
                    }
                }
            }
        }

        /** Convert lat/lon to (col, row). Returns null if out of bounds. */
        int[] latlonToCell(double lat, double lon) {
            int col = (int) ((lon - minLon) / lonStep);
            int row = (int) ((lat - minLat) / latStep);
            if (col >= 0 && col < cols && row >= 0 && row < rows) {
                return new int[] { col, row };
            }
            return null;
        }

        /** Convert lat/lon to (col, row), clamped to grid bounds. */
        int[] latlonToCellClamped(double lat, double lon) {
            int col = (int) ((lon - minLon) / lonStep);
            int row = (int) ((lat - minLat) / latStep);
            col = Math.max(0, Math.min(cols - 1, col));
            row = Math.max(0, Math.min(rows - 1, row));
            return new int[] { col, row };
        }

        /** Convert (col, row) back to (lat, lon). */
        double[] cellToLatlon(int col, int row) {
            double lat = minLat + (row + 0.5) * latStep;
            double lon = minLon + (col + 0.5) * lonStep;
            return new double[] { lat, lon };
        }

        boolean isWalkable(int col, int row) {
            if (col >= 0 && col < cols && row >= 0 && row < rows) {
                return blocked[row * cols + col] == 0;
            }
            return false;
        }

        /** Spiral outward to find nearest walkable cell. */
        int[] findNearestWalkable(int col, int row, int maxRadius) {
            if (isWalkable(col, row))
                return new int[] { col, row };
            for (int r = 1; r <= maxRadius; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy : new int[] { -r, r }) {
                        int nc = col + dx, nr = row + dy;
                        if (isWalkable(nc, nr))
                            return new int[] { nc, nr };
                    }
                }
                for (int dy = -r + 1; dy < r; dy++) {
                    for (int dx : new int[] { -r, r }) {
                        int nc = col + dx, nr = row + dy;
                        if (isWalkable(nc, nr))
                            return new int[] { nc, nr };
                    }
                }
            }
            return null;
        }
    }

    // ========================================================================
    // Bresenham line-of-sight check
    // ========================================================================
    private static boolean bresenhamClear(PathfindingGrid grid,
            int c0, int r0, int c1, int r1) {
        int dc = Math.abs(c1 - c0);
        int dr = Math.abs(r1 - r0);
        int sc = c0 < c1 ? 1 : -1;
        int sr = r0 < r1 ? 1 : -1;
        int err = dc - dr;
        int c = c0, r = r0;
        while (true) {
            if (!grid.isWalkable(c, r))
                return false;
            if (c == c1 && r == r1)
                break;
            int e2 = 2 * err;
            if (e2 > -dr) {
                err -= dr;
                c += sc;
            }
            if (e2 < dc) {
                err += dc;
                r += sr;
            }
        }
        return true;
    }

    // ========================================================================
    // A* search — 8-directional with Euclidean heuristic
    // ========================================================================
    private static List<int[]> astar(PathfindingGrid grid,
            int sc, int sr, int gc, int gr) {
        double hStart = 1.01 * Math.sqrt((gc - sc) * (gc - sc) + (gr - sr) * (gr - sr));

        // Priority queue entries: {f, tiebreaker, col, row}
        PriorityQueue<double[]> openHeap = new PriorityQueue<>(
                Comparator.comparingDouble((double[] a) -> a[0])
                        .thenComparingDouble(a -> a[1]));
        openHeap.add(new double[] { hStart, 0, sc, sr });

        int cols = grid.cols;
        int maxNodes = grid.rows * cols;

        double[] gScore = new double[maxNodes];
        Arrays.fill(gScore, Double.POSITIVE_INFINITY);
        int[] cameFrom = new int[maxNodes];
        Arrays.fill(cameFrom, -1);

        int startKey = sr * cols + sc;
        gScore[startKey] = 0.0;
        int counter = 1;

        while (!openHeap.isEmpty()) {
            double[] current = openHeap.poll();
            int c = (int) current[2];
            int r = (int) current[3];

            if (c == gc && r == gr) {
                // Reconstruct path
                List<int[]> path = new ArrayList<>();
                int node = gr * cols + gc;
                path.add(new int[] { gc, gr });
                while (cameFrom[node] != -1) {
                    int prevNode = cameFrom[node];
                    int prevC = prevNode % cols;
                    int prevR = prevNode / cols;
                    path.add(new int[] { prevC, prevR });
                    node = prevNode;
                }
                Collections.reverse(path);
                return path;
            }

            int currentKey = r * cols + c;
            double currentG = gScore[currentKey];
            if (currentG == Double.POSITIVE_INFINITY)
                continue;

            // Skip stale entries
            double h = 1.01 * Math.sqrt((gc - c) * (gc - c) + (gr - r) * (gr - r));
            if (current[0] - h > currentG + 1e-6)
                continue;

            for (double[] dir : DIRECTIONS) {
                int nc = c + (int) dir[0];
                int nr = r + (int) dir[1];
                if (!grid.isWalkable(nc, nr))
                    continue;

                double newG = currentG + dir[2];
                int nKey = nr * cols + nc;
                double oldG = gScore[nKey];

                if (newG < oldG - 1e-6) {
                    gScore[nKey] = newG;
                    cameFrom[nKey] = currentKey;
                    double nh = 1.01 * Math.sqrt((gc - nc) * (gc - nc) + (gr - nr) * (gr - nr));
                    openHeap.add(new double[] { newG + nh, counter++, nc, nr });
                }
            }
        }
        return null; // No path found
    }

    // ========================================================================
    // Ramer-Douglas-Peucker line simplification
    // ========================================================================
    private static double perpendicularDistance(double px, double py,
            double ax, double ay,
            double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq == 0)
            return Math.hypot(px - ax, py - ay);
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSq));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    private static List<int[]> simplifyPath(List<int[]> path, double epsilon) {
        if (path.size() <= 2)
            return new ArrayList<>(path);

        double ax = path.get(0)[0], ay = path.get(0)[1];
        double bx = path.get(path.size() - 1)[0], by = path.get(path.size() - 1)[1];

        double maxDist = 0;
        int maxIdx = 0;
        for (int i = 1; i < path.size() - 1; i++) {
            double d = perpendicularDistance(path.get(i)[0], path.get(i)[1], ax, ay, bx, by);
            if (d > maxDist) {
                maxDist = d;
                maxIdx = i;
            }
        }

        if (maxDist > epsilon) {
            List<int[]> left = simplifyPath(path.subList(0, maxIdx + 1), epsilon);
            List<int[]> right = simplifyPath(path.subList(maxIdx, path.size()), epsilon);
            List<int[]> result = new ArrayList<>(left.subList(0, left.size() - 1));
            result.addAll(right);
            return result;
        } else {
            List<int[]> result = new ArrayList<>();
            result.add(path.get(0));
            result.add(path.get(path.size() - 1));
            return result;
        }
    }

    // ========================================================================
    // Grid cache management
    // ========================================================================
    private String obstacleHash(List<Obstacle> obstacles) {
        List<Obstacle> sorted = new ArrayList<>(obstacles);
        sorted.sort(Comparator.comparing(Obstacle::getId, Comparator.nullsLast(String::compareTo)));
        StringBuilder sb = new StringBuilder();
        for (Obstacle o : sorted) {
            sb.append(String.format("%.6f,%.6f,%.1f|",
                    o.getLatitude(), o.getLongitude(), o.getRadius()));
        }
        return sb.toString();
    }

    private synchronized PathfindingGrid getOrBuildGrid(List<Obstacle> obstacles) {
        String hash = obstacleHash(obstacles);
        if (hash.equals(cachedObstacleHash) && cachedGrid != null && cachedGrid.valid) {
            return cachedGrid;
        }
        cachedGrid = new PathfindingGrid(obstacles, 200.0);
        cachedObstacleHash = hash;
        return cachedGrid;
    }

    // ========================================================================
    // Public API
    // ========================================================================

    /**
     * Check if the straight-line path between two points intersects any obstacle.
     */
    public boolean isPathClear(double lat1, double lon1, double lat2, double lon2,
            List<Obstacle> obstacles) {
        if (obstacles == null || obstacles.isEmpty())
            return true;
        PathfindingGrid grid = getOrBuildGrid(obstacles);
        if (!grid.valid)
            return true;

        int[] s = grid.latlonToCell(lat1, lon1);
        int[] g = grid.latlonToCell(lat2, lon2);
        if (s == null || g == null)
            return true;

        return bresenhamClear(grid, s[0], s[1], g[0], g[1]);
    }

    /**
     * Find the nearest point outside any obstacle zone.
     */
    public double[] getNearestClearPoint(double lat, double lon, List<Obstacle> obstacles) {
        if (obstacles == null || obstacles.isEmpty())
            return new double[] { lat, lon };
        PathfindingGrid grid = getOrBuildGrid(obstacles);
        if (!grid.valid)
            return new double[] { lat, lon };

        int[] cell = grid.latlonToCell(lat, lon);
        if (cell == null)
            return new double[] { lat, lon };

        if (grid.isWalkable(cell[0], cell[1]))
            return new double[] { lat, lon };

        int[] nearest = grid.findNearestWalkable(cell[0], cell[1], 50);
        if (nearest == null)
            return new double[] { lat, lon };

        return grid.cellToLatlon(nearest[0], nearest[1]);
    }

    /**
     * Given current position and target waypoint, return list of detour points
     * that avoid all obstacles. Empty list if path is clear.
     *
     * Drop-in replacement for the old tangent-point getSafePath().
     */
    public List<double[]> getSafePath(double droneLat, double droneLon,
            double waypointLat, double waypointLon,
            List<Obstacle> obstacles) {
        if (obstacles == null || obstacles.isEmpty())
            return List.of();

        PathfindingGrid grid = getOrBuildGrid(obstacles);
        if (!grid.valid)
            return List.of();

        int[] startCell = grid.latlonToCell(droneLat, droneLon);
        boolean clampedS = false;
        if (startCell == null) {
            startCell = grid.latlonToCellClamped(droneLat, droneLon);
            clampedS = true;
        }

        int[] goalCell = grid.latlonToCell(waypointLat, waypointLon);
        boolean clampedG = false;
        if (goalCell == null) {
            goalCell = grid.latlonToCellClamped(waypointLat, waypointLon);
            clampedG = true;
        }

        int sc = startCell[0], sr = startCell[1];
        int gc = goalCell[0], gr = goalCell[1];

        // Quick exit: Bresenham line-of-sight check
        if (bresenhamClear(grid, sc, sr, gc, gr))
            return List.of();

        // If start or goal is blocked, find nearest walkable
        if (!grid.isWalkable(sc, sr)) {
            int[] nearest = grid.findNearestWalkable(sc, sr, 50);
            if (nearest == null)
                return List.of();
            sc = nearest[0];
            sr = nearest[1];
        }
        if (!grid.isWalkable(gc, gr)) {
            int[] nearest = grid.findNearestWalkable(gc, gr, 50);
            if (nearest == null)
                return List.of();
            gc = nearest[0];
            gr = nearest[1];
        }

        // Run A*
        List<int[]> rawPath = astar(grid, sc, sr, gc, gr);
        if (rawPath == null)
            return List.of();

        // Simplify with RDP
        List<int[]> simplified = simplifyPath(rawPath, RDP_EPSILON_CELLS);

        // Convert back to lat/lon, skip first (drone) and last (waypoint) unless
        // clamped
        List<double[]> result = new ArrayList<>();
        int limit = simplified.size();
        for (int i = 0; i < limit; i++) {
            if (i == 0 && !clampedS && limit > 1)
                continue;
            if (i == limit - 1 && !clampedG && limit > 1)
                continue;

            double[] latlon = grid.cellToLatlon(simplified.get(i)[0], simplified.get(i)[1]);
            result.add(latlon);
        }
        return result;
    }

    /**
     * Calculate the full planned path for display — used by SwarmOrchestrator.
     * Returns list of [lat, lon] detour points between drone and next waypoint,
     * with the waypoint appended as the final destination.
     *
     * Returns null if no path calculation is needed.
     */
    public List<double[]> calculatePlannedPath(double droneLat, double droneLon,
            double waypointLat, double waypointLon,
            List<Obstacle> obstacles) {
        List<double[]> safePath = getSafePath(droneLat, droneLon,
                waypointLat, waypointLon, obstacles);
        if (!safePath.isEmpty()) {
            List<double[]> full = new ArrayList<>(safePath);
            full.add(new double[] { waypointLat, waypointLon });
            return full;
        }
        return List.of(new double[] { waypointLat, waypointLon });
    }

    /**
     * Perpendicular distance from point to line segment — kept for backward compat.
     */
    private double pointToLineDistance(double px, double py,
            double ax, double ay,
            double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        if (dx == 0 && dy == 0)
            return Math.hypot(px - ax, py - ay);
        double t = Math.max(0, Math.min(1,
                ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }
}
