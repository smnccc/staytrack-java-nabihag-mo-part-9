package com.staytrack.domain.enums;

public enum UserStatus implements WireEnum {
    PENDING,
    ACTIVE,
    SUSPENDED,
    REJECTED;

    @Override
    public String wire() {
        return name().toLowerCase();
    }

    public static UserStatus fromWire(String value) {
        return value == null ? null : UserStatus.valueOf(value.trim().toUpperCase());
    }
}
