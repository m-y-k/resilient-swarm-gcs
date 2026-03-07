import React, { useMemo } from 'react';
import { getRoleColor, getDroneColor, TYPE_LABELS } from '../utils/droneIcons';

/**
 * Right sidebar — per-drone telemetry cards with battery bars, altitude, speed.
 */
export default function TelemetryPanel({ drones }) {
    const droneList = useMemo(
        () => Object.values(drones || {}).sort((a, b) => a.droneId - b.droneId),
        [drones]
    );

    return (
        <div style={{
            height: '100%',
            overflowY: 'auto',
            background: 'var(--bg-panel)',
            borderLeft: '1px solid var(--border)',
            display: 'flex',
            flexDirection: 'column',
        }}>
            {/* Header */}
            <div style={{
                padding: '6px 10px',
                background: 'var(--bg-secondary)',
                borderBottom: '1px solid var(--border)',
                fontSize: '10px',
                color: 'var(--text-secondary)',
            }}>
                ▸ DRONE TELEMETRY
            </div>

            {/* Drone cards */}
            <div style={{ flex: 1, overflowY: 'auto', padding: '4px' }}>
                {droneList.map(drone => (
                    <DroneCard key={drone.droneId} drone={drone} />
                ))}
                {droneList.length === 0 && (
                    <div style={{ color: 'var(--text-secondary)', fontSize: '11px', padding: '12px', textAlign: 'center' }}>
                        No drones connected
                    </div>
                )}
            </div>
        </div>
    );
}

const DroneCard = React.memo(function DroneCard({ drone }) {
    const roleColor = getDroneColor(drone);
    const isLost = drone.status === 'LOST' || drone.role === 'LOST';
    const isLeader = drone.role === 'LEADER';
    const batteryColor = drone.batteryPercent > 50 ? 'var(--accent-green)'
        : drone.batteryPercent > 20 ? 'var(--accent-yellow)' : 'var(--accent-red)';

    return (
        <div style={{
            margin: '3px 0',
            padding: '6px 8px',
            background: isLost ? 'rgba(239, 68, 68, 0.05)' : 'var(--bg-secondary)',
            border: `1px solid ${isLeader ? 'var(--accent-blue)' : 'var(--border)'}`,
            borderRadius: 4,
            opacity: isLost ? 0.5 : 1,
            boxShadow: isLeader ? '0 0 8px rgba(59, 130, 246, 0.2)' : 'none',
            fontSize: '10px',
        }}>
            {/* Header row */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                <span style={{
                    fontWeight: 700,
                    color: isLost ? 'var(--accent-red)' : 'var(--text-primary)',
                    textDecoration: isLost ? 'line-through' : 'none',
                }}>
                    {TYPE_LABELS[drone.droneType] || ''} D{drone.droneId}
                </span>
                <span style={{
                    fontSize: '9px',
                    padding: '1px 6px',
                    borderRadius: 3,
                    background: `${roleColor}22`,
                    color: roleColor,
                    fontWeight: 600,
                }}>
                    {drone.role}
                </span>
            </div>

            {/* Battery bar */}
            <div style={{
                height: 4, background: '#1e293b', borderRadius: 2, marginBottom: 4,
                overflow: 'hidden',
            }}>
                <div style={{
                    width: `${Math.max(0, drone.batteryPercent || 0)}%`,
                    height: '100%',
                    background: batteryColor,
                    borderRadius: 2,
                    transition: 'width 0.5s ease',
                }} />
            </div>

            {/* Stats */}
            <div style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: '2px 8px',
                color: 'var(--text-secondary)',
                fontSize: '9px',
            }}>
                <span>BAT: <span style={{ color: batteryColor }}>{(drone.batteryPercent || 0).toFixed(1)}%</span></span>
                <span>ALT: {(drone.altitude || 0).toFixed(0)}m</span>
                <span>SPD: {(drone.speed || 0).toFixed(1)} m/s</span>
                <span>HDG: {(drone.heading || 0).toFixed(0)}°</span>
            </div>
        </div>
    );
});
