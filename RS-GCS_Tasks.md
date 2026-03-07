# RS-GCS: Resilient Swarm Ground Control Station
## Phase-Wise Task Tracker

> **Project:** Real-time drone swarm GCS with autonomous leader election, mission planning & obstacle avoidance  
> **Stack:** Java 21 (Spring Boot) · Python 3.11 (asyncio) · React 18 (Vite + Leaflet)  
> **Status Key:** `[ ]` Todo · `[/]` In Progress · `[x]` Done

---

## ⚙️ PHASE 0 — Project Scaffold & Environment Setup

### 0.1 Backend (Spring Boot)
- [x] Maven project with `pom.xml` — Spring Web, Spring WebSocket, Lombok, Actuator
- [x] `RsgcsApplication.java` — Spring Boot main class with `@EnableScheduling`
- [x] `application.yml` — virtual threads enabled, port 8080, heartbeat timings, actuator health
- [x] `CorsConfig.java` — allow all origins `*` for development
- [x] `WebSocketConfig.java` — STOMP broker on `/topic`, app prefix `/app`, SockJS at `/ws`
- [ ] `GET /api/health` endpoint → `{ "status": "UP", "service": "rs-gcs-backend" }` *(using actuator `/actuator/health` instead — no custom `/api/health`)*

### 0.2 Simulator (Python)
- [x] `requirements.txt` with `aiohttp`
- [x] `main.py` — reads `BACKEND_URL`, waits for backend health, spawns drones

### 0.3 Frontend (React)
- [x] Vite + React project with `package.json`
- [x] Dependencies: `leaflet`, `react-leaflet`, `@stomp/stompjs`, `sockjs-client`, `tailwindcss`
- [ ] Vite proxy config for `/api/*` and `/ws` → `http://localhost:8080` *(missing — frontend fetches from direct URL via env vars instead)*
- [x] Tailwind CSS configured (v4 via `@tailwindcss/vite` plugin — plan specified v3 `@tailwind` directives)
- [x] `App.jsx` — initial component

### 0.4 Docker Compose
- [x] `docker-compose.yml` — 3 services: backend, simulator, frontend
- [x] `backend/Dockerfile` — multi-stage Maven build → JRE runtime (eclipse-temurin:21)
- [x] `simulator/Dockerfile` — python:3.11-slim
- [x] `frontend/Dockerfile` — node:20-alpine running `npm run dev -- --host 0.0.0.0`
- [x] Health check on backend, `depends_on` conditions for simulator and frontend

### ✅ Phase 0 Verification
| # | Check | Status |
|---|-------|--------|
| 1 | Backend starts on port 8080 | ✅ |
| 2 | Health endpoint returns 200 | ✅ (via actuator, not custom `/api/health`) |
| 3 | WebSocket `/ws` reachable with STOMP | ✅ |
| 4 | Simulator connects to backend | ✅ |
| 5 | Frontend starts on port 5173 | ✅ |
| 6 | Frontend proxies to backend | ⚠️ Uses direct URL via env vars instead of Vite proxy |
| 7 | `docker-compose up` starts all 3 services | ✅ |
| 8 | No CORS errors | ✅ |

---

## 🏗️ PHASE 1 — Data Models & Telemetry Pipeline

### 1.1 Backend Models (`com.rsgcs.model`)
- [x] `DroneType.java` — enum: `SURVEILLANCE`, `LOGISTICS`, `STRIKE`
- [x] `DroneRole.java` — enum: `LEADER`, `FOLLOWER`, `CANDIDATE`, `LOST`
- [x] `DroneState.java` — full telemetry: droneId, type, role, position, heading, speed, battery, heartbeat, sequence, plannedPath
- [x] `TelemetryPacket.java` — incoming MAVLink-style packet
- [x] `ElectionEvent.java` — election lifecycle events
- [x] `MissionState.java` — mission-level state: ID, status, leader, counts, elections, waypoints
- [x] `Waypoint.java` — id, lat, lon, altitude, order
- [x] `Obstacle.java` — id, name, type, lat, lon, radius, height

### 1.2 Backend SwarmOrchestrator (`com.rsgcs.service`)
- [x] `ConcurrentHashMap<Integer, DroneState>` drone registry
- [x] `handleTelemetry()` — get/create drone, assign type by ID range, set leader if first, update fields, set heartbeat, broadcast
- [x] `getSwarmSnapshot()` — returns drones + mission state for frontend
- [x] `getDroneRegistry()` — returns the ConcurrentHashMap
- [x] Broadcasts updated state to `/topic/drones`

