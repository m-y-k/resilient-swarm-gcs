import { useState, useEffect, useCallback, useRef } from 'react';
import { Client } from '@stomp/stompjs';

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';
const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws';

/**
 * Custom hook: STOMP WebSocket connection to Spring Boot backend.
 * Subscribes to /topic/drones, /topic/events, /topic/mission, /topic/logs
 * Provides command helpers for missions, obstacles, and waypoints.
 */
export default function useSwarmSocket() {
    const [drones, setDrones] = useState({});
    const [missionState, setMissionState] = useState(null);
    const [eventLog, setEventLog] = useState([]);
    const [logs, setLogs] = useState([]);
    const [isConnected, setIsConnected] = useState(false);
    const [trails, setTrails] = useState({});
    const [obstacles, setObstacles] = useState([]);
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

    // Fetch obstacles from backend
    const fetchObstacles = useCallback(async () => {
        try {
            const res = await fetch(`${BACKEND_URL}/api/obstacles`);
            const data = await res.json();
            if (Array.isArray(data)) setObstacles(data);
        } catch (err) {
            console.warn('Failed to fetch obstacles:', err);
        }
    }, []);

    useEffect(() => {
        // Derive proper WS URL (supports http/https or ws/wss in env)
        const brokerURL = WS_URL.startsWith('ws')
            ? WS_URL
            : WS_URL.replace(/^http/, 'ws');

        const client = new Client({
            brokerURL,
            reconnectDelay: 3000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,

            onConnect: () => {
                console.log('[WS] Connected to RS-GCS');
                setIsConnected(true);
                fetchSnapshot();
                fetchObstacles();

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
                            return updated.length > 500 ? updated.slice(-500) : updated;
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
                            return updated.length > 500 ? updated.slice(-500) : updated;
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
    }, [fetchSnapshot, fetchObstacles]);

    // Command helpers
    const sendCommand = useCallback(async (endpoint, method = 'POST', body = null) => {
        try {
            const opts = { method };
            if (body) {
                opts.headers = { 'Content-Type': 'application/json' };
                opts.body = JSON.stringify(body);
            }
            await fetch(`${BACKEND_URL}${endpoint}`, opts);
        } catch (err) {
            console.error('Command failed:', err);
        }
    }, []);

    const killLeader = useCallback(() => sendCommand('/api/command/kill-leader'), [sendCommand]);
    const killRandom = useCallback(() => sendCommand('/api/command/kill-random'), [sendCommand]);
    const startMission = useCallback(() => sendCommand('/api/command/start-mission'), [sendCommand]);
    const resetSwarm = useCallback(() => {
        sendCommand('/api/command/reset');
        fetchObstacles(); // Refresh obstacles after reset
    }, [sendCommand, fetchObstacles]);

    // Deploy mission waypoints
    const deployMission = useCallback((waypoints) => {
        return sendCommand('/api/mission/waypoints', 'POST', { waypoints });
    }, [sendCommand]);

    // Add obstacle
    const addObstacle = useCallback(async (obstacle) => {
        await sendCommand('/api/obstacles', 'POST', obstacle);
        fetchObstacles(); // Refresh list
    }, [sendCommand, fetchObstacles]);

    // Remove obstacle
    const removeObstacle = useCallback(async (obstacleId) => {
        await sendCommand(`/api/obstacles/${obstacleId}`, 'DELETE');
        fetchObstacles(); // Refresh list
    }, [sendCommand, fetchObstacles]);

    // Set spawn point
    const setSpawnPoint = useCallback(async (lat, lon) => {
        await sendCommand('/api/spawn-point', 'POST', { latitude: lat, longitude: lon });
    }, [sendCommand]);

    return {
        drones, missionState, eventLog, logs, trails, obstacles,
        isConnected,
        killLeader, killRandom, startMission, resetSwarm,
        deployMission, addObstacle, removeObstacle, setSpawnPoint,
    };
}
