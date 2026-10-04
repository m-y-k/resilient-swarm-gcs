<p align="center">
  <h1 align="center">🛡️ RS-GCS</h1>
  <p align="center"><strong>Resilient Swarm Ground Control Station</strong></p>
  <p align="center">
    Real-time drone swarm command & control dashboard with autonomous leader election,<br>
    mission waypoint planning, obstacle avoidance, and live pathfinding
  </p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.2-green?logo=spring" />
  <img src="https://img.shields.io/badge/React-19-blue?logo=react" />
  <img src="https://img.shields.io/badge/Python-3.11-yellow?logo=python" />
  <img src="https://img.shields.io/badge/Docker-Ready-blue?logo=docker" />
</p>

---

## 🎯 Problem Statement

In autonomous drone swarms, **leader failure is inevitable**. A single point of failure can cascade into mission collapse. RS-GCS solves this by implementing a **self-healing swarm architecture** where:

- Drones autonomously detect leader failure via heartbeat monitoring
- The **Bully Election Algorithm** elects a new leader in under 500ms
- A **Raft-inspired quorum check** prevents split-brain scenarios
- The ground operator sees everything in real-time on a tactical dashboard

---

## 🎬 Demo

[Watch the failover demo →](https://www.loom.com/share/3960f208f84f4b8ca3551b7802f58090)

*Live leader election triggered by killing the current leader — watch the Election Log Terminal and tactical map update in real time.*

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         RS-GCS ARCHITECTURE                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐    HTTP/REST     ┌──────────────────────────┐    │
│  │   SIMULATOR   │ ──────────────→ │      SPRING BOOT         │    │
│  │  (Python 3.11)│   Telemetry     │     ORCHESTRATOR         │    │
│  │               │   POST /api/    │       (Java 21)          │    │
│  │  10 Drones    │   telemetry     │                          │    │
│  │  × 10 Hz      │                 │  ┌──────────────────┐   │    │
│  │               │ ←────────────── │  │ SwarmOrchestrator │   │    │
│  │  grid_search  │   Kill Commands │  │ HeartbeatMonitor  │   │    │
│  │  waypoints    │   GET /api/     │  │ LeaderElection    │   │    │
│  └──────────────┘   simulator/     │  │ MissionCoordinator│   │    │
│                      commands      │  └──────────────────┘   │    │
│                                    │          │               │    │
│                                    │    STOMP/WebSocket       │    │
│                                    │    /ws (SockJS)          │    │
│                                    └──────────┬───────────────┘    │
│                                               │                    │
│                                    ┌──────────▼───────────────┐    │
│                                    │     REACT DASHBOARD      │    │
│                                    │      (Vite + Leaflet)    │    │
│                                    │                          │    │
│                                    │  ┌────┐ ┌────┐ ┌─────┐ │    │
│                                    │  │Map │ │Tele│ │Elect│ │    │
│                                    │  │    │ │metr│ │ Log │ │    │
│                                    │  └────┘ └────┘ └─────┘ │    │
│                                    └──────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘

  Topics:  /topic/drones  /topic/mission  /topic/logs  /topic/events
```

*Hub-and-spoke topology — the orchestrator broadcasts one-way over STOMP, and no drone depends on a two-way handshake to receive telemetry or commands.*

---

## 🧠 Design Decisions & Reasoning

### Why Bully Algorithm over Raft or a static leader

Three approaches were on the table:

**Static leader** — simplest option, but a single point of failure. If the designated leader drone goes down, the entire mission loses coordination with no recovery path.

**Raft** — strong consistency guarantees, but impractical to scale cleanly. Beyond a certain swarm size (1000+ nodes), a single-round election doesn't hold up — you'd need multi-level/hierarchical elections, which adds latency exactly where it can least be afforded.

**Bully Algorithm** — the right fit here: no two-way coordination overhead, elections resolve fast, and the highest-priority surviving node simply takes over. The leader broadcasts one-way — no request/acknowledge round trip — which matters when every millisecond of delay affects real-time flight coordination.

**Trade-off accepted:** Bully assumes relatively reliable message delivery. For this swarm's scale and communication range, that assumption holds; a quorum check (Raft-inspired) was layered on top specifically to catch the split-brain case Bully alone doesn't handle.

### Failover performance (<500ms)

Measured by crashing a drone process and timing re-election — software-level testing, not hardware flight tests. The result wasn't a specific optimization target; it fell out naturally from the one-way broadcast design — no acknowledgment round trips means the failure-detection-to-new-leader path stays short by construction.

### Why Java 21 Virtual Threads

Before Virtual Threads, each drone's constant 10Hz telemetry stream meant a growing number of platform threads — expensive enough to visibly slow the orchestrator, and painful to manage manually as swarm size grew. Virtual Threads removed that ceiling: non-blocking heartbeat monitoring and high-concurrency telemetry handling without the overhead of traditional thread pool management.

### Path planning — per-drone, not centralized

Each drone runs its own tangent-point obstacle avoidance rather than a single centrally-computed path. This is necessary because drones maintain relative/parallel distance from each other for formation flying — paths can't be computed once centrally; they need to account for each drone's relative positioning in real time. Static obstacles trigger real-time recalculation, balancing shortest-path efficiency against the cost of recomputing mid-flight.

| Decision | Rationale |
|----------|-----------|
| **Bully Algorithm** over Raft | Simpler to implement + visualize for a demo; highest-ID always wins |
| **Virtual Threads** (Java 21) | Non-blocking heartbeat monitoring without thread pool management |
| **ConcurrentHashMap** for state | Lock-free reads from WebSocket broadcast threads |
| **STOMP over raw WS** | Topic-based pub/sub with SockJS fallback for browser compatibility |
| **10 Hz telemetry** | Smooth map movement without overwhelming the backend |
| **Quorum check** | Prevents split-brain — election aborts if <50% drones reachable |
| **Tangent-point avoidance** | Computes left/right tangent points on obstacle circles, picks shorter path |
| **Formation offsets** | Leader flies exact waypoints; followers fly perpendicular offsets by index |

---

## ✨ Key Capabilities

| Feature | Description |
|---------|-------------|
| **Autonomous Leader Election** | Bully Algorithm with quorum validation — new leader in <500ms |
| **Real-time Tactical Map** | Leaflet map with drone markers, role-based colors, path trails |
| **Mission Waypoint Planning** | Click-to-place waypoints, formation offsets (±30m/60m/90m), deploy to swarm |
| **Obstacle Avoidance** | 8 pre-loaded Greater Noida landmarks, CRUD API, type-colored no-fly zones |
| **Live Pathfinding** | Tangent-point algorithm routes drones around obstacles in real-time |
| **Heartbeat Monitoring** | 200ms sweep cycle, STALE/LOST detection with configurable timeouts |
| **Chaos Engineering** | Kill Leader, Kill Random, Reset buttons for live fault injection |
| **Election Log Terminal** | Green monospace terminal showing election cascade in real-time |
| **Swarm Health Grid** | 10-drone visual overview with color-coded status indicators |
| **Split-Brain Prevention** | Raft-inspired quorum check — election aborts if <50% drones reachable |
| **Docker Deployment** | One-command `docker-compose up` for all 3 services |

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 21 + Spring Boot 3.2 + Virtual Threads |
| **Real-time** | STOMP over WebSocket (SockJS fallback) |
| **Simulator** | Python 3.11 + aiohttp (async HTTP) |
| **Frontend** | React 19 + Vite + Leaflet + @stomp/stompjs |
| **Styling** | Hand-crafted dark military CSS (JetBrains Mono) |
| **Container** | Docker + Docker Compose |

---

## 🚀 Quick Start

### Option 1: Docker (Recommended)

```bash
docker-compose up --build
```

Then open [http://localhost:5173](http://localhost:5173) in your browser.

### Option 2: Local Development

**Prerequisites:** Java 21, Node 20+, Python 3.11+

```bash
# Terminal 1 — Backend
cd backend
mvn spring-boot:run

# Terminal 2 — Simulator
cd simulator
pip install -r requirements.txt
python main.py

# Terminal 3 — Frontend
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173)

