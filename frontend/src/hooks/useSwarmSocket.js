import { useState, useEffect, useCallback, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';
const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws';

/**
 * Custom hook: STOMP WebSocket connection to Spring Boot backend.
 * Subscribes to /topic/drones, /topic/events, /topic/mission, /topic/logs
 */
export default function useSwarmSocket() {
    const [drones, setDrones] = useState({});
    const [missionState, setMissionState] = useState(null);
    const [eventLog, setEventLog] = useState([]);
    const [logs, setLogs] = useState([]);
    const [isConnected, setIsConnected] = useState(false);
    const [trails, setTrails] = useState({});
    const clientRef = useRef(null);

    // Fetch initial snapshot
    const fetchSnapshot = useCallback(async () => {
        try {
            const res = await fetch(`${BACKEND_URL}/api/swarm/snapshot`);
            const data = await res.json();
            if (data.drones && Array.isArray(data.drones)) {
                const droneMap = {};
                data.drones.forEach(d => { droneMap[d.droneId] = d; });
                setDrones(droneMap);
            }
            if (data.mission) setMissionState(data.mission);
            if (data.trails) setTrails(data.trails);
        } catch (err) {
            console.warn('Failed to fetch snapshot:', err);
        }
    }, []);

    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS(WS_URL),
            reconnectDelay: 3000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,

            onConnect: () => {
                console.log('[WS] Connected to RS-GCS');
                setIsConnected(true);
                fetchSnapshot();

                // Subscribe to drone state updates
                client.subscribe('/topic/drones', (message) => {
                    try {
                        const data = JSON.parse(message.body);
                        if (data.drones && Array.isArray(data.drones)) {
                            const droneMap = {};
                            data.drones.forEach(d => { droneMap[d.droneId] = d; });
                            setDrones(droneMap);
                        }
                        if (data.trails) setTrails(data.trails);
                        if (data.mission) setMissionState(data.mission);
                    } catch (e) { console.warn('Parse error /topic/drones:', e); }
                });

                // Subscribe to election events
                client.subscribe('/topic/events', (message) => {
                    try {
                        const event = JSON.parse(message.body);
                        setEventLog(prev => {
                            const updated = [...prev, event];
                            return updated.length > 200 ? updated.slice(-200) : updated;
                        });
                    } catch (e) { console.warn('Parse error /topic/events:', e); }
                });

                // Subscribe to mission state
                client.subscribe('/topic/mission', (message) => {
                    try {
                        setMissionState(JSON.parse(message.body));
                    } catch (e) { console.warn('Parse error /topic/mission:', e); }
                });

                // Subscribe to log lines
                client.subscribe('/topic/logs', (message) => {
                    try {
                        const log = JSON.parse(message.body);
                        setLogs(prev => {
                            const updated = [...prev, log];
                            return updated.length > 200 ? updated.slice(-200) : updated;
                        });
                    } catch (e) { console.warn('Parse error /topic/logs:', e); }
                });
            },

            onDisconnect: () => {
                console.log('[WS] Disconnected');
                setIsConnected(false);
            },

            onStompError: (frame) => {
                console.error('[WS] STOMP error:', frame.headers?.message);
            },
        });

        client.activate();
        clientRef.current = client;

        return () => {
            client.deactivate();
        };
    }, [fetchSnapshot]);

    // Command helpers
    const sendCommand = useCallback(async (endpoint, method = 'POST') => {
        try {
            await fetch(`${BACKEND_URL}${endpoint}`, { method });
        } catch (err) {
            console.error('Command failed:', err);
        }
    }, []);

    const killLeader = useCallback(() => sendCommand('/api/command/kill-leader'), [sendCommand]);
    const killRandom = useCallback(() => sendCommand('/api/command/kill-random'), [sendCommand]);
    const startMission = useCallback(() => sendCommand('/api/command/start-mission'), [sendCommand]);
    const resetSwarm = useCallback(() => sendCommand('/api/command/reset'), [sendCommand]);

    return {
        drones, missionState, eventLog, logs, trails,
        isConnected,
        killLeader, killRandom, startMission, resetSwarm,
    };
}
