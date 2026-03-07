import React from 'react';
import { Circle, Tooltip } from 'react-leaflet';
import { OBSTACLE_COLORS, OBSTACLE_ICONS } from '../data/greaterNoidaObstacles';

/**
 * ObstacleLayer — renders obstacles as translucent colored circles.
 * Labels appear ONLY on hover to avoid cluttering the map.
 */
export default function ObstacleLayer({ obstacles, showObstacles }) {
    if (!showObstacles || !obstacles || obstacles.length === 0) return null;

    return (
        <>
            {obstacles.map(obs => {
                const color = OBSTACLE_COLORS[obs.type] || '#6b7280';
                const icon = OBSTACLE_ICONS[obs.type] || '⚠️';
                const isRfHazard = obs.type === 'RF_HAZARD';

                return (
                    <Circle
                        key={obs.id}
                        center={[obs.latitude, obs.longitude]}
                        radius={obs.radius}
                        pathOptions={{
                            color: color,
                            weight: 2,
                            fillColor: color,
                            fillOpacity: 0.12,
                            opacity: 0.6,
                            dashArray: isRfHazard ? '8 4' : '6 3',
                            className: isRfHazard ? 'rf-hazard-pulse' : '',
                        }}
                    >
                        <Tooltip direction="top" className="obstacle-tooltip">
                            <div style={{
                                fontFamily: "'JetBrains Mono', monospace",
                                fontSize: '10px',
                                color: color,
                                textAlign: 'center',
                                fontWeight: 600,
                            }}>
                                {icon} {obs.name}
                                {obs.height > 0 && <><br />{obs.height}m · {obs.radius}m radius</>}
                            </div>
                        </Tooltip>
                    </Circle>
                );
            })}
        </>
    );
}
