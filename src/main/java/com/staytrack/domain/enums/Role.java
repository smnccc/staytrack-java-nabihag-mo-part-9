package com.staytrack.domain.enums;

public enum Role implements WireEnum {
    TENANT,
    ADMIN;

    @Override
    public String wire() {
        return name().toLowerCase();
    }

    public static Role fromWire(String value) {
        return value == null ? null : Role.valueOf(value.trim().toUpperCase());
    }
}
