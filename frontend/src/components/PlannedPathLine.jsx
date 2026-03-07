import React, { useMemo } from 'react';
import { Polyline } from 'react-leaflet';
import { getRoleColor, getDroneColor } from '../utils/droneIcons';

/**
 * PlannedPathLine — dotted line showing drone's intended path through detour points.
 * Visible when drone is actively navigating around obstacles.
 */
export default function PlannedPathLine({ drones }) {
    const droneList = useMemo(() => Object.values(drones || {}), [drones]);

    return (
        <>
            {droneList.map(drone => {
                if (!drone.plannedPath || drone.plannedPath.length === 0) return null;
                if (drone.status === 'LOST') return null;

                const color = getDroneColor(drone);

                // Build path: drone position → detour points → destination
                const positions = [
                    [drone.latitude, drone.longitude],
                    ...drone.plannedPath.map(p => [p[0], p[1]]),
                ];

                return (
                    <Polyline
                        key={`planned-${drone.droneId}`}
                        positions={positions}
                        pathOptions={{
                            color: color,
                            weight: 1.5,
                            opacity: 0.4,
                            dashArray: '3 5',
                        }}
                    />
                );
            })}
        </>
    );
}
