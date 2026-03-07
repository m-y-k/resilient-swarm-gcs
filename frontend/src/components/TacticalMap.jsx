import React, { useMemo, useCallback, useState, useEffect } from 'react';
import { MapContainer, TileLayer, CircleMarker, Marker, Polyline, Tooltip, useMapEvents, useMap } from 'react-leaflet';
import L from 'leaflet';
import { getRoleColor, getRoleClass, getDroneColor, TYPE_COLORS } from '../utils/droneIcons';
import ObstacleLayer from './ObstacleLayer';
import WaypointPlanner from './WaypointPlanner';
import PlannedPathLine from './PlannedPathLine';

const MAP_CENTER = [28.4595, 77.5021]; // Greater Noida
const MAP_ZOOM = 14;
const DARK_TILES = 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png';

/**
 * Map click handler — adds waypoints, obstacles, or spawn point when in respective modes.
 */
function MapClickHandler({ planningMode, obstacleMode, spawnMode, onMapClick }) {
    useMapEvents({
        click(e) {
            if (planningMode || obstacleMode || spawnMode) {
                onMapClick(e.latlng);
            }
        },
    });
    return null;
}

/**
 * Component to auto-fit map bounds around active drones.
 */
function SwarmTracker({ drones, isFollowing }) {
    const map = useMap();

    useEffect(() => {
        if (!isFollowing) return;

        const activeDrones = Object.values(drones || {}).filter(d => d.status !== 'LOST' && d.role !== 'LOST');
        if (activeDrones.length === 0) return;

        const coords = activeDrones.map(d => [d.latitude, d.longitude]);

        map.fitBounds(coords, { padding: [60, 60], maxZoom: 17, animate: true, duration: 0.5 });
    }, [drones, isFollowing, map]);

    return null;
}

/**
 * Create a Leaflet DivIcon for a drone with SVG arrow rotated to heading.
 */
function createDroneIcon(drone) {
    const isLeader = drone.role === 'LEADER';
    const isLost = drone.role === 'LOST' || drone.status === 'LOST';
    const isCandidate = drone.role === 'CANDIDATE';

    // Use TYPE color for fill, ROLE color for glow/ring
    const droneType = (drone.droneType || '').toUpperCase();
    const fillColor = isLost ? '#ef4444' : (TYPE_COLORS[droneType] || '#22c55e');
    const size = isLeader ? 32 : 24;
    const rotation = drone.heading || 0;
    const opacity = isLost ? 0.4 : 1;

    // Build glow/ring for leader or candidate
    let glowStyle = '';
    if (isLeader) {
        glowStyle = 'box-shadow: 0 0 12px 4px #3b82f6; animation: leaderPulse 1.5s ease-in-out infinite;';
    } else if (isCandidate) {
        glowStyle = 'border: 2px solid #f97316; animation: statusBarFlash 0.4s steps(1) infinite;';
    }

    const html = `
        <div style="
            width: ${size}px; height: ${size}px;
            display: flex; align-items: center; justify-content: center;
            border-radius: 50%;
            opacity: ${opacity};
            ${glowStyle}
            position: relative;
        ">
            <svg viewBox="0 0 24 24" width="${size - 8}" height="${size - 8}"
                 style="transform: rotate(${rotation}deg); fill: ${fillColor}; filter: drop-shadow(0 0 3px ${fillColor});">
                <path d="M12 2L4 20h16L12 2z"/>
            </svg>
            ${isLost ? `<span style="position:absolute;color:#ef4444;font-size:${size - 6}px;font-weight:900;top:50%;left:50%;transform:translate(-50%,-50%);">✕</span>` : ''}
        </div>
    `;

    return L.divIcon({
        html,
        className: '',
        iconSize: [size, size],
        iconAnchor: [size / 2, size / 2],
    });
}

/**
 * Tactical Map — Leaflet with dark CartoDB tiles, drone markers, path trails,
 * obstacles, waypoints, and planned path lines.
 */