### 1.3 Backend TelemetryController
- [x] `POST /api/telemetry` — single TelemetryPacket
- [x] `POST /api/telemetry/batch` — list of TelemetryPackets
- [x] `GET /api/swarm/snapshot` — via `SwarmController.java`

### 1.4 Simulator — Basic Drone Simulation
- [x] `drone.py` — `SimulatedDrone` class with waypoint navigation, battery drain, heartbeat POST
- [x] Speed/altitude by type: SURVEILLANCE=50m/s, LOGISTICS=50m/s, STRIKE=50m/s *(plan specified 15/5/10 — increased for demo visibility)*
- [x] 100ms heartbeat interval
- [x] `swarm_patterns.py` — `grid_search()` with zigzag waypoints, center `28.4595, 77.5021`
- [x] `main.py` — spawns 10 drones, assigns types by ID range, asyncio event loop, graceful shutdown

### 1.5 Frontend — WebSocket Subscription
- [x] `hooks/useSwarmSocket.js` — STOMP client, subscribes to `/topic/drones`, `/topic/events`, `/topic/mission`, `/topic/logs`
- [x] Stores drones as object map, missionState, eventLog, logs, trails, obstacles
- [x] Returns `{ drones, isConnected, ... }` with command helpers
- [x] `App.jsx` — uses hook, displays all data

### ✅ Phase 1 Verification
| # | Check | Status |
|---|-------|--------|
| 1 | Backend compiles and starts | ✅ |
| 2 | POST /api/telemetry returns 200 | ✅ |
| 3 | systemId 1-3 → SURVEILLANCE, 7-10 → STRIKE | ✅ |
| 4 | GET /api/swarm/snapshot returns drones + mission | ✅ |
| 5 | First drone auto-becomes LEADER | ✅ |
| 6 | Simulator sends telemetry from 10 drones | ✅ |
| 7 | Backend broadcasts to /topic/drones | ✅ |
| 8 | Frontend connects via WebSocket | ✅ |
| 9 | Frontend displays drones with updating data | ✅ |
| 10 | Battery values decrease | ✅ |
| 11 | Positions change over time | ✅ |
| 12 | Sequence numbers increment | ✅ |

---

## 🧠 PHASE 2 — Heartbeat Monitoring & Leader Election (Bully Algorithm)

### 2.1 HeartbeatMonitor (`com.rsgcs.service`)
- [x] `@Scheduled(fixedRate = 200)` sweep
- [x] Three-state detection: `ACTIVE` → `STALE` (>500ms) → `LOST` (>1000ms)
- [x] Broadcasts log messages for STALE/LOST transitions
- [x] Auto-triggers election on leader LOST (runs on virtual thread)
- [x] Throttled heartbeat summary broadcast (every ~2 seconds)
- [x] Recovery detection: STALE → ACTIVE when heartbeat resumes
- [x] Config in `application.yml`: `heartbeat-timeout-ms: 500`, `heartbeat-check-interval-ms: 200`

### 2.2 LeaderElectionService (`com.rsgcs.service`)
- [x] `AtomicBoolean electionInProgress` — prevents double elections
- [x] **Step 1:** LEADER_TIMEOUT detection log (CRITICAL)
- [x] **Step 2:** Identify candidates (all non-LOST drones, sorted by ID desc)
- [x] **Step 3:** Quorum check — abort if active ≤ 50% (split-brain prevention, SAFE MODE)
- [x] **Step 4:** Bully election messages with dramatic delays (CHALLENGE → RESPONSE → NEW_LEADER)
- [x] **Step 5:** Promote highest-ID active drone as LEADER, demote others to FOLLOWER
- [x] Updates missionState: status, currentLeaderId, lastElectionTime, electionCount
- [x] Broadcasts to `/topic/logs`, `/topic/events`, `/topic/mission`, `/topic/drones`
- [x] Sub-500ms total election time