---

## 🎬 Demo Walkthrough: The "Money Shot"

The signature demo flow that showcases the entire system:

1. **Start** → Click `▶ START MISSION` — Leader assigned (highest-ID drone)
2. **Observe** → Watch 10 drones fly grid patterns on the tactical map
3. **Plan** → Click `📍 PLAN MISSION` → place waypoints through Greater Noida obstacle zones
4. **Deploy** → Click `🚀 DEPLOY` — Leader flies exact path, followers fly ±30m/60m/90m offsets
5. **Avoid** → Drones visibly route around Gaur City Mall and other obstacles (dotted path bends)
6. **Kill** → Click `⚡ DESTROY LEADER` — Leader drone goes red
7. **Election** → Watch the Election Log Terminal cascade:
   ```
   [CRITICAL] LEADER_TIMEOUT: Drone_10 — initiating election protocol
   [SYSTEM]   Mission status → RECOVERING
   [ELECTION] ELECTION_START: Initiated by Drone_8
   [ELECTION] ELECTION_MSG: Drone_8 → Drone_9
   [ELECTION] ELECTION_RESPONSE: Drone_9 asserts leadership
   [SUCCESS]  NEW_LEADER: Drone_9 elected — resuming mission
   ```
8. **Recovery** → Map updates: new leader turns blue, pathfinding persists through leadership change
9. **Repeat** → Kill again to see cascading elections

