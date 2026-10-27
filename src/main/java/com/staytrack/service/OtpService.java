package com.staytrack.service;

import com.staytrack.config.AppProperties;
import com.staytrack.domain.OtpCode;
import com.staytrack.domain.User;
import com.staytrack.domain.enums.OtpPurpose;
import com.staytrack.exception.ApiException;
import com.staytrack.repository.OtpCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Gumagawa at nagche-check ng OTP; naka-hash ang code sa database. */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository otpCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final EmailService emailService;

    public OtpService(OtpCodeRepository otpCodeRepository,
                      PasswordEncoder passwordEncoder,
                      AppProperties props,
                      EmailService emailService) {
        this.otpCodeRepository = otpCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.emailService = emailService;
    }

    /** Gumawa ng numeric code, di pwedeng mag-umpisa sa zero. */
    public String generateCode() {
        int length = props.getOtp().getLength();
        StringBuilder sb = new StringBuilder(length);
        sb.append(1 + RANDOM.nextInt(9));
        for (int i = 1; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /** Patayin ang lumang code tapos gumawa at ipadala ang bago. */
    public EmailService.OtpMailResult issue(User user, OtpPurpose purpose) {
        String code = generateCode();
        String hash = passwordEncoder.encode(code);
        Instant now = Instant.now();

        List<OtpCode> active = otpCodeRepository
            .findAllByUserIdAndPurposeAndConsumedAtIsNull(user.getId(), purpose);
        for (OtpCode existing : active) {
            existing.setConsumedAt(now);
        }
        otpCodeRepository.saveAll(active);

        OtpCode otp = new OtpCode();
        otp.setUserId(user.getId());
        otp.setCodeHash(hash);
        otp.setPurpose(purpose);
        otp.setMaxAttempts(props.getOtp().getMaxAttempts());
        otp.setExpiresAt(now.plus(Duration.ofMinutes(props.getOtp().getTtlMinutes())));
        otpCodeRepository.save(otp);

        return emailService.sendOtp(user.getEmail(), code, user.getFirstName(), purpose);
    }

    /** Oras ng huling code, para may cooldown sa resend. */
    public Optional<Instant> lastIssuedAt(UUID userId, OtpPurpose purpose) {
        return otpCodeRepository.findTopByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
            .map(OtpCode::getCreatedAt);
    }

    /** I-check ang code at gamitin agad kapag tama. */
    public void consume(UUID userId, OtpPurpose purpose, String code) {
        Optional<OtpCode> maybe = otpCodeRepository
            .findTopByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(userId, purpose);

        OtpCode otp = maybe.orElseThrow(
            () -> ApiException.badRequest("No active code found. Please request a new one."));

        if (otp.isExpired()) {
            throw ApiException.badRequest("That code has expired. Please request a new one.");
        }
        if (otp.getAttempts() >= otp.getMaxAttempts()) {
            throw ApiException.tooManyRequests("Too many incorrect attempts. Please request a new code.");
        }

        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpCodeRepository.save(otp); // commits on its own (no surrounding transaction)
            int left = otp.getMaxAttempts() - otp.getAttempts();
            throw ApiException.badRequest(left > 0
                ? "Incorrect code. " + left + " attempt(s) left."
                : "Incorrect code. Please request a new one.");
        }

        otp.setConsumedAt(Instant.now());
        otpCodeRepository.save(otp);
    }
}