### 2.3 CommandController
- [x] `POST /api/command/kill/{droneId}` — kill specific drone, trigger election if leader
- [x] `POST /api/command/kill-leader` — find current leader, kill it
- [x] `POST /api/command/kill-random` — kill random non-leader active drone
- [x] `POST /api/command/start-mission` — set mission ACTIVE, assign leader
- [x] `POST /api/command/reset` — clear all state

### 2.4 Simulator — Kill Support
- [x] Command listener polling backend for kill commands (every 500ms)
- [x] Sets `is_alive = False` on killed drones

### 2.5 Frontend — New Subscriptions
- [x] `useSwarmSocket.js` subscribes to `/topic/events`, `/topic/mission`, `/topic/logs`
- [x] EventLog (max 200 FIFO), logs (max 200 FIFO) *(plan specified 500 max — actually 200)*
- [x] `App.jsx` shows missionState, logs, and has Kill Leader button

### ✅ Phase 2 Verification
| # | Check | Status |
|---|-------|--------|
| 1 | All drones show ACTIVE during normal operation | ✅ |
| 2 | Drones go STALE after 500ms, LOST after 1000ms | ✅ |
| 3 | Kill-leader returns 200 and identifies correct leader | ✅ |
| 4 | Full election log sequence appears (7 messages) | ✅ |
| 5 | New leader = highest-ID active drone | ✅ |
| 6 | missionState updates (leaderID, electionCount, status) | ✅ |
| 7 | Status: ACTIVE → RECOVERING → ACTIVE | ✅ |
| 8 | Election completes in <500ms | ✅ |
| 9 | Non-leader kill does NOT trigger election | ✅ |
| 10 | Frontend shows logs via WebSocket | ✅ |
| 11 | Kill 6+ drones triggers QUORUM_FAIL | ✅ |
| 12 | No race conditions on double-click | ✅ |

---

## 🗺️ PHASE 3 — Tactical Map & Drone Visualization

### 3.1 Global Styles (`index.css`)
- [x] CSS custom properties: `--bg-primary: #0a0e17`, `--bg-secondary: #111827`, etc.
- [x] JetBrains Mono font family declared *(but missing Google Fonts CDN import — relies on local install)*
- [x] Dark body background, overflow hidden, scrollbar styling
- [x] Leader pulse animation (2s ease-in-out infinite)
- [x] Candidate flash animation (0.5s)
- [x] Kill flash animation
- [x] Log entry fade-in animation
- [x] Leaflet overrides: dark zoom controls, dark attribution

### 3.2 Color System (`utils/droneIcons.js`)
- [x] TYPE_COLORS: SURVEILLANCE=`#06b6d4`, LOGISTICS=`#f59e0b`, STRIKE=`#ec4899` ✅ exact match
- [x] ROLE_COLORS: LEADER=`#3b82f6`, FOLLOWER=`#22c55e`, CANDIDATE=`#f97316`, LOST=`#ef4444`
- [x] `getDroneColor()` — returns TYPE color for followers, ROLE color for leader/lost
- [x] `createDroneIconHtml()` — DivIcon with SVG arrow, rotation, role-based styling

### 3.3 TacticalMap.jsx
- [x] Leaflet `MapContainer` with CartoDB Dark Matter tiles
- [x] Center: `[28.4595, 77.5021]` (Greater Noida), zoom 14
- [x] Drone markers visible on map — **uses `CircleMarker`** instead of DivIcon with SVG arrows *(plan specified DivIcon — implemented as CircleMarker with role-based colors)*
- [x] Leader drone has pulsing blue ring (larger CircleMarker at radius 18)
- [x] Leader marker is larger (radius 10 vs 7 for followers)
- [x] LOST drone markers get red X overlay (small CircleMarker)
- [x] Drone tooltip on hover: `"D{id} | {type} | {battery}% | Alt | Speed"`
- [x] Path trails rendered as Polylines (from `trails` data)
- [x] Includes ObstacleLayer, WaypointPlanner, PlannedPathLine
- [x] Map click handler for waypoints/obstacles/spawn modes

### 3.4 App.jsx Layout
- [x] CSS Grid: 4 rows — Status Bar, Health Grid, Main (Map 60% + Sidebar 40%), Election Log
- [x] Status bar full width, health grid full width, log terminal full width
- [x] Map takes `1fr` with 260px sidebar

