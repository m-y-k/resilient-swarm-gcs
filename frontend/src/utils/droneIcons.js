/**
 * SVG drone icons and role-based color helpers.
 */

export const ROLE_COLORS = {
    LEADER: '#3b82f6',
    FOLLOWER: '#22c55e',
    CANDIDATE: '#f97316',
    LOST: '#ef4444',
};

export const TYPE_LABELS = {
    SURVEILLANCE: '🔍',
    LOGISTICS: '📦',
    STRIKE: '⚔️',
};

export const STATUS_COLORS = {
    ACTIVE: '#22c55e',
    STALE: '#eab308',
    LOST: '#ef4444',
};

export function getRoleClass(role) {
    return (role || '').toLowerCase();
}

export function getStatusColor(status) {
    return STATUS_COLORS[status] || STATUS_COLORS.ACTIVE;
}

export function getRoleColor(role) {
    return ROLE_COLORS[role] || ROLE_COLORS.FOLLOWER;
}

/**
 * Create a Leaflet DivIcon HTML string for a drone marker.
 */
export function createDroneIconHtml(drone) {
    const color = getRoleColor(drone.role);
    const size = drone.role === 'LEADER' ? 28 : 22;
    const cls = getRoleClass(drone.role);
    const rotation = drone.heading || 0;

    return `
    <div class="drone-marker ${cls}" style="
      width: ${size}px;
      height: ${size}px;
      border-color: ${color};
      position: relative;
    ">
      <svg viewBox="0 0 24 24" width="${size - 8}" height="${size - 8}" 
           style="transform: rotate(${rotation}deg); fill: ${color};">
        <path d="M12 2L4 20h16L12 2z"/>
      </svg>
    </div>
  `;
}
