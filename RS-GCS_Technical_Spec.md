# RS-GCS: Resilient Swarm Ground Control Station

## TECHNICAL SPECIFICATION FOR AI-ASSISTED DEVELOPMENT

> **Purpose of this document:** This is the COMPLETE technical spec for building the RS-GCS prototype. It is designed to be fed directly into Cursor, Claude Code, or Google Antigravity as the single source of truth. Every file, every function, every design decision is specified here. The AI agent should be able to build the entire project from this document alone.

> **Timeline:** 2 days (aggressive prototype)

> **Developer profile:** 3-year Java backend developer (Spring Boot, REST APIs). No prior ROS/C++/drone experience. This project is a career pivot piece targeting Indian defense startups.

---

## 1. WHAT THIS PROJECT IS (AND IS NOT)

### What it IS:
- A **real-time web dashboard** that simulates 5-10 drones flying a coordinated search mission
- A **Java Spring Boot backend** that acts as the "Swarm Orchestrator" managing all drone state
- A **Python simulation layer** that spawns fake drones sending heartbeat/telemetry data
- A **React frontend** that looks like a military Ground Control Station (dark theme, live map, scrolling logs)
- A **leader election demo** — when you kill the leader drone, the system auto-elects a new one in <500ms

### What it is NOT:
- NOT a real drone controller (no actual hardware)
- NOT using ROS, ArduPilot, or any robotics framework
- NOT a physics simulation (drones move in simple patterns, no aerodynamics)
- NOT production-grade security (no mTLS, no real encryption)

### The "Money Shot" (what gets the interview):
A 60-second screen recording where:
1. 10 drones are flying a grid search pattern on a map
2. You click "KILL LEADER" button
3. The log terminal explodes with election messages
4. A new leader is elected in <500ms
5. The swarm reorganizes and continues the mission

---

## 2. TECH STACK (EXACT VERSIONS)

```
BACKEND:
- Java 21 (LTS) — we need Virtual Threads (Project Loom)
- Spring Boot 3.2+
- Spring WebSocket (STOMP over SockJS)
- Maven (not Gradle)
- Jackson for JSON serialization

SIMULATION:
- Python 3.11+
- asyncio (for concurrent drone instances)
- websockets library (pip install websockets)

FRONTEND:
- React 18 (Create React App or Vite — use Vite for speed)
- Leaflet.js (for the map — it's free, no API key needed unlike Mapbox)
- react-leaflet
- OpenStreetMap tiles (free, no API key)
- @stomp/stompjs (for WebSocket connection to Spring Boot)
- Tailwind CSS (for rapid dark-theme styling)

DEPLOYMENT:
- Docker + Docker Compose (single `docker-compose up` to run everything)
```

### Why these specific choices:
- **Leaflet over Mapbox**: No API key hassle. Free tiles. Works offline-ish. For a prototype demo, it looks identical.
- **STOMP over raw WebSocket**: Spring Boot has first-class STOMP support. Less boilerplate. Message topics make it easy to broadcast to all frontend clients.
- **Vite over CRA**: Faster dev server. Faster builds. Better for a 2-day sprint.
- **Python asyncio over threading**: Lighter weight. One Python process can simulate 10 drones easily using coroutines.

---

## 3. PROJECT STRUCTURE

```
rs-gcs/
├── docker-compose.yml
├── README.md                          # Defense-grade README (see Section 12)
│
├── backend/                           # Java Spring Boot
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/rsgcs/
│       ├── RsgcsApplication.java      # Main Spring Boot entry
│       ├── config/
│       │   └── WebSocketConfig.java   # STOMP WebSocket configuration
│       ├── model/
│       │   ├── DroneState.java        # Core drone data model
│       │   ├── DroneRole.java         # Enum: LEADER, FOLLOWER, CANDIDATE, LOST
│       │   ├── DroneType.java         # Enum: SURVEILLANCE, LOGISTICS, STRIKE
│       │   ├── TelemetryPacket.java   # Incoming data from Python drones
│       │   ├── ElectionEvent.java     # Election log entry
│       │   └── MissionState.java      # Overall swarm mission status
│       ├── service/
│       │   ├── SwarmOrchestrator.java      # Core: manages all drone state
│       │   ├── HeartbeatMonitor.java       # Detects dead drones
│       │   ├── LeaderElectionService.java  # Bully Algorithm implementation
│       │   └── MissionCoordinator.java     # Assigns waypoints/patterns
│       ├── controller/
│       │   ├── TelemetryController.java    # REST endpoint for Python drones to POST telemetry
│       │   ├── CommandController.java      # REST endpoints for frontend (kill drone, start mission)
│       │   └── DashboardWebSocket.java     # STOMP message broker for real-time frontend updates
│       └── util/
│           └── MavlinkPacketParser.java    # Parses MAVLink-style JSON packets
│
├── simulator/                         # Python drone simulator
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── main.py                        # Entry: spawns N drone coroutines
│   ├── drone.py                       # Single drone logic (heartbeat, movement, telemetry)
│   ├── swarm_patterns.py              # Movement patterns (grid search, circle, line)
│   └── mavlink_emulator.py            # Generates MAVLink-style JSON packets
│
└── frontend/                          # React dashboard
    ├── Dockerfile
    ├── package.json
    ├── tailwind.config.js
    ├── index.html
    └── src/
        ├── App.jsx                    # Main layout: map + sidebar + logs
        ├── main.jsx                   # Vite entry point
        ├── index.css                  # Tailwind imports + custom dark theme
        ├── hooks/
        │   └── useSwarmSocket.js      # Custom hook: STOMP WebSocket connection
        ├── components/
        │   ├── TacticalMap.jsx        # Leaflet map with drone markers
        │   ├── DroneMarker.jsx        # Individual drone icon on map (color-coded by role)
        │   ├── TelemetryPanel.jsx     # Right sidebar: per-drone stats
        │   ├── ElectionLog.jsx        # Bottom terminal: scrolling green-text log
        │   ├── SwarmStatusBar.jsx     # Top bar: total drones, leader ID, mission status
        │   ├── ControlPanel.jsx       # Buttons: Kill Leader, Kill Random, Start Mission, Reset
        │   └── DroneHealthGrid.jsx    # Grid of small boxes (green/yellow/red per drone)
        └── utils/
            └── droneIcons.js          # SVG icons for drone types + role colors
```

