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
 * │                      │                         │
 * │                      ├─────────────────────────┤
 * │                      │    COMMAND PANEL         │
 * ├──────────────────────┴─────────────────────────┤
 * │         ELECTION LOG TERMINAL                  │
 * └────────────────────────────────────────────────┘
 */
function App() {
  const {
    drones, missionState, eventLog, logs, trails,
    isConnected,
    killLeader, killRandom, startMission, resetSwarm,
  } = useSwarmSocket();

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
        <TacticalMap drones={drones} trails={trails} />

        {/* Right Column: Telemetry + Control */}
        <div style={{
          display: 'grid',
          gridTemplateRows: '1fr auto',
          overflow: 'hidden',
        }}>
          <TelemetryPanel drones={drones} />
          <ControlPanel
            killLeader={killLeader}
            killRandom={killRandom}
            startMission={startMission}
            resetSwarm={resetSwarm}
            missionState={missionState}
          />
        </div>
      </div>

      {/* Row 4: Election Log Terminal */}
      <div style={{ height: '180px', overflow: 'hidden' }}>
        <ElectionLog logs={logs} />
      </div>
    </div>
  );
}

export default App;
