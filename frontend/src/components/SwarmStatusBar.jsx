import React from 'react';

/**
 * Top status bar — mission status, leader ID, active count, elections, uptime.
 */
export default function SwarmStatusBar({ missionState, drones, isConnected }) {
    const status = missionState?.status || 'IDLE';
    const leaderId = missionState?.currentLeaderId || 0;
    const activeCount = missionState?.activeDroneCount || 0;
    const totalCount = missionState?.totalDroneCount || 10;
    const electionCount = missionState?.electionCount || 0;
    const isRecovering = status === 'RECOVERING';

    const statusColors = {
        IDLE: '#94a3b8',
        ACTIVE: '#22c55e',
        PAUSED: '#eab308',
        RECOVERING: '#f97316',
    };

    return (
        <div className={`flex items-center justify-between px-4 py-2 border-b ${isRecovering ? 'status-flash' : ''}`}
            style={{
                background: 'var(--bg-secondary)',
                borderColor: 'var(--border)',
                minHeight: '48px',
            }}>
            {/* Left — Logo + Mission Status */}
            <div className="flex items-center gap-4">
                <span style={{ color: 'var(--accent-blue)', fontWeight: 700, fontSize: '14px' }}>
                    RS-GCS v1.0
                </span>
                <span style={{ color: 'var(--text-secondary)', fontSize: '12px' }}>│</span>
                <span style={{ fontSize: '12px' }}>
                    MISSION:{' '}
                    <span style={{ color: statusColors[status] || '#94a3b8', fontWeight: 700 }}>
                        {status}
                    </span>
                </span>
            </div>

            {/* Center — Leader */}
            <div style={{ fontSize: '13px' }}>
                LEADER:{' '}
                {leaderId > 0 ? (
                    <span style={{ color: 'var(--accent-blue)', fontWeight: 700 }}>
                        Drone_{leaderId}
                    </span>
                ) : (
                    <span style={{ color: 'var(--accent-red)', fontWeight: 700 }}>
                        NO LEADER
                    </span>
                )}
            </div>

            {/* Right — Stats */}
            <div className="flex items-center gap-4" style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>
                <span>
                    ACTIVE:{' '}
                    <span style={{ color: activeCount > 0 ? 'var(--accent-green)' : 'var(--accent-red)', fontWeight: 700 }}>
                        {activeCount}/{totalCount}
                    </span>
                </span>
                <span>
                    ELECTIONS:{' '}
                    <span style={{ color: electionCount > 0 ? 'var(--accent-orange)' : 'var(--text-secondary)', fontWeight: 700 }}>
                        {electionCount}
                    </span>
                </span>
                <span style={{
                    display: 'inline-flex', alignItems: 'center', gap: '4px',
                    color: isConnected ? 'var(--accent-green)' : 'var(--accent-red)'
                }}>
                    <span style={{
                        width: 8, height: 8, borderRadius: '50%',
                        background: isConnected ? 'var(--accent-green)' : 'var(--accent-red)',
                        display: 'inline-block',
                    }} />
                    {isConnected ? 'LINK' : 'OFFLINE'}
                </span>
            </div>
        </div>
    );
}