---

## 4. BACKEND — COMPLETE SPECIFICATION

### 4.1 `pom.xml` dependencies

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.3</version>
</parent>

<properties>
    <java.version>21</java.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

Enable virtual threads in `application.yml`:
```yaml
spring:
  threads:
    virtual:
      enabled: true
  
server:
  port: 8080

rsgcs:
  heartbeat-timeout-ms: 500
  heartbeat-check-interval-ms: 200
  max-drones: 10
  election-timeout-ms: 300
```

### 4.2 Models (Data Classes)

#### `DroneState.java`
```
Fields:
  - droneId: int (1-10, unique)
  - droneType: DroneType (SURVEILLANCE, LOGISTICS, STRIKE)
  - role: DroneRole (LEADER, FOLLOWER, CANDIDATE, LOST)
  - latitude: double
  - longitude: double
  - altitude: double (meters)
  - heading: double (degrees 0-360)
  - speed: double (m/s)
  - batteryPercent: double (0-100)
  - lastHeartbeat: Instant (java.time.Instant)
  - status: String ("ACTIVE", "STALE", "LOST")
  - sequenceNumber: long (increments with each packet)
```

#### `DroneType.java` (Enum)
```
SURVEILLANCE  — fast, low battery, equipped with cameras
LOGISTICS     — slow, high battery, heavy payload
STRIKE        — medium speed, medium battery, armed
```

Assign types based on droneId:
- Drones 1-3: SURVEILLANCE
- Drones 4-6: LOGISTICS  
- Drones 7-10: STRIKE
- This demonstrates "heterogeneous" swarm management (key NRT selling point)

#### `DroneRole.java` (Enum)
```
LEADER     — currently commanding the swarm
FOLLOWER   — executing orders from leader
CANDIDATE  — participating in an election
LOST       — heartbeat timeout, presumed dead
```

#### `TelemetryPacket.java`
```
This mimics a MAVLink HEARTBEAT + GLOBAL_POSITION_INT message.

Fields:
  - systemId: int          (which drone — maps to droneId)
  - componentId: int       (always 1 for flight controller)
  - sequenceNumber: long   (monotonically increasing per drone)
  - messageType: String    ("HEARTBEAT", "POSITION", "STATUS")  
  - latitude: double
  - longitude: double
  - altitude: double
  - heading: double
  - speed: double
  - batteryPercent: double
  - timestamp: long        (epoch millis)
```

#### `ElectionEvent.java`
```
Fields:
  - timestamp: Instant
  - eventType: String ("LEADER_TIMEOUT", "ELECTION_START", "ELECTION_MSG", "COORDINATOR", "NEW_LEADER")
  - initiatorId: int
  - targetId: int (optional)
  - message: String (human-readable log line)
```

#### `MissionState.java`
```
Fields:
  - missionId: String (UUID)
  - status: String ("IDLE", "ACTIVE", "PAUSED", "RECOVERING")
  - currentLeaderId: int
  - activeDroneCount: int
  - totalDroneCount: int
  - missionStartTime: Instant
  - lastElectionTime: Instant (nullable)
  - electionCount: int
```

### 4.3 Service Layer (THE CORE LOGIC)

#### `SwarmOrchestrator.java`
```
This is the central brain. It holds ALL drone state in a ConcurrentHashMap.

Fields:
  - Map<Integer, DroneState> droneRegistry  (ConcurrentHashMap)
  - MissionState missionState
  - HeartbeatMonitor heartbeatMonitor (injected)
  - LeaderElectionService electionService (injected)
  - SimpMessagingTemplate messagingTemplate (for broadcasting to frontend via STOMP)

Methods:

  handleTelemetry(TelemetryPacket packet):
    1. Get or create DroneState for packet.systemId
    2. Update all fields (lat, lon, alt, heading, speed, battery)
    3. Update lastHeartbeat to Instant.now()
    4. Update sequenceNumber (check for gaps — log if packet was lost)
    5. If drone was previously LOST and is now sending again, mark as FOLLOWER and log "DRONE RECONNECTED"
    6. Broadcast updated drone state to frontend via STOMP topic "/topic/drones"

  killDrone(int droneId):
    1. Mark drone as LOST in droneRegistry
    2. If killed drone was the LEADER, trigger electionService.startElection()
    3. Broadcast kill event to frontend via "/topic/events"
    4. Send HTTP signal to Python simulator to stop that drone's coroutine

  getSwarmSnapshot():
    Returns: List<DroneState> — all current drones for frontend initial load

  getDroneById(int id):
    Returns: DroneState or null
```