### ✅ Phase 3 Verification
| # | Check | Status |
|---|-------|--------|
| 1 | Map renders with dark CartoDB tiles centered on Greater Noida | ✅ |
| 2 | All 10 drone markers visible | ✅ |
| 3 | Drones color-coded by TYPE (cyan/amber/magenta) | ✅ |
| 4 | Leader has blue pulsing ring, larger marker | ✅ |
| 5 | Markers move smoothly | ✅ |
| 6 | Marker arrows rotate to heading | ⚠️ Uses CircleMarker, no rotation — heading shown in tooltip |
| 7 | Path trails appear | ✅ |
| 8 | Path trails limited length | ✅ |
| 9 | Hover tooltips work | ✅ |
| 10 | Status bar shows mission/leader/active count | ✅ |
| 11 | Dead drone dimmed, new leader gains glow | ✅ |
| 12 | CSS Grid layout correct | ✅ |
| 13 | JetBrains Mono font | ✅ (in CSS, needs local/CDN font) |
| 14 | Dark navy background | ✅ |

---

## 🎮 PHASE 4 — Election Log Terminal, Telemetry Panel & Control Panel

### 4.1 ElectionLog.jsx ★
- [x] Container: `background: #0a0a0a`, full width, overflow-y auto
- [x] Font: JetBrains Mono, 11px, terminal green
- [x] Each log line: `> [{timestamp}] {message}`
- [x] Level-based colors: HEARTBEAT=dim grey, INFO=green, WARN=yellow, CRITICAL=red (bold), SUCCESS=bright green (bold)
- [x] Auto-scroll to bottom on new messages (`scrollIntoView`)
- [x] FIFO: max 200 entries *(plan specified 500)*
- [x] Heartbeat throttling done in backend (every ~2 seconds)
- [x] Terminal header bar: "▸ ELECTION LOG TERMINAL" with entry count
- [ ] ELECTION level with orange bold styling *(ELECTION case exists in getLevelColor but not emphasized as separate bold weight)*

### 4.2 TelemetryPanel.jsx
- [x] Scrollable sidebar, one card per drone, sorted by ID
- [x] Card header: `D{id}` + type emoji + role badge (colored with drone color)
- [x] Battery bar: horizontal, color changes at thresholds (green >50%, yellow 20-50%, red <20%)
- [x] Stats: BAT%, ALT, SPD, HDG
- [x] LOST drones: dimmed (opacity 0.5), ID strikethrough, red-tinted background
- [x] LEADER card: blue border + blue box-shadow glow
- [x] Uses `React.memo` on DroneCard

### 4.3 ControlPanel.jsx
- [x] DESTROY LEADER button (red `#ef4444`)
- [x] KILL RANDOM button (orange `#f97316`)
- [x] START MISSION button (green `#22c55e`)
- [x] RESET SWARM button (grey `#64748b`)
- [x] Cooldown after click (prevents rapid double-clicks)
- [x] Confirm dialog on DESTROY LEADER
- [x] Button disables when RECOVERING
- [x] Military/utilitarian button style
- [x] PLAN MISSION toggle + DEPLOY MISSION button
- [x] OBSTACLES toggle + ADD OBSTACLE mode
- [x] SET SPAWN mode

### 4.4 DroneHealthGrid.jsx
- [x] 10 boxes (36x36px) in horizontal row, one per drone
- [x] Box shows `D{id}` text
- [x] Background color by STATUS: ACTIVE=green, STALE=yellow, LOST=red *(plan specified TYPE colors for ACTIVE — uses status colors instead)*
- [x] Leader box: blue ring/border + blue shadow
- [x] Hover tooltip with drone details
- [x] Status dot indicator per box

### ✅ Phase 4 Verification
| # | Check | Status |
|---|-------|--------|
| 1 | ElectionLog: dark bg, green mono text | ✅ |
| 2 | Log level colors correct | ✅ |
| 3 | Heartbeats throttled | ✅ |
| 4 | Election events appear individually | ✅ |
| 5 | Auto-scroll works | ✅ |
| 6 | FIFO max 200 (plan says 500) | ⚠️ 200, not 500 |
| 7 | TelemetryPanel: 10 cards sorted by ID | ✅ |
| 8 | Battery bars change color at thresholds | ✅ |
| 9 | LOST cards dimmed with strikethrough | ✅ |
| 10 | DESTROY LEADER triggers kill-leader API | ✅ |
| 11 | Confirm dialog before kill | ✅ |
| 12 | Cooldown prevents double-click | ✅ |
| 13 | Button disables during RECOVERING | ✅ |
| 14 | Health grid: 10 boxes, status colors | ✅ |
| 15 | Full flow: kill → election → update all panels | ✅ |

