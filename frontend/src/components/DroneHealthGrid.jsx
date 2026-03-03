import React, { useMemo } from 'react';

/**
 * Horizontal row of 10 drone health boxes — instant visual swarm overview.
 * Green = ACTIVE, Yellow = STALE, Red = LOST. Leader has blue ring.
 */
export default function DroneHealthGrid({ drones }) {
    const droneList = useMemo(
        () => Object.values(drones || {}).sort((a, b) => a.droneId - b.droneId),
        [drones]
    );

    // Pad to 10 slots if fewer drones
    const slots = useMemo(() => {
        const result = [];
        for (let i = 1; i <= 10; i++) {
            result.push(droneList.find(d => d.droneId === i) || null);
        }
        return result;
    }, [droneList]);

    return (
        <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            padding: '6px 12px',
            background: 'var(--bg-secondary)',
            borderTop: '1px solid var(--border)',
            borderBottom: '1px solid var(--border)',
        }}>
            <span style={{ fontSize: '10px', color: 'var(--text-secondary)', marginRight: 4, whiteSpace: 'nowrap' }}>
                SWARM:
            </span>
            {slots.map((drone, idx) => {
                const id = idx + 1;
                const status = drone?.status || 'NONE';
                const isLeader = drone?.role === 'LEADER';
                const bgColor = !drone ? '#1e293b'
                    : status === 'ACTIVE' ? 'var(--accent-green)'
                        : status === 'STALE' ? 'var(--accent-yellow)'
                            : 'var(--accent-red)';

                return (
                    <div
                        key={id}
                        title={drone ? `D${id} | ${drone.droneType} | ${drone.role} | Bat: ${drone.batteryPercent?.toFixed(1)}%` : `D${id} — offline`}
                        style={{
                            width: 36,
                            height: 36,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            borderRadius: 4,
                            fontSize: '10px',
                            fontWeight: 700,
                            background: !drone ? '#1e293b' : `${bgColor}22`,
                            color: !drone ? '#4a5568' : bgColor,
                            border: isLeader ? '2px solid var(--accent-blue)' : `1px solid ${!drone ? '#334155' : `${bgColor}44`}`,
                            boxShadow: isLeader ? '0 0 6px rgba(59, 130, 246, 0.3)' : 'none',
                            transition: 'all 0.3s ease',
                            cursor: 'default',
                            position: 'relative',
                        }}
                    >
                        D{id}
                        {/* Status dot */}
                        {drone && (
                            <div style={{
                                position: 'absolute',
                                top: 2, right: 2,
                                width: 5, height: 5,
                                borderRadius: '50%',
                                background: bgColor,
                            }} />
                        )}
                    </div>
                );
            })}
        </div>
    );
}