---

## 📂 Project Structure

```
RS-GCS/
├── backend/                    # Spring Boot Orchestrator
│   ├── src/main/java/com/rsgcs/
│   │   ├── controller/         # REST + WebSocket endpoints
│   │   ├── model/              # DroneState, MissionState, Waypoint, Obstacle
│   │   ├── service/            # SwarmOrchestrator, HeartbeatMonitor, LeaderElection, PathfindingService
│   │   └── config/             # CORS, WebSocket config
│   ├── Dockerfile
│   └── pom.xml
├── simulator/                  # Python Drone Simulator
│   ├── main.py                 # Entry point — spawns N drone coroutines
│   ├── drone.py                # SimulatedDrone with movement + obstacle avoidance
│   ├── pathfinding.py          # Tangent-point obstacle avoidance algorithm
│   ├── swarm_patterns.py       # Grid search waypoint generation
│   ├── Dockerfile
│   └── requirements.txt
├── frontend/                   # React Dashboard
│   ├── src/
│   │   ├── components/         # TacticalMap, ObstacleLayer, WaypointPlanner, PlannedPathLine, etc.
│   │   ├── data/               # greaterNoidaObstacles.js (8 real landmarks)
│   │   ├── hooks/              # useSwarmSocket (WebSocket + REST)
│   │   ├── utils/              # droneIcons, role colors
│   │   └── App.jsx             # Main layout orchestrator
│   ├── Dockerfile
│   └── package.json
├── docker-compose.yml
└── .gitignore
```

---

## ⚠️ Known Limitations

- **Simulator-only** — No real MAVLink drone integration yet
- **In-memory state** — No persistence; state resets on backend restart
- **Single backend** — Not horizontally scalable (single orchestrator)
- **Dev server** — Frontend runs Vite dev server (not production build)
- **Battery drain** — Simulated drones deplete battery over ~15 minutes

---

## 🗺️ Future Roadmap

- [ ] Production Nginx build for frontend
- [ ] Persistent state with Redis or PostgreSQL
- [ ] MAVLink protocol integration for real drones
- [ ] Advanced pathfinding (A* / RRT*) for complex environments
- [ ] Multi-swarm support with inter-swarm communication
- [ ] Prometheus + Grafana monitoring
- [ ] Kubernetes Helm chart deployment

---

## 💡 What I Learned

Consensus is straightforward on paper — the hard part is latency in practice. Even with a fast algorithm, real coordination exposes edge cases that diagrams don't show. Virtual Threads, on the other hand, made concurrent telemetry handling far easier than expected going in.

This project also raised a broader question worth exploring next: how do autonomous systems actually communicate under real-world constraints — jammed networks, latency spikes, GPS-based geo-location vs. pre-fed coordinates and pre-planned paths. Different vehicle types (drones, humanoids, ground vehicles) operate at different speeds and altitudes, which raises an interesting architectural question — could a single ground control system eventually coordinate heterogeneous autonomous units performing a shared mission without human intervention? That's the direction this project points toward next.

---

## 📡 API Reference

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/telemetry` | POST | Submit drone telemetry (single or batch) |
| `/api/swarm/snapshot` | GET | Full swarm state snapshot |
| `/api/command/kill/{id}` | POST | Kill a specific drone |
| `/api/command/kill-leader` | POST | Kill current leader (triggers election) |
| `/api/command/kill-random` | POST | Kill a random non-leader drone |
| `/api/command/start-mission` | POST | Start swarm mission |
| `/api/command/reset` | POST | Reset all swarm state |
| `/api/mission/waypoints` | POST | Deploy operator-planned waypoints to swarm |
| `/api/obstacles` | GET | List all obstacles |
| `/api/obstacles` | POST | Add new obstacle |
| `/api/obstacles/{id}` | DELETE | Remove obstacle |
| `/api/simulator/commands` | GET | Simulator polls for kill commands |
| `/api/simulator/waypoints` | GET | Simulator polls for drone waypoints |
| `/api/simulator/obstacles` | GET | Simulator polls for obstacle list |

---

## 📜 License

MIT

---

<p align="center">
  Built with ☕ Java 21 + 🐍 Python + ⚛️ React + 🐳 Docker
</p>
