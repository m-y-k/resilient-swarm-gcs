import React, { useEffect, useRef } from 'react';

/**
 * Election Log Terminal — THE SHOWPIECE COMPONENT.
 * Green monospace scrolling terminal showing election events and system logs.
 */
export default function ElectionLog({ logs }) {
    const bottomRef = useRef(null);

    // Auto-scroll to bottom on new log entries
    useEffect(() => {
        bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [logs]);

    const getLevelColor = (level) => {
        switch (level) {
            case 'HEARTBEAT': return '#4a5568';
            case 'INFO': return 'var(--accent-green)';
            case 'WARN': return 'var(--accent-yellow)';
            case 'CRITICAL': return 'var(--accent-red)';
            case 'SUCCESS': return '#4ade80';
            case 'ELECTION': return 'var(--accent-orange)';
            default: return 'var(--terminal-green)';
        }
    };

    const formatTime = (ts) => {
        if (!ts) return '??:??:??';
        try {
            const d = new Date(ts);
            return d.toLocaleTimeString('en-US', { hour12: false });
        } catch { return '??:??:??'; }
    };

    return (
        <div style={{
            background: '#0a0a0a',
            height: '100%',
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column',
            borderTop: '1px solid var(--border)',
        }}>
            {/* Terminal header */}
            <div style={{
                padding: '4px 10px',
                background: 'var(--bg-secondary)',
                borderBottom: '1px solid var(--border)',
                fontSize: '10px',
                color: 'var(--text-secondary)',
                display: 'flex',
                justifyContent: 'space-between',
            }}>
                <span>▸ ELECTION LOG TERMINAL</span>
                <span>{logs.length} entries</span>
            </div>

            {/* Log lines */}
            <div style={{
                flex: 1,
                overflowY: 'auto',
                padding: '4px 8px',
                fontSize: '11px',
                fontFamily: "'JetBrains Mono', monospace",
                lineHeight: '1.6',
            }}>
                {logs.length === 0 && (
                    <div style={{ color: '#4a5568', padding: '8px 0' }}>
                        {'>'} Awaiting system events...
                    </div>
                )}
                {logs.map((log, i) => (
                    <div key={i} className="log-entry" style={{ color: getLevelColor(log.level) }}>
                        <span style={{ color: '#4a5568' }}>{'>'} </span>
                        <span style={{ color: '#6b7280' }}>[{formatTime(log.timestamp)}]</span>
                        {' '}
                        <span style={{
                            fontWeight: (log.level === 'CRITICAL' || log.level === 'SUCCESS') ? 700 : 400,
                        }}>
                            {log.message}
                        </span>
                    </div>
                ))}
                <div ref={bottomRef} />
            </div>
        </div>
    );
}
