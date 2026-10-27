package com.staytrack.service;

import com.staytrack.config.AppProperties;
import com.staytrack.domain.Notification;
import com.staytrack.domain.Room;
import com.staytrack.domain.TenantProfile;
import com.staytrack.domain.User;
import com.staytrack.domain.enums.OtpPurpose;
import com.staytrack.domain.enums.PaymentStatus;
import com.staytrack.domain.enums.Role;
import com.staytrack.domain.enums.UserStatus;
import com.staytrack.dto.AuthRequests;
import com.staytrack.dto.DtoMapper;
import com.staytrack.exception.ApiException;
import com.staytrack.repository.NotificationRepository;
import com.staytrack.repository.PaymentRepository;
import com.staytrack.repository.TenantProfileRepository;
import com.staytrack.repository.UserRepository;
import com.staytrack.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Lahat ng tungkol sa account: signup, OTP, login, reset. */
@Service
public class AuthService {

    /** Fake hash para pantay ang oras kahit wala yung email. */
    private static final String DUMMY_HASH =
        "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final List<PaymentStatus> OUTSTANDING =
        List.of(PaymentStatus.UNPAID, PaymentStatus.SUBMITTED, PaymentStatus.OVERDUE, PaymentStatus.REJECTED);

    private final UserRepository userRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final NotificationRepository notificationRepository;
    private final PaymentRepository paymentRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final AppProperties props;

    public AuthService(UserRepository userRepository,
                       TenantProfileRepository tenantProfileRepository,
                       NotificationRepository notificationRepository,
                       PaymentRepository paymentRepository,
                       PasswordEncoder passwordEncoder,
                       OtpService otpService,
                       JwtService jwtService,
                       AppProperties props) {
        this.userRepository = userRepository;
        this.tenantProfileRepository = tenantProfileRepository;
        this.notificationRepository = notificationRepository;
        this.paymentRepository = paymentRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.props = props;
    }

    // --- Signup ---
    @Transactional
    public Map<String, Object> signup(AuthRequests.Signup req) {
        String email = req.email().trim().toLowerCase();

        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        User user;

        if (existing.isPresent()) {
            user = existing.get();
            if (user.isEmailVerified() && user.getStatus() != UserStatus.PENDING) {
                throw ApiException.conflict("An account with that email already exists. Try logging in instead.");
            }
            user.setFirstName(req.firstName().trim());
            user.setLastName(req.lastName().trim());
            user.setPhone(blankToNull(req.phone()));
            user.setPasswordHash(passwordEncoder.encode(req.password()));
            if (!user.isEmailVerified()) {
                user.setStatus(UserStatus.PENDING);
            }
            user = userRepository.save(user);
        } else {
            user = new User();
            user.setEmail(email);
            user.setFirstName(req.firstName().trim());
            user.setLastName(req.lastName().trim());
            user.setPhone(blankToNull(req.phone()));
            user.setPasswordHash(passwordEncoder.encode(req.password()));
            user.setRole(Role.TENANT);
            user.setStatus(UserStatus.PENDING);
            user.setEmailVerified(false);
            user = userRepository.save(user);
            tenantProfileRepository.save(new TenantProfile(user));
        }

        EmailService.OtpMailResult mail = otpService.issue(user, OtpPurpose.SIGNUP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Account created. Check your email for the verification code.");
        result.put("email", user.getEmail());
        result.put("requiresVerification", true);
        if (mail.devCode() != null) {
            result.put("devOtp", mail.devCode());
        }
        return result;
    }

    // --- Verify OTP (hindi transactional, tingnan sa OtpService) ---
    public Map<String, Object> verifyOtp(AuthRequests.VerifyOtp req) {
        String email = req.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> ApiException.notFound("No account found for that email."));

        if (user.isEmailVerified() && user.getStatus() == UserStatus.ACTIVE) {
            Map<String, Object> already = new LinkedHashMap<>();
            already.put("message", "Email already verified.");
            already.put("token", jwtService.generateToken(user));
            already.put("user", DtoMapper.user(user));
            return already;
        }

        otpService.consume(user.getId(), OtpPurpose.SIGNUP, req.code());

        user.setEmailVerified(true);
        if (user.getStatus() == UserStatus.PENDING) {
            user.setStatus(UserStatus.ACTIVE);
        }
        user = userRepository.save(user);

        notificationRepository.save(new Notification(
            user.getId(),
            "Welcome to StayTrack",
            "Your email is verified. You can now submit requests and pay rent.",
            "success"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Email verified successfully. Welcome to StayTrack!");
        result.put("token", jwtService.generateToken(user));
        result.put("user", DtoMapper.user(user));
        return result;
    }

    // --- Resend OTP ---
    public Map<String, Object> resendOtp(AuthRequests.ResendOtp req) {
        String email = req.email().trim().toLowerCase();
        OtpPurpose purpose = parsePurpose(req.purpose());

        Optional<User> maybe = userRepository.findByEmailIgnoreCase(email);
        if (maybe.isEmpty()) {
            // Wag ipahalata kung existing ang account.
            return messageOnly("If that account exists, a new code has been sent.");
        }

        User user = maybe.get();
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw ApiException.forbidden("This account is suspended.");
        }

        Optional<Instant> last = otpService.lastIssuedAt(user.getId(), purpose);
        if (last.isPresent()) {
            long elapsed = Duration.between(last.get(), Instant.now()).toSeconds();
            int cooldown = props.getOtp().getResendCooldownSeconds();
            if (elapsed < cooldown) {
                throw ApiException.tooManyRequests(
                    "Please wait " + (cooldown - elapsed) + "s before requesting another code.");
            }
        }

        EmailService.OtpMailResult mail = otpService.issue(user, purpose);

        Map<String, Object> result = messageOnly("A new verification code has been sent to your email.");
        if (mail.devCode() != null) {
            result.put("devOtp", mail.devCode());
        }
        return result;
    }

    // --- Login ---
    @Transactional
    public Map<String, Object> login(AuthRequests.Login req) {
        String email = req.email().trim().toLowerCase();
        Optional<User> maybeUser = userRepository.findByEmailIgnoreCase(email);

        boolean matches;
        if (maybeUser.isPresent()) {
            matches = passwordEncoder.matches(req.password(), maybeUser.get().getPasswordHash());
        } else {
            passwordEncoder.matches(req.password(), DUMMY_HASH);
            matches = false;
        }

        if (maybeUser.isEmpty() || !matches) {
            throw ApiException.unauthorized("Incorrect email or password.");
        }

        User user = maybeUser.get();

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw ApiException.forbidden("Your account has been suspended. Please contact the dormitory admin.");
        }
        if (!user.isEmailVerified()) {
            throw ApiException.forbidden("Your email is not verified yet.",
                Map.of("code", "EMAIL_NOT_VERIFIED", "email", user.getEmail()));
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("Your account is not active. Please contact the dormitory admin.");
        }

        user.setLastLoginAt(Instant.now());
        user = userRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Logged in successfully.");
        result.put("token", jwtService.generateToken(user));
        result.put("user", DtoMapper.user(user));
        return result;
    }

