import { useState, useCallback, useEffect, useRef } from 'react';
import useSwarmSocket from './hooks/useSwarmSocket';
import SwarmStatusBar from './components/SwarmStatusBar';
import TacticalMap from './components/TacticalMap';
import ElectionLog from './components/ElectionLog';
import TelemetryPanel from './components/TelemetryPanel';
import ControlPanel from './components/ControlPanel';
import DroneHealthGrid from './components/DroneHealthGrid';

/**
 * RS-GCS Dashboard Layout
 * 
 * ┌────────────────────────────────────────────────┐
 * │              SWARM STATUS BAR                  │
 * ├────────────────────────────────────────────────┤
 * │ [ D1 D2 D3 D4 D5 D6 D7 D8 D9 D10 ] HEALTH   │
 * ├──────────────────────┬─────────────────────────┤
 * │                      │     TELEMETRY PANEL     │
 * │   TACTICAL MAP       │     (scrollable cards)  │
 * │   + Obstacles        │                         │
 * │   + Waypoints        ├─────────────────────────┤
 * │   + Planned Paths    │    COMMAND PANEL         │
 * ├──────────────────────┴─────────────────────────┤
 * │         ELECTION LOG TERMINAL                  │
 * └────────────────────────────────────────────────┘
 */
function App() {
  const {
    drones, missionState, eventLog, logs, trails, obstacles,
    isConnected,
    killLeader, killRandom, startMission, resetSwarm,
    deployMission, addObstacle, removeObstacle, setSpawnPoint,
  } = useSwarmSocket();

  // Mission planning state
  const [planningMode, setPlanningMode] = useState(false);
  const [waypoints, setWaypoints] = useState([]);
  const [obstacleMode, setObstacleMode] = useState(false);
  const [showObstacles, setShowObstacles] = useState(true);
  const [spawnMode, setSpawnMode] = useState(false);
  const [spawnMarker, setSpawnMarker] = useState(null);
  const [showKillFlash, setShowKillFlash] = useState(false);
  const killFlashTimer = useRef(null);

  // Handle map clicks for waypoint/obstacle/spawn placement
  // Wrapped killLeader with red flash overlay
  const handleKillLeader = useCallback(() => {
    killLeader();
    setShowKillFlash(true);
    if (killFlashTimer.current) clearTimeout(killFlashTimer.current);
    killFlashTimer.current = setTimeout(() => setShowKillFlash(false), 600);
  }, [killLeader]);

  const handleMapClick = useCallback((latlng) => {
    // Handle waypoint right-click delete signal from WaypointPlanner
    if (latlng && latlng.__removeWaypoint) {
      setWaypoints(prev => prev.filter(wp => wp.id !== latlng.__removeWaypoint));
      return;
    }

    if (spawnMode) {
      setSpawnMarker({ lat: latlng.lat, lng: latlng.lng });
      setSpawnPoint(latlng.lat, latlng.lng);
      setSpawnMode(false);
    } else if (planningMode) {
      const newWaypoint = {
        id: `wp_${Date.now()}`,
        lat: latlng.lat,
        lng: latlng.lng,
        altitude: 100,
        order: waypoints.length + 1,
      };
      setWaypoints(prev => [...prev, newWaypoint]);
    } else if (obstacleMode) {
      const name = prompt('Obstacle name:', 'Custom Obstacle');
      if (!name) return;
      const newObstacle = {
        id: `custom_${Date.now()}`,
        name: name,
        type: 'BUILDING',
        latitude: latlng.lat,
        longitude: latlng.lng,
        radius: 50,
        height: 30,
      };
      addObstacle(newObstacle);
      setObstacleMode(false);
    }
  }, [planningMode, obstacleMode, spawnMode, waypoints, addObstacle, setSpawnPoint]);

  // Deploy planned waypoints to backend
  const handleDeployMission = useCallback(async () => {
    if (waypoints.length < 2) {
      console.warn('[DEPLOY] Need at least 2 waypoints');
      return;
    }
    const waypointPayload = waypoints.map((wp, idx) => ({
      id: wp.id,
      latitude: wp.lat,
      longitude: wp.lng,
      altitude: wp.altitude,
      order: idx + 1,
    }));
    console.log('[DEPLOY] Sending', waypointPayload.length, 'waypoints to backend');
    try {
      await deployMission(waypointPayload);
      console.log('[DEPLOY] Waypoints deployed successfully — drones will update in ~2s');
    } catch (err) {
      console.error('[DEPLOY] Failed:', err);
    }
    setPlanningMode(false);
    // Keep waypoints visible but exit planning mode
  }, [waypoints, deployMission]);

  return (
    <div style={{
      display: 'grid',
      gridTemplateRows: 'auto auto 1fr auto',
      height: '100vh',
      width: '100vw',
      overflow: 'hidden',
      background: 'var(--bg-primary)',
    }}>
      {/* Row 1: Status Bar */}
      <SwarmStatusBar
        missionState={missionState}
        drones={drones}
        isConnected={isConnected}
      />

      {/* Row 2: Drone Health Grid */}
      <DroneHealthGrid drones={drones} />

      {/* Row 3: Main Area — Map + Right Panel */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr 260px',
        overflow: 'hidden',
      }}>
        {/* Tactical Map */}
        <TacticalMap
          drones={drones}
          trails={trails}
          obstacles={obstacles}
          waypoints={waypoints}
          planningMode={planningMode}
          obstacleMode={obstacleMode}
          spawnMode={spawnMode}
          spawnMarker={spawnMarker}
          showObstacles={showObstacles}
          onMapClick={handleMapClick}
        />

        {/* Right Column: Telemetry + Control */}
        <div style={{
          display: 'grid',
          gridTemplateRows: '1fr auto',
          overflow: 'hidden',
        }}>
          <TelemetryPanel drones={drones} />
          <ControlPanel
            killLeader={handleKillLeader}
            killRandom={killRandom}
            startMission={startMission}
            resetSwarm={resetSwarm}
            missionState={missionState}
            planningMode={planningMode}
            setPlanningMode={setPlanningMode}
            obstacleMode={obstacleMode}
            setObstacleMode={setObstacleMode}
            showObstacles={showObstacles}
            setShowObstacles={setShowObstacles}
            deployMission={handleDeployMission}
            waypointCount={waypoints.length}
            spawnMode={spawnMode}
            setSpawnMode={setSpawnMode}
            spawnMarker={spawnMarker}
          />
        </div>
      </div>

      {/* Row 4: Election Log Terminal */}
      <div style={{ height: '180px', overflow: 'hidden' }}>
        <ElectionLog logs={logs} />
      </div>

      {/* Leader kill red flash overlay */}
      {showKillFlash && <div className="leader-kill-flash" />}
    </div>
  );
}

export default App;
