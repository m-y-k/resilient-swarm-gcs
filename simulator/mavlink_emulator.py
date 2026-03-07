"""
MAVLink-style packet wrapper — cosmetic defense domain polish.

Wraps telemetry data in MAVLink v2 packet structure (header + payload).
This is NOT real MAVLink, but mimics the protocol format to demonstrate
awareness of the MAVLink standard used in real drone systems.

Reference: https://mavlink.io/en/guide/serialization.html
"""

# MAVLink v2 message IDs
MSGID_HEARTBEAT = 0
MSGID_GLOBAL_POSITION_INT = 33
MSGID_ATTITUDE = 30

# MAV_TYPE constants
MAV_TYPE = {
    "SURVEILLANCE": 2,   # MAV_TYPE_QUADROTOR
    "LOGISTICS": 10,     # MAV_TYPE_GROUND_ROVER
    "STRIKE": 2,         # MAV_TYPE_QUADROTOR (armed variant)
}

# MAV_AUTOPILOT
MAV_AUTOPILOT_ARDUPILOT = 3

# MAV_STATE
MAV_STATE = {
    "ACTIVE": 4,     # MAV_STATE_ACTIVE
    "STALE": 5,      # MAV_STATE_CRITICAL
    "LOST": 6,       # MAV_STATE_EMERGENCY
}

# Base mode flags
BASE_MODE_ARMED = 128
BASE_MODE_GUIDED = 8
BASE_MODE_CUSTOM = 1
DEFAULT_BASE_MODE = BASE_MODE_ARMED | BASE_MODE_GUIDED | BASE_MODE_CUSTOM  # 137


def create_heartbeat_packet(drone_id, drone_type, status, sequence=0):
    """
    Create a MAVLink v2 HEARTBEAT-style packet.

    Args:
        drone_id:   int, system ID (1-10)
        drone_type: str, one of 'SURVEILLANCE', 'LOGISTICS', 'STRIKE'
        status:     str, one of 'ACTIVE', 'STALE', 'LOST'
        sequence:   int, packet sequence number

    Returns:
        dict mimicking MAVLink v2 serialization format
    """
    payload = {
        "type": MAV_TYPE.get(drone_type, 2),
        "autopilot": MAV_AUTOPILOT_ARDUPILOT,
        "base_mode": DEFAULT_BASE_MODE if status != "LOST" else 0,
        "custom_mode": 4 if status == "ACTIVE" else 0,  # 4 = GUIDED mode
        "system_status": MAV_STATE.get(status, 4),
        "mavlink_version": 3,
    }

    return {
        "header": {
            "stx": 253,             # MAVLink v2 start byte (0xFD)
            "len": len(str(payload)),
            "incompat_flags": 0,
            "compat_flags": 0,
            "seq": sequence % 256,
            "sysid": drone_id,
            "compid": 1,            # Flight controller component
            "msgid": MSGID_HEARTBEAT,
        },
        "payload": payload,
    }


def create_position_packet(drone_id, lat, lon, alt, heading, speed, sequence=0):
    """
    Create a MAVLink v2 GLOBAL_POSITION_INT-style packet.

    Args:
        drone_id: int, system ID
        lat:      float, latitude in degrees
        lon:      float, longitude in degrees
        alt:      float, altitude in meters
        heading:  float, heading in degrees (0-360)
        speed:    float, ground speed in m/s
        sequence: int, packet sequence number

    Returns:
        dict mimicking MAVLink v2 GLOBAL_POSITION_INT message
    """
    payload = {
        "lat": int(lat * 1e7),      # degE7 format (standard MAVLink)
        "lon": int(lon * 1e7),      # degE7 format
        "alt": int(alt * 1000),      # mm (standard MAVLink)
        "relative_alt": int(alt * 1000),
        "vx": int(speed * 100),      # cm/s
        "vy": 0,
        "vz": 0,
        "hdg": int(heading * 100),   # cdeg (centidegrees)
    }

    return {
        "header": {
            "stx": 253,
            "len": len(str(payload)),
            "incompat_flags": 0,
            "compat_flags": 0,
            "seq": sequence % 256,
            "sysid": drone_id,
            "compid": 1,
            "msgid": MSGID_GLOBAL_POSITION_INT,
        },
        "payload": payload,
    }


def wrap_telemetry(drone_id, drone_type, status, lat, lon, alt, heading, speed, battery, sequence):
    """
    Wrap a full telemetry update as a bundle of MAVLink-style packets.
    Returns a list of [heartbeat, position] packets — suitable for
    serialization to JSON and POSTing to the backend.

    This is the main entry point for the emulator.
    """
    return {
        "mavlink_version": 2,
        "system_id": drone_id,
        "packets": [
            create_heartbeat_packet(drone_id, drone_type, status, sequence),
            create_position_packet(drone_id, lat, lon, alt, heading, speed, sequence),
        ],
        # Also include the flat telemetry for backend compatibility
        "telemetry": {
            "systemId": drone_id,
            "componentId": 1,
            "sequenceNumber": sequence,
            "messageType": "HEARTBEAT",
            "latitude": lat,
            "longitude": lon,
            "altitude": alt,
            "heading": heading,
            "speed": speed,
            "batteryPercent": round(battery, 2),
        },
    }
