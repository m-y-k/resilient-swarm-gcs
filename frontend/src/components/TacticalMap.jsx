import React, { useMemo } from 'react';
import { MapContainer, TileLayer, CircleMarker, Polyline, Tooltip, useMap } from 'react-leaflet';
import { getRoleColor, getRoleClass } from '../utils/droneIcons';

const MAP_CENTER = [28.4595, 77.5021]; // Greater Noida
const MAP_ZOOM = 15;
const DARK_TILES = 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png';

/**
 * Tactical Map — Leaflet with dark CartoDB tiles, drone markers, and path trails.
 */
export default function TacticalMap({ drones, trails }) {
    const droneList = useMemo(() => Object.values(drones || {}), [drones]);

    return (
        <div style={{ width: '100%', height: '100%', position: 'relative' }}>
            <MapContainer
                center={MAP_CENTER}
                zoom={MAP_ZOOM}
                style={{ width: '100%', height: '100%' }}
                zoomControl={true}
                attributionControl={true}
            >
                <TileLayer
                    url={DARK_TILES}
                    attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> &copy; <a href="https://carto.com/">CARTO</a>'
                    subdomains="abcd"
                    maxZoom={20}
                />

                {/* Path trails */}
                {Object.entries(trails || {}).map(([droneId, positions]) => {
                    if (!positions || positions.length < 2) return null;
                    const drone = drones[droneId];
                    const color = drone ? getRoleColor(drone.role) : '#94a3b8';
                    return (
                        <Polyline
                            key={`trail-${droneId}`}
                            positions={positions}
                            pathOptions={{ color, weight: 2, opacity: 0.4, dashArray: '4 4' }}
                        />
                    );
                })}

                {/* Drone markers */}
                {droneList.map(drone => (
                    <DroneCircleMarker key={drone.droneId} drone={drone} />
                ))}
            </MapContainer>

            {/* Map overlay label */}
            <div style={{
                position: 'absolute', top: 10, left: 55, zIndex: 1000,
                background: 'rgba(10, 14, 23, 0.85)', padding: '4px 10px',
                borderRadius: 4, border: '1px solid var(--border)',
                fontSize: '10px', color: 'var(--text-secondary)',
            }}>
                TACTICAL VIEW • GREATER NOIDA SECTOR
            </div>
        </div>
    );
}

/**
 * Individual drone marker — CircleMarker with role-based color and tooltip.
 */
const DroneCircleMarker = React.memo(function DroneCircleMarker({ drone }) {
    const color = getRoleColor(drone.role);
    const isLeader = drone.role === 'LEADER';
    const isLost = drone.role === 'LOST' || drone.status === 'LOST';
    const radius = isLeader ? 10 : 7;

    return (
        <>
            {/* Leader pulse ring */}
            {isLeader && (
                <CircleMarker
                    center={[drone.latitude, drone.longitude]}
                    radius={18}
                    pathOptions={{
                        color: 'var(--accent-blue)',
                        weight: 1,
                        opacity: 0.4,
                        fillOpacity: 0.05,
                        className: 'leader-pulse',
                    }}
                />
            )}

            <CircleMarker
                center={[drone.latitude, drone.longitude]}
                radius={radius}
                pathOptions={{
                    color: color,
                    weight: 2,
                    fillColor: color,
                    fillOpacity: isLost ? 0.2 : 0.6,
                    opacity: isLost ? 0.4 : 1,
                }}
            >
                <Tooltip permanent={false} direction="top" offset={[0, -10]}>
                    <div style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '11px' }}>
                        <strong>Drone_{drone.droneId}</strong> | {drone.droneType}<br />
                        Role: {drone.role} | Bat: {drone.batteryPercent?.toFixed(1)}%<br />
                        Alt: {drone.altitude?.toFixed(0)}m | Spd: {drone.speed?.toFixed(1)} m/s
                    </div>
                </Tooltip>
            </CircleMarker>

            {/* Lost X marker */}
            {isLost && (
                <CircleMarker
                    center={[drone.latitude, drone.longitude]}
                    radius={4}
                    pathOptions={{
                        color: 'var(--accent-red)',
                        weight: 3,
                        fillOpacity: 0,
                    }}
                />
            )}
        </>
    );
});