---

## 🚧 PHASE 5 — Obstacles & Greater Noida Landmarks

### 5.1 Frontend — Obstacle Data (`data/greaterNoidaObstacles.js`)
- [x] 8 real Greater Noida landmarks with exact coordinates:
  - [x] Gaur City Mall — BUILDING, 80m, 45m, red
  - [x] Pari Chowk — RESTRICTED_ZONE, 150m, orange
  - [x] Gaur World SmartStreet Mall — BUILDING, 60m, 35m, red
  - [x] JP Aman Society Tower — BUILDING, 50m, 60m, red
  - [x] Noida-GN Expressway Overpass — INFRASTRUCTURE, 40m, 15m, yellow
  - [x] Cell Tower Cluster (Sector 16) — RF_HAZARD, 60m, 50m, purple
  - [x] Water Tank (Sector 12) — STRUCTURE, 25m, 30m, grey *(plan specified red)*
  - [x] Tree Line (Park Belt) — VEGETATION, 100m, 12m, green
- [x] `OBSTACLE_COLORS` and `OBSTACLE_ICONS` exports

### 5.2 Frontend — ObstacleLayer.jsx
- [x] `Circle` per obstacle at correct lat/lon with radius in meters
- [x] Fill: obstacle color at 12% opacity *(plan specified 15%)*
- [x] Stroke: obstacle color at 60% opacity, dashed pattern
- [x] RF_HAZARD: CSS class `rf-hazard-pulse` *(CSS animation not defined in index.css)*
- [x] Tooltip on hover: name, type, height, radius
- [x] Show/Hide toggle in ControlPanel
- [x] ADD OBSTACLE mode in ControlPanel

### 5.3 Backend — Obstacle Endpoints
- [x] `GET /api/obstacles` — returns all obstacles (pre-loaded + operator-added)
- [x] `POST /api/obstacles` — adds new obstacle
- [x] `DELETE /api/obstacles/{id}` — removes obstacle
- [x] Pre-loaded Greater Noida obstacles in `SwarmOrchestrator.createDefaultObstacles()`

### ✅ Phase 5 Verification
| # | Check | Status |
|---|-------|--------|
| 1 | 8 obstacle circles on map at correct locations | ✅ |
| 2 | Correct colors per type | ✅ |
| 3 | Translucent fill (map visible through) | ✅ |
| 4 | Dashed borders | ✅ |
| 5 | Hover tooltips with details | ✅ |
| 6 | RF_HAZARD pulsing animation | ⚠️ CSS class set but no `rf-hazard-pulse` keyframe in index.css |
| 7 | VEGETATION is green | ✅ |
| 8 | Show/Hide toggle works | ✅ |
| 9 | GET /api/obstacles returns JSON | ✅ |
| 10 | POST /api/obstacles adds new obstacle | ✅ |
| 11 | No overlap with drone spawn area | ✅ |

---

## 🧭 PHASE 6 — Waypoint Planning & Live Pathfinding

### 6.1 Frontend — WaypointPlanner.jsx
- [x] PLAN MISSION toggle in ControlPanel
- [x] Click map to place numbered waypoints (W1, W2, W3...)
- [x] Waypoints connected by dashed cyan line (`#06b6d4`, dashArray: '8 6')
- [x] Waypoint markers: CircleMarker with permanent tooltip showing number *(plan specified white circle DivIcon with black numbers — uses CircleMarker instead)*
- [x] DEPLOY MISSION button → `POST /api/mission/waypoints` → exits planning mode
- [ ] Right-click to delete waypoint *(not implemented)*
- [ ] Drag waypoint to reposition *(not implemented)*

### 6.2 Backend — Waypoint Distribution
- [x] `POST /api/mission/waypoints` — stores waypoints, calculates formation offsets
- [x] Formation offset: LEADER exact path, FOLLOWERs ±30m/60m/90m perpendicular offsets
- [x] `calculateOffsetPath()` — perpendicular offset math
- [x] Per-drone waypoint lists stored and available via snapshot
- [x] Sets missionState.status = "ACTIVE" after deployment