export default function TacticalMap({
    drones, trails, obstacles, waypoints,
    planningMode, obstacleMode, spawnMode, spawnMarker,
    showObstacles,
    onMapClick
}) {
    const [isFollowing, setIsFollowing] = useState(true);
    const droneList = useMemo(() => Object.values(drones || {}), [drones]);

    // Determine the furthest along waypoint the swarm has reached
    const swarmCurrentWaypointIndex = useMemo(() => {
        if (!droneList || droneList.length === 0) return 0;
        const activeDrones = droneList.filter(d => d.status !== 'LOST' && d.role !== 'LOST');
        if (activeDrones.length === 0) return 0;

        return Math.min(...activeDrones.map(d => d.currentWaypointIndex || 0));
    }, [droneList]);

    return (
        <div style={{ width: '100%', height: '100%', position: 'relative' }}>
            <MapContainer
                center={MAP_CENTER}
                zoom={MAP_ZOOM}
                style={{
                    width: '100%',
                    height: '100%',
                    cursor: (planningMode || obstacleMode || spawnMode) ? 'crosshair' : 'grab',
                }}
                zoomControl={true}
                attributionControl={true}
            >
                <TileLayer
                    url={DARK_TILES}
                    attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> &copy; <a href="https://carto.com/">CARTO</a>'
                    subdomains="abcd"
                    maxZoom={20}
                />

                {/* Click handler for waypoint/obstacle placement */}
                <MapClickHandler
                    planningMode={planningMode}
                    obstacleMode={obstacleMode}
                    spawnMode={spawnMode}
                    onMapClick={onMapClick}
                />

                {/* Obstacle circles */}
                <ObstacleLayer obstacles={obstacles} showObstacles={showObstacles} />

                {/* Waypoint markers and route */}
                <WaypointPlanner
                    waypoints={waypoints}
                    planningMode={planningMode}
                    swarmCurrentIndex={swarmCurrentWaypointIndex}
                    onRemoveWaypoint={onMapClick}
                />

                {/* Planned path lines (obstacle avoidance visualization) */}
                <PlannedPathLine drones={drones} />

                {/* Auto-pan tracker */}
                <SwarmTracker drones={drones} isFollowing={isFollowing} />

                {/* Path trails */}
                {Object.entries(trails || {}).map(([droneId, positions]) => {
                    if (!positions || positions.length < 2) return null;
                    const drone = drones[droneId];
                    const color = drone ? getDroneColor(drone) : '#94a3b8';
                    return (
                        <Polyline
                            key={`trail-${droneId}`}
                            positions={positions}
                            pathOptions={{ color, weight: 2, opacity: 0.4, dashArray: '4 4' }}
                        />
                    );
                })}

                {/* Drone markers — DivIcon SVG arrows rotated to heading */}
                {droneList.map(drone => (
                    <DroneDivMarker key={drone.droneId} drone={drone} />
                ))}

                {/* Spawn point marker */}
                {spawnMarker && (
                    <CircleMarker
                        center={[spawnMarker.lat, spawnMarker.lng]}
                        radius={12}
                        pathOptions={{
                            color: '#f59e0b',
                            weight: 2,
                            fillColor: '#f59e0b',
                            fillOpacity: 0.3,
                            dashArray: '4 4',
                        }}
                    >
                        <Tooltip permanent direction="top" offset={[0, -14]}>
                            <div style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '10px', color: '#f59e0b' }}>
                                ✦ SPAWN POINT
                            </div>
                        </Tooltip>
                    </CircleMarker>
                )}
            </MapContainer>

            {/* Map overlay label */}
            <div style={{
                position: 'absolute', top: 10, left: 55, zIndex: 1000,
                background: 'rgba(10, 14, 23, 0.85)', padding: '4px 10px',
                borderRadius: 4, border: '1px solid var(--border)',
                fontSize: '10px', color: 'var(--text-secondary)',
            }}>
                TACTICAL VIEW • GREATER NOIDA SECTOR
                {planningMode && (
                    <span style={{ color: '#06b6d4', marginLeft: 8 }}>
                        📍 CLICK TO PLACE WAYPOINTS
                    </span>
                )}
                {obstacleMode && (
                    <span style={{ color: '#f97316', marginLeft: 8 }}>
                        🚧 CLICK TO PLACE OBSTACLE
                    </span>
                )}
                {spawnMode && (
                    <span style={{ color: '#f59e0b', marginLeft: 8 }}>
                        ✦ CLICK TO SET SPAWN POINT
                    </span>
                )}
                <div style={{ marginTop: 6, display: 'flex', alignItems: 'center', gap: 6, pointerEvents: 'auto' }}>
                    <input
                        type="checkbox"
                        id="follow-swarm"
                        checked={isFollowing}
                        onChange={(e) => setIsFollowing(e.target.checked)}
                        style={{ cursor: 'pointer', accentColor: '#06b6d4' }}
                    />
                    <label htmlFor="follow-swarm" style={{ cursor: 'pointer', userSelect: 'none' }}>
                        FOLLOW SWARM (KEEP IN FRAME)
                    </label>
                </div>
            </div>

            {/* RS-GCS watermark */}
            <div style={{
                position: 'absolute', bottom: 24, left: 10, zIndex: 1000,
                fontSize: '9px', color: 'rgba(148, 163, 184, 0.4)',
                fontFamily: "'JetBrains Mono', monospace",
                pointerEvents: 'none',
            }}>
                RS-GCS v1.0
            </div>
        </div>
    );
}

/**
 * Individual drone marker — DivIcon with SVG arrow rotated to heading direction.
 * TYPE-colored fill, leader gets pulsing blue ring, lost gets X overlay.
 */
const DroneDivMarker = React.memo(function DroneDivMarker({ drone }) {
    const icon = useMemo(() => createDroneIcon(drone), [
        drone.droneId, drone.role, drone.status, drone.heading, drone.droneType
    ]);

    return (
        <Marker
            position={[drone.latitude, drone.longitude]}
            icon={icon}
        >
            <Tooltip permanent={false} direction="top" offset={[0, -14]}>
                <div style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '11px' }}>
                    <strong>Drone-{drone.droneId}</strong> | {drone.droneType}<br />
                    Bat: {drone.batteryPercent?.toFixed(1)}%<br />
                    Alt: {drone.altitude?.toFixed(0)}m | Spd: {drone.speed?.toFixed(1)} m/s | Hdg: {drone.heading?.toFixed(0)}°
                </div>
            </Tooltip>
        </Marker>
    );
});
