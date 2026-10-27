package com.staytrack.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Mga request body ng admin endpoints. */
public final class AdminRequests {

    private AdminRequests() {
    }

    public record CreateTenant(
        @NotBlank(message = "First name is required")
        @Size(max = 80, message = "First name is too long")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 80, message = "Last name is too long")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Email is too long")
        String email,

        @Size(max = 30, message = "Phone is too long")
        String phone,

        String roomId,

        LocalDate moveInDate
    ) {
    }

    public record CreateRoom(
        @NotBlank(message = "Room number is required")
        @Size(max = 20, message = "Room number is too long")
        String roomNumber,

        @Size(max = 80, message = "Building is too long")
        String building,

        Integer floor,

        BigDecimal monthlyRent,

        Integer capacity
    ) {
    }

    public record UpdateRequestBody(
        String status,
        @Size(max = 2000, message = "Admin notes are too long")
        String adminNotes
    ) {
    }

    public record CreatePayment(
        @NotBlank(message = "Tenant is required")
        String tenantId,

        LocalDate billingPeriod,

        BigDecimal rentAmount,

        BigDecimal utilitiesAmount,

        BigDecimal otherAmount,

        LocalDate dueDate
    ) {
    }

    public record GenerateBilling(
        LocalDate billingPeriod,
        LocalDate dueDate,
        BigDecimal utilitiesAmount,
        BigDecimal otherAmount
    ) {
    }

    public record VerifyPayment(
        BigDecimal amountPaid,
        @Size(max = 1000, message = "Remarks are too long")
        String remarks
    ) {
    }

    public record RejectPayment(
        @Size(max = 1000, message = "Remarks are too long")
        String remarks
    ) {
    }
}