    // --- Me ---
    @Transactional(readOnly = true)
    public Map<String, Object> me(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("Account no longer exists"));

        Room room = user.getProfile() == null ? null : user.getProfile().getRoom();
        BigDecimal outstanding = paymentRepository.sumOutstandingForTenant(userId, OUTSTANDING);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", DtoMapper.me(user, room, outstanding));
        return result;
    }

    // --- Password reset ---
    public Map<String, Object> forgotPassword(AuthRequests.ForgotPassword req) {
        String email = req.email().trim().toLowerCase();
        Optional<User> maybe = userRepository.findByEmailIgnoreCase(email);

        Map<String, Object> result = messageOnly(
            "If that email is registered, a password reset code has been sent.");

        if (maybe.isPresent() && maybe.get().isEmailVerified()) {
            EmailService.OtpMailResult mail = otpService.issue(maybe.get(), OtpPurpose.PASSWORD_RESET);
            if (mail.devCode() != null) {
                result.put("devOtp", mail.devCode());
            }
        }
        return result;
    }

    public Map<String, Object> resetPassword(AuthRequests.ResetPassword req) {
        String email = req.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> ApiException.notFound("No account found for that email."));

        otpService.consume(user.getId(), OtpPurpose.PASSWORD_RESET, req.code());

        user.setPasswordHash(passwordEncoder.encode(req.password()));
        userRepository.save(user);

        return messageOnly("Password reset successfully. You can now log in.");
    }

    @Transactional
    public Map<String, Object> changePassword(UUID userId, AuthRequests.ChangePassword req) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("Account no longer exists"));

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Your current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        return messageOnly("Password updated successfully.");
    }

    // --- helpers ---
    private Map<String, Object> messageOnly(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", message);
        return result;
    }

    private OtpPurpose parsePurpose(String raw) {
        if (raw == null || raw.isBlank()) {
            return OtpPurpose.SIGNUP;
        }
        try {
            return OtpPurpose.fromWire(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("purpose must be one of: signup, password_reset, login");
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