#### `HeartbeatMonitor.java`
```
This runs a background scheduled task that sweeps the droneRegistry every 200ms.

Uses: @Scheduled(fixedRate = 200) with virtual threads

Logic:
  For each drone in droneRegistry:
    elapsed = Duration.between(drone.lastHeartbeat, Instant.now()).toMillis()
    
    if elapsed > 500ms AND drone.status == "ACTIVE":
      drone.status = "STALE"
      broadcast warning to frontend: "[WARN] Drone_{id} heartbeat stale ({elapsed}ms)"
    
    if elapsed > 1000ms AND drone.status != "LOST":
      drone.status = "LOST"  
      drone.role = LOST
      broadcast critical to frontend: "[CRITICAL] Drone_{id} LOST — no heartbeat for {elapsed}ms"
      
      if drone was LEADER:
        electionService.startElection()

NOTE: The "3-strike rule" — don't trigger election on first stale. Wait for full LOST status (1000ms).
This prevents false elections from network glitches.
```

#### `LeaderElectionService.java` — THE BULLY ALGORITHM
```
This is the MOST IMPORTANT class in the entire project. This is what gets you the interview.

Fields:
  - AtomicBoolean electionInProgress
  - SwarmOrchestrator orchestrator (injected)
  - SimpMessagingTemplate messagingTemplate

Method: startElection()

  STEP 1 — DETECTION LOG:
    Log: "[CRITICAL] LEADER_TIMEOUT: Drone_{oldLeaderId} — initiating election protocol"
    Set electionInProgress = true
    Set missionState.status = "RECOVERING"
    Broadcast ElectionEvent to frontend

  STEP 2 — IDENTIFY CANDIDATES:
    Get all drones where status != "LOST" and role != "LOST"
    Sort by droneId descending (highest ID first — this is the "Bully")
    
  STEP 3 — BULLY ELECTION:
    The Bully Algorithm is simple:
    - The highest-ID ACTIVE drone becomes the new leader
    - But we simulate the "election messages" to make the demo dramatic
    
    For visual effect, simulate this sequence with small delays (50-100ms each):
    
    a) The second-highest active drone "initiates" the election:
       Log: "[INFO] ELECTION_START: Initiated by Drone_{initiatorId}"
       
    b) It sends ELECTION messages to all higher-ID active drones:
       For each higher drone:
         Log: "[INFO] ELECTION_MSG: Drone_{initiatorId} → Drone_{targetId}"
       
    c) The highest-ID active drone responds:
       Log: "[INFO] ELECTION_RESPONSE: Drone_{highestId} asserts leadership"
       
    d) The highest-ID drone broadcasts COORDINATOR:
       Log: "[SUCCESS] NEW_LEADER: Drone_{highestId} confirmed as Swarm Leader"
    
  STEP 4 — PROMOTE NEW LEADER:
    newLeader.role = LEADER
    All other active drones: role = FOLLOWER
    missionState.currentLeaderId = newLeaderId
    missionState.status = "ACTIVE"
    missionState.lastElectionTime = Instant.now()
    missionState.electionCount++
    
  STEP 5 — MISSION CONTINUITY:
    Log: "[INFO] MISSION_SYNC: Swarm pivoting to Drone_{newLeaderId} command vector"
    Broadcast full updated swarm state to frontend
    Set electionInProgress = false

  TOTAL TIME from detection to new leader: Should be < 500ms
  The small delays (50-100ms) between log lines make the demo visually dramatic
  without actually being slow.

QUORUM CHECK (Raft-lite addition):
  Before confirming new leader, check:
    activeDroneCount > totalDroneCount / 2
  If NOT (split-brain scenario):
    Log: "[CRITICAL] QUORUM_FAIL: Only {n}/{total} drones reachable. Election aborted. Entering SAFE MODE."
    missionState.status = "PAUSED"
  This is the "interview killer" detail that shows you understand distributed systems.
```

#### `MissionCoordinator.java`
```
Assigns waypoints to drones based on a search pattern.

Method: generateGridSearchPattern(double centerLat, double centerLon, int droneCount)
  
  Creates a simple grid search pattern:
  - Divide an area (e.g., 500m x 500m) into strips
  - Assign each drone a strip to fly back and forth
  - The LEADER drone gets the center strip
  - SURVEILLANCE drones get the perimeter
  - LOGISTICS drones hold a staging position
  - STRIKE drones orbit the area
  
  Returns: Map<Integer, List<double[]>> — droneId → list of [lat, lon] waypoints

  Use these coordinates as the CENTER of the search area:
    latitude: 28.4595 (Greater Noida — near NRT's actual office!)
    longitude: 77.5021
  
  This is a subtle touch — when NRT engineers see their own neighborhood on the map, 
  it shows you did your homework.
```

### 4.4 Controllers (REST + WebSocket)

#### `TelemetryController.java`
```
POST /api/telemetry
  Body: TelemetryPacket (JSON)
  Logic: calls swarmOrchestrator.handleTelemetry(packet)
  Returns: 200 OK

POST /api/telemetry/batch
  Body: List<TelemetryPacket>
  Logic: processes multiple packets at once (for efficiency)
  Returns: 200 OK
```

#### `CommandController.java`
```
POST /api/command/kill/{droneId}
  Logic: calls swarmOrchestrator.killDrone(droneId)
  Returns: 200 with message "Drone {id} terminated"

POST /api/command/kill-leader
  Logic: finds current leader, calls killDrone on it
  Returns: 200 with message "Leader Drone {id} terminated"

POST /api/command/start-mission
  Logic: initializes mission, tells Python simulator to start all drones
  Returns: 200

POST /api/command/reset
  Logic: resets all state, restarts all drones
  Returns: 200

GET /api/swarm/snapshot
  Returns: JSON with { drones: [...], mission: {...} }
```

