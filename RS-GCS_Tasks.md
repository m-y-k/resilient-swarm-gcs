# RS-GCS: Master Task List — 2-Day Sprint

> **Goal:** Build a complete, impressive drone swarm GCS prototype in 2 days.  
> **Status Key:** `[ ]` Todo · `[/]` In Progress · `[x]` Done

---

## DAY 1 — Backend + Simulator (Data Pipeline)

### Phase 1: Project Scaffolding (Est. 30 min)
- [x] Create root project structure (`backend/`, `simulator/`, `frontend/`)
- [x] Initialize Spring Boot project with `pom.xml` (Java 21, Spring Web, Spring WebSocket, Lombok)
- [x] Create `application.yml` with virtual threads, server port, and RSGCS config properties
- [x] Set up `.gitignore` (Java, Python, Node artifacts)
- [x] Initialize Git repo

---

### Phase 2: Backend Models (Est. 30 min)
- [x] `DroneRole.java` — Enum: `LEADER`, `FOLLOWER`, `CANDIDATE`, `LOST`
- [x] `DroneType.java` — Enum: `SURVEILLANCE`, `LOGISTICS`, `STRIKE`
- [x] `DroneState.java` — Core drone data model with all fields (id, position, battery, role, heartbeat, etc.)
- [x] `TelemetryPacket.java` — Incoming telemetry from Python drones
- [x] `ElectionEvent.java` — Election log entry
- [x] `MissionState.java` — Swarm mission status

---

### Phase 3: Backend Core Services (Est. 2 hours)
- [x] `SwarmOrchestrator.java` — Central brain: ConcurrentHashMap registry, `handleTelemetry()`, `killDrone()`, `getSwarmSnapshot()`
- [x] `HeartbeatMonitor.java` — `@Scheduled` task every 200ms, 3-strike rule (ACTIVE → STALE @500ms → LOST @1000ms), triggers election on leader loss
- [x] `LeaderElectionService.java` — **THE MONEY CLASS**: Bully Algorithm with dramatic logged steps, quorum check, sub-500ms election
- [x] `MissionCoordinator.java` — Grid search pattern generator centered on Greater Noida (28.4595, 77.5021)

---

### Phase 4: Backend Controllers & WebSocket (Est. 1 hour)
- [x] `WebSocketConfig.java` — STOMP over SockJS config, topics: `/topic/drones`, `/topic/events`, `/topic/mission`, `/topic/logs`
- [x] `TelemetryController.java` — `POST /api/telemetry` and `/api/telemetry/batch`
- [x] `CommandController.java` — `POST /api/command/kill/{id}`, `kill-leader`, `start-mission`, `reset` + `GET /api/swarm/snapshot`
- [x] `DashboardWebSocket.java` — STOMP message broadcasting logic
- [x] CORS configuration — Allow all origins for dev

---

### Phase 5: Backend Smoke Test (Est. 30 min)
- [x] Build and run Spring Boot app
- [x] Test `POST /api/telemetry` with a manual JSON payload (Postman/curl)
- [x] Test `GET /api/swarm/snapshot` returns drone data
- [x] Verify STOMP WebSocket endpoint `/ws` is accessible

---

### Phase 6: Python Simulator (Est. 2 hours)
- [x] `requirements.txt` — `aiohttp`
- [x] `swarm_patterns.py` — `grid_search()` function: divide 500m×500m area into drone strips with waypoints
- [x] `drone.py` — `SimulatedDrone` class: movement toward waypoints, battery drain, telemetry POST to backend, `kill()` method
- [x] `main.py` — Entry point: spawn N drone coroutines, command listener, graceful shutdown
- [ ] *(Optional)* `mavlink_emulator.py` — MAVLink-style packet wrapper (polish feature, skip if short on time)

---

### Phase 7: End-to-End Data Flow Test (Est. 30 min)
- [x] Run backend + simulator together
- [x] Verify backend receives telemetry from all 10 drones
- [x] Verify drones appear in `/api/swarm/snapshot`
- [x] Test kill flow: `POST /api/command/kill-leader` → verify election in backend logs
- [x] Verify election completes in <500ms

---

## DAY 2 — Frontend + Docker + Polish