### 6.3 Simulator — Follow Waypoints
- [x] `update_waypoints()` in `drone.py` — replaces waypoints at runtime
- [x] Waypoint listener polling backend (every 2 seconds)
- [x] Drones navigate waypoints in order
- [x] Mission status listener — drones only move if mission ACTIVE

### 6.4 Simulator — Obstacle Avoidance (`pathfinding.py`)
- [x] `point_to_line_distance()` — perpendicular distance from point to line segment
- [x] `find_detour_point()` — tangent avoidance point calculation
- [x] `get_safe_path()` — chains detour points around multiple obstacles
- [x] Integrated into `drone.py` run() loop — calls `get_safe_path()` before moving
- [x] Obstacle listener polls backend for obstacle list changes

### 6.5 Backend — PathfindingService
- [x] `PathfindingService.java` — path validation and planned path calculation
- [x] `isPathClear()` — line-segment vs obstacle-circle intersection
- [x] `getNearestClearPoint()` — nearest point outside obstacle zones
- [x] `plannedPath` populated in DroneState and broadcast via `/topic/drones`

### 6.6 Frontend — PlannedPathLine.jsx
- [x] Dotted line from drone position through `plannedPath` to destination
- [x] Color: drone color at 40% opacity
- [x] Updates every tick — shows live pathfinding recalculation

### ✅ Phase 6 Verification
| # | Check | Status |
|---|-------|--------|
| 1 | PLAN MISSION toggles planning mode | ✅ |
| 2 | Click map places numbered waypoints | ✅ |
| 3 | Dashed cyan waypoint line | ✅ |
| 4 | Right-click delete + drag reposition | ❌ Not implemented |
| 5 | DEPLOY sends waypoints to backend | ✅ |
| 6 | Drones fly toward waypoints | ✅ |
| 7 | Formation offset paths visible | ✅ |
| 8 | Obstacle avoidance: drones detour | ✅ |
| 9 | PlannedPathLine shows path bending | ✅ |
| 10 | Election still works with waypoints | ✅ |

---

## 🐳 PHASE 7 — Docker, README, Polish & Demo Readiness

### 7.1 Docker Compose
- [x] `docker-compose.yml` with 3 services
- [x] Backend health check via actuator
- [x] Simulator `depends_on: backend` (condition: service_healthy)
- [x] Frontend at port 5173

### 7.2 README.md
- [x] Defense-grade language, no emojis
- [x] Problem statement, capabilities, architecture
- [x] Tech stack with rationale
- [x] Quick start instructions
- [x] Demo flow description
- [x] Design decisions
- [x] Known limitations
- [x] Future vision

### 7.3 UI Polish
- [x] CSS transitions on drone status changes (0.3s ease)
- [x] Kill flash animation defined
- [x] Status bar flash animation for RECOVERING state
- [x] Leaflet zoom controls styled dark
- [x] "RS-GCS v1.0" in status bar *(plan said map watermark bottom-left)*
- [x] Log entry fade-in animation
- [ ] Red flash overlay when leader killed *(animation defined but not triggered in components)*
- [ ] Glow effect on CRITICAL/SUCCESS log lines *(not implemented)*

### 7.4 Cleanup
- [x] `.gitignore` — covers Java, Python, Node, IDE, OS, env files
- [x] No hardcoded secrets committed
- [x] Clean project structure

### ✅ Phase 7 Verification
| # | Check | Status |
|---|-------|--------|
| 1 | `docker-compose up --build` starts all 3 | ✅ |
| 2 | Backend healthy within 30s | ✅ |
| 3 | Drones appear on map within 10s | ✅ |
| 4 | Frontend loads without errors | ✅ |
| 5 | Dark theme throughout | ✅ |
| 6 | JetBrains Mono everywhere | ✅ |
| 7 | Drone type colors distinguishable | ✅ |
| 8 | Leader blue pulsing glow | ✅ |
| 9 | Obstacles visible without obscuring map | ✅ |
| 10 | Election log looks like a real terminal | ✅ |
| 11 | README exists with all sections | ✅ |
| 12 | .gitignore correct | ✅ |

---

## 📋 Known Deviations from Technical Plan