#### `DashboardWebSocket.java` (WebSocket Config)
```
Configure STOMP message broker:
  - Enable simple broker on "/topic"
  - Set application destination prefix "/app"
  - Register STOMP endpoint at "/ws" with SockJS fallback
  - Allow origins: "*" (for development)

STOMP Topics (frontend subscribes to these):
  /topic/drones     — real-time drone state updates (fires every 100ms per drone)
  /topic/events     — election events, warnings, critical alerts
  /topic/mission    — mission state changes
  /topic/logs       — raw log lines for the terminal display
```

### 4.5 CORS Configuration
```
Allow all origins for development (the frontend runs on a different port).
Create a WebMvcConfigurer bean:
  addCorsMappings: allow "/**" from "*" with all methods
```

---

## 5. PYTHON SIMULATOR — COMPLETE SPECIFICATION

### 5.1 `requirements.txt`
```
aiohttp==3.9.3
asyncio-mqtt==0.16.1    # optional, not needed for prototype
```

### 5.2 `main.py`
```
Entry point. Spawns N drone coroutines.

Logic:
  1. Parse environment variables:
     - BACKEND_URL (default: http://localhost:8080)
     - NUM_DRONES (default: 10)
     - HEARTBEAT_INTERVAL_MS (default: 100)
     - CENTER_LAT (default: 28.4595)
     - CENTER_LON (default: 77.5021)
  
  2. Create an aiohttp.ClientSession
  
  3. For each drone (1 to NUM_DRONES):
     - Assign DroneType based on ID (1-3: SURVEILLANCE, 4-6: LOGISTICS, 7-10: STRIKE)
     - Create a Drone instance
     - Start its coroutine
  
  4. Also start a "command listener" coroutine that polls GET /api/simulator/commands
     to check if the backend wants to kill a specific drone.
     (Alternatively, use a simple HTTP endpoint that the backend POSTs to)
  
  5. asyncio.gather() all coroutines
  
  6. Handle graceful shutdown on SIGINT/SIGTERM
```

### 5.3 `drone.py`
```
Single drone simulation coroutine.

Class: SimulatedDrone

Fields:
  - drone_id: int
  - drone_type: str ("SURVEILLANCE", "LOGISTICS", "STRIKE")
  - lat: float
  - lon: float
  - altitude: float
  - heading: float
  - speed: float (m/s — varies by type: SURVEILLANCE=15, LOGISTICS=5, STRIKE=10)
  - battery: float (starts at 100.0, decreases by 0.01 per heartbeat)
  - sequence_number: int (starts at 0, increments per packet)
  - is_alive: bool
  - waypoints: list of (lat, lon) tuples
  - current_waypoint_index: int
  - session: aiohttp.ClientSession
  - backend_url: str

Method: async run()
  While is_alive:
    1. Move toward current waypoint:
       - Calculate bearing from (lat, lon) to waypoints[current_waypoint_index]
       - Move by (speed * heartbeat_interval) in that direction
       - If within 10m of waypoint, advance to next waypoint (wrap around)
       - Update heading based on bearing
    
    2. Drain battery:
       - battery -= 0.01 per tick
       - If battery < 10: slow down speed by 50%
       - If battery < 0: is_alive = False (simulate crash)
    
    3. Build TelemetryPacket JSON:
       {
         "systemId": drone_id,
         "componentId": 1,
         "sequenceNumber": sequence_number,
         "messageType": "HEARTBEAT",
         "latitude": lat,
         "longitude": lon,
         "altitude": altitude,
         "heading": heading,
         "speed": speed,
         "batteryPercent": battery,
         "timestamp": current_epoch_millis
       }
    
    4. POST to backend_url + "/api/telemetry"
       - Use aiohttp session
       - Don't await response (fire-and-forget for speed)
       - On failure: log warning, continue (resilient to backend hiccups)
    
    5. Increment sequence_number
    
    6. await asyncio.sleep(heartbeat_interval_ms / 1000)

Method: kill()
  Set is_alive = False
  This stops the run() loop, simulating drone destruction

Method: get_movement_params()
  Returns speed/altitude based on drone_type:
    SURVEILLANCE: speed=15 m/s, altitude=120m
    LOGISTICS:    speed=5 m/s,  altitude=80m
    STRIKE:       speed=10 m/s, altitude=150m
```

### 5.4 `swarm_patterns.py`
```
Generates waypoint lists for different search patterns.

Function: grid_search(center_lat, center_lon, num_drones, area_size_meters=500)
  
  Divides the area into vertical strips (one per drone).
  Each strip is a back-and-forth "lawnmower" pattern.
  
  Returns: dict of { drone_id: [(lat1, lon1), (lat2, lon2), ...] }
  
  Implementation:
    - Convert area_size_meters to approximate lat/lon degrees:
      1 degree latitude ≈ 111,320 meters
      1 degree longitude ≈ 111,320 * cos(latitude) meters
    - Divide area into num_drones vertical strips
    - For each strip, create waypoints at top and bottom (zigzag)
    - Add 4-6 waypoints per strip for smooth movement

Function: circular_orbit(center_lat, center_lon, radius_meters, num_drones)
  Creates concentric circular paths.
  Returns: dict of { drone_id: [(lat1, lon1), ...] }
```

