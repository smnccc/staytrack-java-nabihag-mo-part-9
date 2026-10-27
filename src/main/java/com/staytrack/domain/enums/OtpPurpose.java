package com.staytrack.domain.enums;

public enum OtpPurpose implements WireEnum {
    SIGNUP,
    PASSWORD_RESET,
    LOGIN;

    @Override
    public String wire() {
        return name().toLowerCase();
    }

    public static OtpPurpose fromWire(String value) {
        return value == null ? null : OtpPurpose.valueOf(value.trim().toUpperCase());
    }
}
