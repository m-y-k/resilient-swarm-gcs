import React, { useState, useCallback } from 'react';

/**
 * Control Panel — military-style command buttons.
 * DESTROY LEADER (red), KILL RANDOM (orange), START MISSION (green), RESET (grey)
 */
export default function ControlPanel({ killLeader, killRandom, startMission, resetSwarm, missionState }) {
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

    const buttons = [
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

            {/* Buttons */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px' }}>
                {buttons.map(btn => (
                    <button
                        key={btn.name}
                        onClick={() => handleWithCooldown(btn.action, btn.name)}
                        disabled={btn.disabled || cooldown === btn.name}
                        style={{
                            padding: '8px 4px',
                            fontSize: '10px',
                            fontFamily: "'JetBrains Mono', monospace",
                            fontWeight: 600,
                            background: (btn.disabled || cooldown === btn.name) ? '#1e293b' : btn.bg,
                            color: (btn.disabled || cooldown === btn.name) ? '#4a5568' : '#fff',
                            border: `1px solid ${(btn.disabled || cooldown === btn.name) ? '#334155' : btn.border}`,
                            borderRadius: 4,
                            cursor: (btn.disabled || cooldown === btn.name) ? 'not-allowed' : 'pointer',
                            transition: 'all 0.2s ease',
                            letterSpacing: '0.5px',
                        }}
                        onMouseEnter={e => {
                            if (!btn.disabled && cooldown !== btn.name) {
                                e.target.style.background = btn.hoverBg;
                            }
                        }}
                        onMouseLeave={e => {
                            if (!btn.disabled && cooldown !== btn.name) {
                                e.target.style.background = btn.bg;
                            }
                        }}
                    >
                        {cooldown === btn.name ? '⏳ WAIT...' : btn.label}
                    </button>
                ))}
            </div>
        </div>
    );
}