### 5.5 `mavlink_emulator.py`
```
Wraps telemetry data in MAVLink-style packet structure.
This is cosmetic but shows defense domain awareness.

Function: create_heartbeat_packet(drone_id, drone_type, status)
  Returns dict with MAVLink-standard field names:
  {
    "header": {
      "stx": 253,           # MAVLink v2 start byte
      "len": <payload_len>,
      "seq": <sequence>,
      "sysid": drone_id,
      "compid": 1,
      "msgid": 0             # 0 = HEARTBEAT in MAVLink
    },
    "payload": {
      "type": <MAV_TYPE>,    # 2=quadrotor, 10=ground_rover, etc.
      "autopilot": 3,        # 3 = ArduPilot
      "base_mode": 217,      # Armed + Guided + Custom
      "system_status": 4     # 4 = ACTIVE
    }
  }

NOTE FOR THE AI AGENT: This function is OPTIONAL for the prototype. 
The simpler JSON format in drone.py works fine. Only implement this 
if there's time left on day 2. It's a "polish" feature.
```

---

## 6. FRONTEND — COMPLETE SPECIFICATION

### 6.1 Design Requirements (CRITICAL — this is 60% of the demo impact)

**Theme:** Military Ground Control Station. Think dark, utilitarian, functional.

**Color Palette:**
```css
--bg-primary:    #0a0e17    /* near-black navy */
--bg-secondary:  #111827    /* dark grey */
--bg-panel:      #1a1f2e    /* panel backgrounds */
--border:        #2a3142    /* subtle borders */
--text-primary:  #e2e8f0    /* off-white */
--text-secondary:#94a3b8    /* muted grey */
--accent-green:  #22c55e    /* healthy/active */
--accent-red:    #ef4444    /* critical/dead */
--accent-yellow: #eab308    /* warning/stale */
--accent-blue:   #3b82f6    /* leader/info */
--accent-orange: #f97316    /* election events */
--terminal-green:#4ade80    /* log terminal text */
```

**Font:** `"JetBrains Mono", "Fira Code", monospace` for the entire UI. This gives it the "ops center" feel.

Import from Google Fonts:
```html
<link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@300;400;500;700&display=swap" rel="stylesheet">
```

### 6.2 Layout (Grid)

```
┌──────────────────────────────────────────────────────────────┐
│  SWARM STATUS BAR (full width, 48px height)                  │
│  [Mission: ACTIVE] [Leader: Drone_10] [Active: 9/10] [Time] │
├──────────────────────────────────┬───────────────────────────┤
│                                  │                           │
│                                  │   TELEMETRY PANEL         │
│         TACTICAL MAP             │   (per-drone cards)       │
│         (Leaflet map)            │   Battery, Alt, Speed     │
│         60% width                │   Status indicator        │
│                                  │   40% width               │
│                                  │                           │
│                                  ├───────────────────────────┤
│                                  │   CONTROL PANEL           │
│                                  │   [KILL LEADER] [KILL     │
│                                  │    RANDOM] [RESET] [START]│
├──────────────────────────────────┴───────────────────────────┤
│  DRONE HEALTH GRID (10 small boxes — one per drone)          │
│  [D1 ■] [D2 ■] [D3 ■] [D4 ■] [D5 ■] ... [D10 ■]          │
├──────────────────────────────────────────────────────────────┤
│  ELECTION LOG TERMINAL (full width, 200px height)            │
│  > [09:42:01] [HEARTBEAT] Drone_10 — OK (lat:28.459 ...)    │
│  > [09:42:10] [CRITICAL] LEADER_TIMEOUT: Drone_10            │
│  > [09:42:10] [INFO] ELECTION_START: Initiated by Drone_09   │
│  > [09:42:10] [SUCCESS] NEW_LEADER: Drone_09 confirmed       │
└──────────────────────────────────────────────────────────────┘
```

### 6.3 Component Specifications

#### `App.jsx`
```
Main layout container. Uses CSS Grid.
On mount:
  1. Connect to STOMP WebSocket at ws://localhost:8080/ws
  2. Subscribe to /topic/drones, /topic/events, /topic/mission, /topic/logs
  3. Fetch initial state from GET /api/swarm/snapshot
  4. Store all state in React useState hooks

State:
  - drones: Map<droneId, DroneState>
  - missionState: MissionState object
  - eventLog: Array of ElectionEvent (max 200, FIFO)
  - isConnected: boolean
```

#### `TacticalMap.jsx`
```
Leaflet map component.

Setup:
  - Center: [28.4595, 77.5021] (Greater Noida)
  - Zoom: 15
  - Tile layer: OpenStreetMap (https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png)
  - Dark map style: Use CartoDB dark tiles instead:
    https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png
    (This gives the military dark-map look for FREE)

For each drone in drones state:
  - Render a custom marker at (drone.latitude, drone.longitude)
  - Marker color based on role:
    LEADER:   bright blue (#3b82f6) + larger size + pulsing animation
    FOLLOWER: green (#22c55e)
    CANDIDATE: orange (#f97316) + flashing
    LOST:     red (#ef4444) + X icon
  - Marker shape based on type:
    SURVEILLANCE: triangle pointing in heading direction
    LOGISTICS:    square
    STRIKE:       diamond
  - Draw a "path trail" (polyline) showing last 20 positions of each drone
  - Leader drone should have a subtle pulsing circle around it (CSS animation)

When a drone is killed:
  - Show a brief "explosion" animation (red circle that expands and fades)
  - Marker switches to red X

When a new leader is elected:
  - The new leader's marker should flash blue 3 times then stay blue
  - Draw a brief "command link" line from new leader to all followers
```