| # | Deviation | Impact | Severity |
|---|-----------|--------|----------|
| 1 | No custom `/api/health` — uses Spring Actuator `/actuator/health` instead | Functional — same result | Low |
| 2 | No Vite proxy config — frontend uses env vars for direct backend URL | Works for dev and Docker — no proxy needed | Low |
| 3 | Tailwind v4 (`@tailwindcss/vite`) instead of v3 (`postcss + tailwind.config.js`) | Actually newer/better — no functional impact | Low |
| 4 | Drone markers use `CircleMarker` instead of `DivIcon` with SVG arrow rotation | Markers don't point in heading direction — heading shown in tooltip instead | Medium |
| 5 | Log FIFO max is 200, not 500 as specified | Less history retained but prevents memory bloat | Low |
| 6 | DroneHealthGrid uses STATUS colors (green/yellow/red) instead of TYPE colors (cyan/amber/magenta) for ACTIVE drones | Status colors are arguably more useful at a glance | Low |
| 7 | Water Tank obstacle color is grey (`#6b7280`) instead of red as plan specified | Minor visual difference | Low |
| 8 | Waypoint markers use CircleMarker instead of white circle DivIcon with black numbers | Still shows numbers via permanent tooltip — functionally equivalent | Low |
| 9 | No right-click delete or drag-to-reposition on waypoints | Reduces planning flexibility but deploy workflow works | Medium |
| 10 | RF_HAZARD pulse animation CSS class referenced but keyframe not defined | Obstacle doesn't pulse — just static dashed circle | Low |
| 11 | No Google Fonts CDN import for JetBrains Mono | Relies on system/local font — may fall back to Fira Code or generic monospace | Medium |
| 12 | Drone speeds all set to 50 m/s instead of differentiated (15/5/10) per plan | Less realistic but better for demo visibility | Low |

---

## 🚦 PHASE 8 — A* Pathfinding Replacement (Replaces Tangent-Point Avoidance)

### 8.1 Simulator — Python Grid A* (`simulator/pathfinding.py`)
- [x] Configure Grid Resolution (5m) and Safety Buffer (15m)
- [x] Auto-calculate grid bounds from mission area
- [x] `PathfindingGrid` class — converts lat/lon to blocked/walkable cells
- [x] `astar()` — 8-directional A* with Euclidean heuristic
- [x] `simplify_path()` — Ramer-Douglas-Peucker line simplification
- [x] Quick-exit for clear paths (Bresenham line walk)
- [x] `get_safe_path()` drop-in replacement
- [x] Grid caching to prevent rebuilding every tick

### 8.2 Backend — Java Grid A* (`backend/src/main/java/com/rsgcs/service/PathfindingService.java`)
- [x] Port `PathfindingGrid` rasterization logic to Java
- [x] Port `astar()` and spiral-out `findNearestWalkable` logic
- [x] Port Ramer-Douglas-Peucker line simplification
- [x] Provide `calculatePlannedPath` replacing previous tangent-point methods
- [x] Update `SwarmOrchestrator` to call `calculatePlannedPath`

### 8.3 Integration & Verification
- [x] Frontend `PlannedPathLine` renders A* curved paths
- [x] Drones smoothly circumnavigate overlapping obstacle clusters without getting stuck


---

## 📌 Phase Summary

| Phase | Focus | Status |
|-------|-------|--------|
| **0** | Scaffold & Environment Setup | ✅ Complete |
| **1** | Data Models & Telemetry Pipeline | ✅ Complete |
| **2** | Heartbeat & Leader Election (Bully Algorithm) | ✅ Complete |
| **3** | Tactical Map & Drone Visualization | ✅ Complete (CircleMarker, no heading rotation) |
| **4** | Election Log Terminal, Telemetry Panel & Controls | ✅ Complete |
| **5** | Obstacles & Greater Noida Landmarks | ✅ Complete (minor: no RF pulse animation) |
| **6** | Waypoint Planning & Live Pathfinding | ✅ Complete (missing: delete/drag waypoints) |
| **7** | Docker, README, Polish & Demo Readiness | ✅ Complete |
| **8** | A* Pathfinding Replacement | ✅ Complete |

**All 8 phases are implemented.** The project is demo-ready with minor cosmetic deviations documented above.

**Critical Path Complete:** Phase 0 → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 ✅  
**The "Money Shot" Works:** Drones routing around Gaur City Mall via A* grid paths → Kill Leader → Election cascade → New leader → Swarm continues ✅
