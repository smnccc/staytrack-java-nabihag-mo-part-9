package com.staytrack.domain.enums;

public enum RequestStatus implements WireEnum {
    PENDING,
    IN_PROGRESS,
    RESOLVED,
    REJECTED,
    CANCELLED;

    @Override
    public String wire() {
        return name().toLowerCase();
    }

    public static RequestStatus fromWire(String value) {
        return value == null ? null : RequestStatus.valueOf(value.trim().toUpperCase());
    }
}