#### `DroneMarker.jsx`
```
Individual drone marker component for Leaflet.

Props: drone (DroneState object)

Renders:
  - Leaflet CircleMarker or custom DivIcon
  - Tooltip on hover showing: "Drone_{id} | {type} | Battery: {battery}%"
  - Popup on click showing full telemetry details
  
Use Leaflet DivIcon for custom HTML markers:
  - Create a small div with the drone icon (rotated SVG arrow/triangle)
  - Rotate the icon to match drone.heading degrees
  - Apply CSS class based on role for color
```

#### `TelemetryPanel.jsx`
```
Right sidebar showing per-drone stats.

For each active drone (sorted by ID):
  Render a card with:
    - Drone ID + Type badge (color-coded)
    - Role badge (LEADER in blue, FOLLOWER in green)
    - Battery bar (green > 50%, yellow 20-50%, red < 20%)
    - Altitude: {alt}m
    - Speed: {speed} m/s
    - Heading: {heading}°
    - Last heartbeat: {time} ms ago
    - Status indicator dot (green/yellow/red)

  Cards for LOST drones should be greyed out with a strikethrough on the ID.
  LEADER card should have a subtle blue border glow.

  Scrollable if more than 5 drones visible.
```

#### `ElectionLog.jsx`
```
Bottom terminal panel. THE MOST IMPORTANT VISUAL ELEMENT.

Style:
  - Black background (#0a0a0a)
  - Green monospace text (#4ade80) — like a real terminal
  - Font: JetBrains Mono, 12px
  - Left padding for the ">" prompt character
  - Auto-scroll to bottom on new messages
  - Max height: 200px, overflow-y: scroll

Each log line format:
  > [{timestamp}] [{level}] {message}

Level colors:
  [HEARTBEAT] — dim grey (these are frequent, shouldn't dominate)
  [INFO]      — green
  [WARN]      — yellow
  [CRITICAL]  — red, bold
  [SUCCESS]   — bright green, bold
  [ELECTION]  — orange

IMPORTANT: Don't show every heartbeat in the log. Only show:
  - Every 10th heartbeat as a summary: "[HEARTBEAT] 10 drones reporting nominal"
  - All warnings, criticals, and election events
  - This prevents the log from being flooded
```

#### `SwarmStatusBar.jsx`
```
Top bar with mission overview.

Display:
  - Left: "RS-GCS v1.0 | MISSION: {status}" with status color-coded
  - Center: "LEADER: Drone_{id}" with blue highlight (or "NO LEADER" in red during election)
  - Right: "ACTIVE: {n}/{total} | ELECTIONS: {count} | UPTIME: {time}"

When election is in progress:
  - Status changes to "RECOVERING" in orange
  - A subtle flashing/pulsing animation on the entire bar
```

#### `ControlPanel.jsx`
```
Command buttons panel.

Buttons:
  1. "DESTROY LEADER" — RED button, calls POST /api/command/kill-leader
     - Confirm dialog: "Terminate Leader Drone? This will trigger emergency election."
     - After click, disabled for 3 seconds (prevent double-click during election)
  
  2. "KILL RANDOM" — ORANGE button, calls POST /api/command/kill/{randomActiveId}
     - Picks a random non-leader active drone
  
  3. "START MISSION" — GREEN button, calls POST /api/command/start-mission
     - Only enabled when mission status is IDLE
  
  4. "RESET SWARM" — GREY button, calls POST /api/command/reset
     - Restarts everything

Style: Military toggle-switch aesthetic. Rounded rectangles with subtle borders.
Each button should have a small icon (use simple Unicode or inline SVG).
```

#### `DroneHealthGrid.jsx`
```
A horizontal row of 10 small squares, one per drone.

Each square:
  - 40x40px
  - Shows "D{id}" text
  - Background color:
    ACTIVE → green (#22c55e)
    STALE  → yellow (#eab308)  
    LOST   → red (#ef4444)
  - LEADER has a blue ring/border
  - On hover: tooltip with drone type and battery

This gives an instant visual "swarm health" overview.
```

#### `useSwarmSocket.js` (Custom Hook)
```
Manages the STOMP WebSocket connection.

import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

Logic:
  1. Create STOMP client with SockJS:
     brokerURL: null (use SockJS factory)
     webSocketFactory: () => new SockJS('http://localhost:8080/ws')
  
  2. On connect:
     Subscribe to:
       /topic/drones  → update drone state
       /topic/events  → append to event log
       /topic/mission → update mission state
       /topic/logs    → append to log terminal
  
  3. On disconnect: set isConnected = false, attempt reconnect after 3s
  
  4. Return: { drones, missionState, eventLog, isConnected }
```

---

## 7. DOCKER COMPOSE

```yaml
version: '3.8'

services:
  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      retries: 3

  simulator:
    build: ./simulator
    depends_on:
      backend:
        condition: service_healthy
    environment:
      - BACKEND_URL=http://backend:8080
      - NUM_DRONES=10
      - HEARTBEAT_INTERVAL_MS=100
      - CENTER_LAT=28.4595
      - CENTER_LON=77.5021

  frontend:
    build: ./frontend
    ports:
      - "5173:5173"
    depends_on:
      - backend
    environment:
      - VITE_BACKEND_URL=http://localhost:8080
```

### Backend Dockerfile:
```dockerfile
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Simulator Dockerfile:
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY . .
CMD ["python", "main.py"]
```

