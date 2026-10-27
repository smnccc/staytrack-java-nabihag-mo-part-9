package com.staytrack.domain.enums;

public enum RequestType implements WireEnum {
    MAINTENANCE,
    NEIGHBOR;

    @Override
    public String wire() {
        return name().toLowerCase();
    }

    public static RequestType fromWire(String value) {
        return value == null ? null : RequestType.valueOf(value.trim().toUpperCase());
    }
}
