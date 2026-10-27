package com.staytrack.dto;

import jakarta.validation.constraints.Size;

/** Mga request body ng tenant endpoints. */
public final class ProfileRequests {

    private ProfileRequests() {
    }

    public record UpdateProfile(
        @Size(max = 80, message = "First name is too long")
        String firstName,

        @Size(max = 80, message = "Last name is too long")
        String lastName,

        @Size(max = 30, message = "Phone is too long")
        String phone,

        @Size(max = 120, message = "Emergency contact name is too long")
        String emergencyContactName,

        @Size(max = 30, message = "Emergency contact phone is too long")
        String emergencyContactPhone,

        @Size(max = 60, message = "School ID is too long")
        String schoolId
    ) {
    }
}