### Frontend Dockerfile:
```dockerfile
FROM node:20-slim
WORKDIR /app
COPY package*.json .
RUN npm install
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
```

---

## 8. THE BULLY ALGORITHM — DEEP EXPLANATION (FOR YOUR INTERVIEW)

The Bully Algorithm is the core intellectual property of this project. You MUST understand it even though an AI agent wrote the code. Here is how it works:

**Concept:** In a group of N nodes, each with a unique numeric ID, the node with the highest ID is always the leader. When the leader dies, the remaining nodes figure out who has the highest ID and make them the new leader.

**Step-by-step in our project:**

1. **Normal operation:** Drone_10 is the leader (highest ID). All other drones are followers. Drone_10's heartbeats arrive every 100ms.

2. **Failure detection:** The HeartbeatMonitor notices Drone_10 hasn't sent a heartbeat in 1000ms. It marks Drone_10 as LOST.

3. **Election trigger:** Since the LOST drone was the LEADER, HeartbeatMonitor calls `LeaderElectionService.startElection()`.

4. **Election messages:** The service looks at all ACTIVE drones. Say Drones 1-9 are alive. Drone_09 has the highest ID among survivors.

5. **The "Bully" part:** In the classic algorithm, lower-ID drones send ELECTION messages to higher-ID drones. If a higher-ID drone is alive, it responds "OK" and takes over the election. The highest responder wins. In our simplified version, we skip the back-and-forth and just pick the highest active ID — but we LOG the election messages to make the demo dramatic.

6. **Coordinator broadcast:** Drone_09 is declared the new LEADER. A COORDINATOR message is broadcast to all drones.

7. **Mission continues:** The MissionCoordinator reassigns the old leader's waypoints to other drones.

**Interview question you MUST be able to answer:**

Q: "What happens if there's a network partition (Split-Brain)?"
A: "I implemented a quorum check. A new leader is only valid if more than 50% of the total swarm is reachable. If only 3 out of 10 drones are in a partition, they enter SAFE MODE instead of electing a potentially conflicting leader."

Q: "Why Bully over Raft?"
A: "For a swarm of 10-50 drones, the Bully algorithm is simpler and faster. Raft's log replication is overkill here because we don't need consensus on a replicated state — we just need to agree on who's in charge. Bully gives us O(n) election time which is acceptable for our scale."

Q: "How would this scale to 1000 drones?"
A: "At that scale, I'd switch to a hierarchical approach — cluster drones into squads of 10, each with a local leader, and then elect a 'squad leader of leaders.' This reduces election scope from O(n) to O(√n)."

---

## 9. KEY IMPLEMENTATION NOTES FOR THE AI AGENT

### Things that MUST work perfectly (non-negotiable):
1. The map must show drones moving in real-time (smooth movement, not teleporting)
2. The KILL LEADER button must trigger a visible election in the log terminal
3. The election log must show the step-by-step Bully algorithm messages
4. The drone markers must change color when status changes (ACTIVE → LOST)
5. The new leader marker must turn blue after election
6. The swarm status bar must update leader ID after election

### Things that can be simplified for the prototype:
1. Drone movement doesn't need to be physically accurate — just move toward waypoints in straight lines
2. Battery drain can be a simple linear decrease
3. Path trails can be last 10 positions instead of 20
4. The "explosion" animation on kill can be a simple red flash, doesn't need to be fancy
5. Docker health checks can be basic (curl endpoint exists)

### Common pitfalls to avoid:
1. **WebSocket message flooding:** Don't send every heartbeat to the frontend. Batch/throttle to 5 updates per second per drone maximum.
2. **Leaflet memory leak:** When updating drone positions, don't create new markers each time. Update existing marker positions.
3. **CORS issues:** Make sure Spring Boot allows cross-origin from the Vite dev server (port 5173).
4. **Python aiohttp session:** Create ONE session and reuse it. Don't create a new session per request.
5. **React re-renders:** Use React.memo on DroneMarker components to prevent re-rendering all 10 markers when one drone updates.
6. **Map tile loading:** CartoDB dark tiles can be slow. Add a loading indicator.
7. **STOMP reconnection:** Handle WebSocket disconnects gracefully. Show a "DISCONNECTED" banner in the UI.

---

## 10. 2-DAY BUILD PLAN

### Day 1 — Backend + Simulator (get data flowing)

**Morning (4 hours):**
- [ ] Scaffold Spring Boot project with all dependencies
- [ ] Create all model classes (DroneState, TelemetryPacket, etc.)
- [ ] Implement SwarmOrchestrator with ConcurrentHashMap
- [ ] Implement TelemetryController (POST /api/telemetry)
- [ ] Implement CommandController (kill, start, reset)
- [ ] Set up WebSocket STOMP configuration
- [ ] Test with Postman: POST a fake telemetry packet, verify it's stored

**Afternoon (4 hours):**
- [ ] Implement HeartbeatMonitor (scheduled background task)
- [ ] Implement LeaderElectionService (Bully Algorithm with log messages)
- [ ] Implement MissionCoordinator (grid search waypoints)
- [ ] Write Python simulator: drone.py, main.py, swarm_patterns.py
- [ ] Test end-to-end: Run simulator → verify backend receives telemetry
- [ ] Test kill flow: Kill leader via REST → verify election happens in logs

### Day 2 — Frontend + Polish + Docker

