package com.staytrack.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mga request body ng login at signup. */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record Signup(
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

        @NotBlank(message = "Phone number is required")
        @Size(max = 30, message = "Phone number is too long")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 9, max = 128, message = "Password must be more than 8 characters")
        @Pattern(regexp = ".*[A-Z].*", message = "Password must contain at least one uppercase letter")
        String password
    ) {
    }

    public record Login(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password
    ) {
    }

    public record VerifyOtp(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email,

        @NotBlank(message = "Code is required")
        @Pattern(regexp = "\\d{4,8}", message = "Code must contain only digits")
        String code
    ) {
    }

    public record ResendOtp(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email,
        String purpose
    ) {
    }

    public record ForgotPassword(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email
    ) {
    }

    public record ResetPassword(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email,

        @NotBlank(message = "Code is required")
        @Pattern(regexp = "\\d{4,8}", message = "Code must contain only digits")
        String code,

        @NotBlank(message = "Password is required")
        @Size(min = 9, max = 128, message = "Password must be more than 8 characters")
        @Pattern(regexp = ".*[A-Z].*", message = "Password must contain at least one uppercase letter")
        String password
    ) {
    }

    public record ChangePassword(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "Password is required")
        @Size(min = 9, max = 128, message = "Password must be more than 8 characters")
        @Pattern(regexp = ".*[A-Z].*", message = "Password must contain at least one uppercase letter")
        String newPassword
    ) {
    }
}
