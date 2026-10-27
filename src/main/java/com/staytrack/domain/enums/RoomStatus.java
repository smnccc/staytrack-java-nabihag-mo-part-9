package com.staytrack.domain.enums;

public enum RoomStatus implements WireEnum {
    AVAILABLE,
    OCCUPIED,
    MAINTENANCE;

    @Override
    public String wire() {
        return name().toLowerCase();
    }

    public static RoomStatus fromWire(String value) {
        return value == null ? null : RoomStatus.valueOf(value.trim().toUpperCase());
    }
}