**Morning (4 hours):**
- [ ] Scaffold Vite + React + Tailwind project
- [ ] Implement useSwarmSocket hook (STOMP connection)
- [ ] Build TacticalMap with Leaflet (dark CartoDB tiles)
- [ ] Build DroneMarker with role-based colors
- [ ] Build SwarmStatusBar
- [ ] Test: See drones moving on map in real-time

**Afternoon (4 hours):**
- [ ] Build ElectionLog terminal (the "money shot" component)
- [ ] Build ControlPanel with KILL LEADER button
- [ ] Build TelemetryPanel (per-drone cards)
- [ ] Build DroneHealthGrid
- [ ] Wire up KILL LEADER → election → log → map update (full flow)
- [ ] Docker Compose setup
- [ ] Record the 60-second demo video
- [ ] Write the README.md

---

## 11. API CONTRACT SUMMARY

| Method | Endpoint | Request Body | Response | Purpose |
|--------|----------|-------------|----------|---------|
| POST | /api/telemetry | TelemetryPacket JSON | 200 OK | Python drones send data |
| POST | /api/telemetry/batch | List<TelemetryPacket> | 200 OK | Batch telemetry |
| POST | /api/command/kill/{id} | — | 200 + message | Kill specific drone |
| POST | /api/command/kill-leader | — | 200 + message | Kill current leader |
| POST | /api/command/start-mission | — | 200 + message | Start the swarm mission |
| POST | /api/command/reset | — | 200 + message | Reset everything |
| GET | /api/swarm/snapshot | — | Swarm JSON | Initial frontend load |
| WS | /ws (STOMP) | — | — | Real-time frontend updates |

### STOMP Topics:
| Topic | Payload | Frequency |
|-------|---------|-----------|
| /topic/drones | DroneState JSON | ~5/sec per drone (throttled) |
| /topic/events | ElectionEvent JSON | On election events only |
| /topic/mission | MissionState JSON | On status changes |
| /topic/logs | { level, message, timestamp } | Varies |

---

## 12. README.md TEMPLATE (DEFENSE-GRADE)

The README should follow this EXACT structure to speak "defense language":

```markdown
# RS-GCS: Resilient Swarm Ground Control Station

> A self-healing distributed orchestrator for heterogeneous drone swarms.
> Demonstrates autonomous leader re-election under catastrophic node failure.

## Problem Statement
In swarm UAV operations, centralized command architectures create a single 
point of failure. When the command node is neutralized (jamming, kinetic kill, 
cyber intrusion), the entire swarm becomes inoperative. RS-GCS eliminates this 
vulnerability through distributed consensus.

## Architecture
[Include a simple diagram — even ASCII art]

## Key Capabilities
- Heterogeneous swarm management (Surveillance, Logistics, Strike)
- Sub-500ms autonomous leader re-election (Bully Algorithm)
- Quorum-based split-brain prevention
- MAVLink-compatible telemetry protocol
- Real-time War-Room visualization

## Tech Stack
- Java 21 (Virtual Threads/Project Loom) — Swarm Orchestration
- Python 3.11 (asyncio) — Digital Twin Simulation  
- React + Leaflet — Tactical Dashboard
- STOMP/WebSocket — Real-time Communication

## Quick Start
docker-compose up
Open http://localhost:5173

## The Demo (60 seconds)
1. 10 drones execute grid search pattern
2. Click DESTROY LEADER
3. Watch election cascade in log terminal
4. New leader elected in <500ms
5. Swarm continues mission autonomously

## Design Decisions
### Why Java for Swarm Orchestration?
[Explain Project Loom advantage for high-concurrency telemetry]

### Why Bully Algorithm over Raft?
[Explain simplicity vs. complexity tradeoff for swarm size]

### Why UDP-style Telemetry?
[Explain real-world MAVLink precedent]

## Known Limitations
- Simulation only — not tested with physical hardware
- Bully Algorithm doesn't handle equal-priority conflicts
- No encryption on telemetry channel (would use mTLS in production)
- UI optimized for 10 drones; 100+ would need virtualized rendering

## What I'd Build Next
- Raft consensus for production-grade elections
- AES-256 encrypted telemetry payloads
- GNSS-denied navigation fallback (Visual Odometry)
- Hardware-in-the-loop testing with ESP32 mesh
- STANAG 4586 compliance layer
```

---

## 13. FINAL CHECKLIST BEFORE SHARING WITH COMPANIES

- [ ] `docker-compose up` works in one command
- [ ] Map shows 10 drones moving smoothly
- [ ] KILL LEADER triggers visible election in <1 second
- [ ] Log terminal shows step-by-step election messages
- [ ] New leader drone turns blue on map
- [ ] Swarm continues flying after leader change
- [ ] Battery levels visibly decrease over time
- [ ] Drone types are visually distinct (different shapes/colors)
- [ ] README has architecture section and "Known Limitations"
- [ ] 60-second screen recording captured
- [ ] GitHub repo is clean (no node_modules, no .class files, proper .gitignore)

---

## 14. ENVIRONMENT VARIABLES REFERENCE

### Backend (application.yml or env vars):
```
SERVER_PORT=8080
RSGCS_HEARTBEAT_TIMEOUT_MS=500
RSGCS_HEARTBEAT_CHECK_INTERVAL_MS=200
RSGCS_MAX_DRONES=10
RSGCS_ELECTION_TIMEOUT_MS=300
```

### Simulator:
```
BACKEND_URL=http://localhost:8080
NUM_DRONES=10
HEARTBEAT_INTERVAL_MS=100
CENTER_LAT=28.4595
CENTER_LON=77.5021
```

### Frontend:
```
VITE_BACKEND_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080/ws
```
