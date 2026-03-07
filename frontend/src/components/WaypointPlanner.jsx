import React, { useMemo } from 'react';
import { CircleMarker, Polyline, Tooltip, useMapEvents } from 'react-leaflet';

/**
 * WaypointPlanner — renders numbered waypoint markers and a dashed cyan route line.
 * Shows W1, W2, W3... connected by the planned mission path.
 * Right-click on a waypoint to delete it. Drag is not supported (Leaflet CircleMarker limitation).
 */
export default function WaypointPlanner({ waypoints, planningMode, swarmCurrentIndex = 0, onRemoveWaypoint }) {
    const positions = useMemo(
        () => waypoints.map(wp => [wp.lat, wp.lng]),
        [waypoints]
    );

    if (!waypoints || waypoints.length === 0) return null;

    // Only draw the path from the current waypoint onwards
    const remainingPositions = positions.slice(swarmCurrentIndex);

    return (
        <>
            {/* Route Polyline — dashed cyan */}
            {remainingPositions.length >= 2 && (
                <Polyline
                    positions={remainingPositions}
                    pathOptions={{
                        color: '#06b6d4',
                        weight: 2,
                        opacity: 0.7,
                        dashArray: '8 6',
                    }}
                />
            )}

            {/* Waypoint Markers */}
            {waypoints.map((wp, idx) => (
                <CircleMarker
                    key={wp.id}
                    center={[wp.lat, wp.lng]}
                    radius={planningMode ? 10 : 7}
                    pathOptions={{
                        color: '#06b6d4',
                        weight: 2,
                        fillColor: planningMode ? '#0e7490' : '#164e63',
                        fillOpacity: 0.8,
                    }}
                    eventHandlers={{
                        contextmenu: (e) => {
                            if (planningMode && onRemoveWaypoint) {
                                e.originalEvent.preventDefault();
                                // Signal removal by passing waypoint id with __removeWaypoint flag
                                onRemoveWaypoint({ __removeWaypoint: wp.id });
                            }
                        },
                    }}
                >
                    <Tooltip permanent direction="center" className="waypoint-tooltip">
                        <div style={{
                            fontFamily: "'JetBrains Mono', monospace",
                            fontSize: '10px',
                            fontWeight: 700,
                            color: '#06b6d4',
                            textAlign: 'center',
                        }}>
                            W{idx + 1}
                        </div>
                    </Tooltip>
                </CircleMarker>
            ))}
        </>
    );
}
