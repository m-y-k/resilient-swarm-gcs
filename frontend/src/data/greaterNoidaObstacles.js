/**
 * Default Greater Noida obstacle data — pre-loaded on the tactical map.
 * 8 real landmarks to make the demo feel authentic.
 */
const GREATER_NOIDA_OBSTACLES = [
    {
        id: "obs_1",
        name: "Gaur City Mall",
        type: "BUILDING",
        latitude: 28.4585,
        longitude: 77.5005,
        radius: 80,
        height: 45,
        color: "#ef4444",
    },
    {
        id: "obs_2",
        name: "Pari Chowk",
        type: "RESTRICTED_ZONE",
        latitude: 28.4650,
        longitude: 77.5040,
        radius: 150,
        height: 0,
        color: "#f97316",
    },
    {
        id: "obs_3",
        name: "Gaur World SmartStreet Mall",
        type: "BUILDING",
        latitude: 28.4563,
        longitude: 77.4982,
        radius: 60,
        height: 35,
        color: "#ef4444",
    },
    {
        id: "obs_4",
        name: "JP Aman Society Tower",
        type: "BUILDING",
        latitude: 28.4620,
        longitude: 77.5060,
        radius: 50,
        height: 60,
        color: "#ef4444",
    },
    {
        id: "obs_5",
        name: "Noida-GN Expressway Overpass",
        type: "INFRASTRUCTURE",
        latitude: 28.4600,
        longitude: 77.5025,
        radius: 40,
        height: 15,
        color: "#eab308",
    },
    {
        id: "obs_6",
        name: "Cell Tower Cluster (Sector 16)",
        type: "RF_HAZARD",
        latitude: 28.4575,
        longitude: 77.5050,
        radius: 60,
        height: 50,
        color: "#a855f7",
    },
    {
        id: "obs_7",
        name: "Water Tank (Sector 12)",
        type: "STRUCTURE",
        latitude: 28.4640,
        longitude: 77.4990,
        radius: 25,
        height: 30,
        color: "#ef4444",
    },
    {
        id: "obs_8",
        name: "Tree Line (Park Belt)",
        type: "VEGETATION",
        latitude: 28.4610,
        longitude: 77.4970,
        radius: 100,
        height: 12,
        color: "#22c55e",
    },
];

/** Type-to-color mapping for obstacles. */
export const OBSTACLE_COLORS = {
    BUILDING: '#ef4444',
    RESTRICTED_ZONE: '#f97316',
    INFRASTRUCTURE: '#eab308',
    RF_HAZARD: '#a855f7',
    VEGETATION: '#22c55e',
    STRUCTURE: '#6b7280',
};

/** Type-to-icon mapping for obstacle labels. */
export const OBSTACLE_ICONS = {
    BUILDING: '🏢',
    RESTRICTED_ZONE: '🚫',
    INFRASTRUCTURE: '🌉',
    RF_HAZARD: '📡',
    VEGETATION: '🌳',
    STRUCTURE: '🏗️',
};

export default GREATER_NOIDA_OBSTACLES;