### Phase 8: Frontend Scaffolding (Est. 30 min)
- [ ] Initialize Vite + React project
- [ ] Install dependencies: `react-leaflet`, `leaflet`, `@stomp/stompjs`, `sockjs-client`
- [ ] Set up Tailwind CSS with military dark theme
- [ ] Import JetBrains Mono font from Google Fonts
- [ ] Define CSS custom properties (color palette from spec)
- [ ] Set up `index.css` with global dark theme

---

### Phase 9: WebSocket Hook & Core State (Est. 30 min)
- [ ] `useSwarmSocket.js` — Custom hook: STOMP client via SockJS, subscribe to all 4 topics, reconnection logic
- [ ] `App.jsx` — Main layout with CSS Grid, state management for drones/mission/events/logs, initial snapshot fetch

---

### Phase 10: Tactical Map (Est. 1.5 hours)
- [ ] `TacticalMap.jsx` — Leaflet map with CartoDB dark tiles, centered on Greater Noida
- [ ] `DroneMarker.jsx` — Custom DivIcon markers: role-based colors (blue=LEADER, green=FOLLOWER, orange=CANDIDATE, red=LOST), heading rotation, hover tooltips
- [ ] `droneIcons.js` — SVG icons for drone types + role colors
- [ ] Path trails — Polyline showing last 10 positions per drone
- [ ] Leader pulsing animation — CSS pulsing circle on leader marker
- [ ] Kill animation — Red flash when drone dies

---

### Phase 11: Dashboard Panels (Est. 2 hours)
- [ ] `SwarmStatusBar.jsx` — Top bar: mission status, leader ID, active count, election count, uptime
- [ ] `ElectionLog.jsx` — **THE SHOWPIECE**: terminal-style panel, green monospace text, auto-scroll, color-coded log levels, throttled heartbeat display
- [ ] `TelemetryPanel.jsx` — Right sidebar: per-drone cards with battery bars, altitude, speed, heading, status dots, scrollable
- [ ] `ControlPanel.jsx` — Military-style buttons: DESTROY LEADER (red), KILL RANDOM (orange), START MISSION (green), RESET (grey), with confirmation dialogs and cooldowns
- [ ] `DroneHealthGrid.jsx` — Horizontal row of 10 colored squares (green/yellow/red per drone status), leader has blue ring

---

### Phase 12: Full Integration Test (Est. 1 hour)
- [ ] Wire up all components: button clicks → REST calls → backend processing → STOMP broadcast → UI updates
- [ ] Test the "Money Shot" flow: 10 drones flying → KILL LEADER → election log cascade → new leader elected → swarm reorganizes
- [ ] Verify map markers change color correctly during election
- [ ] Verify status bar updates leader ID
- [ ] Verify telemetry panel shows updated roles
- [ ] Verify drone health grid reflects drone states
- [ ] Performance check: smooth movement, no lag or flicker

---

### Phase 13: Docker & Deployment (Est. 1 hour)
- [ ] `backend/Dockerfile` — Multi-stage build: Maven build → JRE runtime
- [ ] `simulator/Dockerfile` — Python 3.11 slim with aiohttp
- [ ] `frontend/Dockerfile` — Node 20 with Vite dev server
- [ ] `docker-compose.yml` — All 3 services, health checks, env vars, proper `depends_on`
- [ ] Test `docker-compose up` — everything starts and works together

---

### Phase 14: README & Final Polish (Est. 1 hour)
- [ ] Write defense-grade `README.md` (problem statement, architecture, key capabilities, tech stack, quick start, demo instructions, design decisions, known limitations, future roadmap)
- [ ] Add ASCII architecture diagram to README
- [ ] Final `.gitignore` cleanup (no `node_modules`, `.class`, `__pycache__`, `target/`)
- [ ] Clean up all code: remove debug logs, add key comments
- [ ] Record 60-second "Money Shot" demo video

---

## Summary

| Day | Phases | Focus |
|-----|--------|-------|
| **Day 1** | 1–7 | Backend services + Python simulator + data flow validation |
| **Day 2** | 8–14 | React dashboard + Docker + README + demo recording |

**Total Phases:** 14  
**Critical Path:** Phase 3 (Election Service) → Phase 6 (Simulator) → Phase 10 (Map) → Phase 11 (Election Log)  
**The "Money Shot" depends on:** Election log + Kill button + Map markers + Status bar all working together
