import React, { useState, useCallback } from 'react';

/**
 * Control Panel — military-style command buttons + mission planning controls.
 */
export default function ControlPanel({
    killLeader, killRandom, startMission, resetSwarm, missionState,
    planningMode, setPlanningMode, obstacleMode, setObstacleMode,
    showObstacles, setShowObstacles, deployMission, waypointCount,
    spawnMode, setSpawnMode, spawnMarker,
}) {
    const [cooldown, setCooldown] = useState(null);
    const status = missionState?.status || 'IDLE';

    const handleWithCooldown = useCallback((action, name, delay = 3000) => {
        if (cooldown === name) return;

        if (name === 'kill-leader') {
            if (!window.confirm('⚠️ Terminate Leader Drone?\nThis will trigger emergency election.')) return;
        }

        setCooldown(name);
        action();
        setTimeout(() => setCooldown(null), delay);
    }, [cooldown]);

    const combatButtons = [
        {
            label: '⚡ DESTROY LEADER',
            action: killLeader,
            name: 'kill-leader',
            bg: '#991b1b', hoverBg: '#dc2626',
            border: '#ef4444',
            disabled: status === 'IDLE',
        },
        {
            label: '💥 KILL RANDOM',
            action: killRandom,
            name: 'kill-random',
            bg: '#92400e', hoverBg: '#d97706',
            border: '#f97316',
            disabled: status === 'IDLE',
        },
        {
            label: '▶ START MISSION',
            action: startMission,
            name: 'start-mission',
            bg: '#14532d', hoverBg: '#16a34a',
            border: '#22c55e',
            disabled: status === 'ACTIVE',
        },
        {
            label: '↺ RESET SWARM',
            action: resetSwarm,
            name: 'reset',
            bg: '#374151', hoverBg: '#6b7280',
            border: '#9ca3af',
            disabled: false,
        },
    ];

    return (
        <div style={{
            background: 'var(--bg-panel)',
            borderLeft: '1px solid var(--border)',
            borderTop: '1px solid var(--border)',
            padding: '8px',
        }}>
            {/* Header */}
            <div style={{
                fontSize: '10px',
                color: 'var(--text-secondary)',
                marginBottom: 6,
            }}>
                ▸ COMMAND PANEL
            </div>

            {/* Combat Buttons */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px' }}>
                {combatButtons.map(btn => (
                    <CommandButton
                        key={btn.name}
                        btn={btn}
                        cooldown={cooldown}
                        handleWithCooldown={handleWithCooldown}
                    />
                ))}
            </div>

            {/* Mission Planning Divider */}
            <div style={{
                fontSize: '10px',
                color: 'var(--text-secondary)',
                marginTop: 10,
                marginBottom: 4,
            }}>
                ▸ MISSION PLANNING
            </div>

            {/* Planning Buttons */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px' }}>
                {/* Plan Mission Toggle */}
                <button
                    onClick={() => {
                        setPlanningMode(!planningMode);
                        if (obstacleMode) setObstacleMode(false);
                    }}
                    style={{
                        padding: '8px 4px',
                        fontSize: '10px',
                        fontFamily: "'JetBrains Mono', monospace",
                        fontWeight: 600,
                        background: planningMode ? '#0e7490' : '#164e63',
                        color: planningMode ? '#fff' : '#67e8f9',
                        border: `1px solid ${planningMode ? '#06b6d4' : '#0e7490'}`,
                        borderRadius: 4,
                        cursor: 'pointer',
                        transition: 'all 0.2s ease',
                        letterSpacing: '0.5px',
                    }}
                >
                    {planningMode ? '✖ CANCEL PLAN' : '📍 PLAN MISSION'}
                </button>

                {/* Deploy Mission */}
                <button
                    onClick={deployMission}
                    disabled={waypointCount < 2}
                    style={{
                        padding: '8px 4px',
                        fontSize: '10px',
                        fontFamily: "'JetBrains Mono', monospace",
                        fontWeight: 600,
                        background: waypointCount >= 2 ? '#0e7490' : '#1e293b',
                        color: waypointCount >= 2 ? '#fff' : '#4a5568',
                        border: `1px solid ${waypointCount >= 2 ? '#06b6d4' : '#334155'}`,
                        borderRadius: 4,
                        cursor: waypointCount >= 2 ? 'pointer' : 'not-allowed',
                        transition: 'all 0.2s ease',
                        letterSpacing: '0.5px',
                    }}
                >
                    🚀 DEPLOY ({waypointCount})
                </button>
            </div>

            {/* Obstacle Buttons */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px', marginTop: 6 }}>
                {/* Add Obstacle Toggle */}
                <button
                    onClick={() => {
                        setObstacleMode(!obstacleMode);
                        if (planningMode) setPlanningMode(false);
                    }}
                    style={{
                        padding: '8px 4px',
                        fontSize: '10px',
                        fontFamily: "'JetBrains Mono', monospace",
                        fontWeight: 600,
                        background: obstacleMode ? '#92400e' : '#451a03',
                        color: obstacleMode ? '#fff' : '#f97316',
                        border: `1px solid ${obstacleMode ? '#f97316' : '#92400e'}`,
                        borderRadius: 4,
                        cursor: 'pointer',
                        transition: 'all 0.2s ease',
                        letterSpacing: '0.5px',
                    }}
                >
                    {obstacleMode ? '✖ CANCEL' : '🚧 ADD OBSTACLE'}
                </button>

                {/* Show/Hide Obstacles */}
                <button
                    onClick={() => setShowObstacles(!showObstacles)}
                    style={{
                        padding: '8px 4px',
                        fontSize: '10px',
                        fontFamily: "'JetBrains Mono', monospace",
                        fontWeight: 600,
                        background: showObstacles ? '#374151' : '#1e293b',
                        color: showObstacles ? '#4ade80' : '#4a5568',
                        border: `1px solid ${showObstacles ? '#4ade80' : '#334155'}`,
                        borderRadius: 4,
                        cursor: 'pointer',
                        transition: 'all 0.2s ease',
                        letterSpacing: '0.5px',
                    }}
                >
                    {showObstacles ? '👁 OBSTACLES' : '👁‍🗨 HIDDEN'}
                </button>
            </div>

            {/* Spawn Point */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '6px', marginTop: 6 }}>
                <button
                    onClick={() => {
                        setSpawnMode(!spawnMode);
                        if (planningMode) setPlanningMode(false);
                        if (obstacleMode) setObstacleMode(false);
                    }}
                    style={{
                        padding: '8px 4px',
                        fontSize: '10px',
                        fontFamily: "'JetBrains Mono', monospace",
                        fontWeight: 600,
                        background: spawnMode ? '#92400e' : '#451a03',
                        color: spawnMode ? '#fff' : '#f59e0b',
                        border: `1px solid ${spawnMode ? '#f59e0b' : '#92400e'}`,
                        borderRadius: 4,
                        cursor: 'pointer',
                        transition: 'all 0.2s ease',
                        letterSpacing: '0.5px',
                    }}
                >
                    {spawnMode ? '✖ CANCEL' : '✦ SET SPAWN'}
                </button>
                {spawnMarker && (
                    <div style={{
                        fontSize: '9px',
                        color: '#f59e0b',
                        fontFamily: "'JetBrains Mono', monospace",
                        textAlign: 'center',
                        opacity: 0.7,
                    }}>
                        ✦ {spawnMarker.lat.toFixed(4)}, {spawnMarker.lng.toFixed(4)}
                    </div>
                )}
            </div>
        </div>
    );
}

/** Reusable command button. */
function CommandButton({ btn, cooldown, handleWithCooldown }) {
    const isDisabled = btn.disabled || cooldown === btn.name;
    return (
        <button
            onClick={() => handleWithCooldown(btn.action, btn.name)}
            disabled={isDisabled}
            style={{
                padding: '8px 4px',
                fontSize: '10px',
                fontFamily: "'JetBrains Mono', monospace",
                fontWeight: 600,
                background: isDisabled ? '#1e293b' : btn.bg,
                color: isDisabled ? '#4a5568' : '#fff',
                border: `1px solid ${isDisabled ? '#334155' : btn.border}`,
                borderRadius: 4,
                cursor: isDisabled ? 'not-allowed' : 'pointer',
                transition: 'all 0.2s ease',
                letterSpacing: '0.5px',
            }}
            onMouseEnter={e => {
                if (!isDisabled) e.target.style.background = btn.hoverBg;
            }}
            onMouseLeave={e => {
                if (!isDisabled) e.target.style.background = btn.bg;
            }}
        >
            {cooldown === btn.name ? '⏳ WAIT...' : btn.label}
        </button>
    );
}
