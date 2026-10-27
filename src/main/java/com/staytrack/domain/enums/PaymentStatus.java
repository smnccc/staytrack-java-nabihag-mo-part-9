package com.staytrack.domain.enums;

public enum PaymentStatus implements WireEnum {
    UNPAID,
    SUBMITTED,
    VERIFIED,
    REJECTED,
    OVERDUE;

    @Override
    public String wire() {
        return name().toLowerCase();
    }

    public static PaymentStatus fromWire(String value) {
        return value == null ? null : PaymentStatus.valueOf(value.trim().toUpperCase());
    }
}
