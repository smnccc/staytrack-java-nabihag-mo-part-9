package com.staytrack.service;

import com.staytrack.config.AppProperties;
import com.staytrack.domain.enums.OtpPurpose;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** Nagpapadala ng email gamit Gmail, tapos log na lang kung wala pang setup. */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final AppProperties props;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    public EmailService(JavaMailSender mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    /** Resulta ng send; nasa dev mode lang yung devCode. */
    public record OtpMailResult(boolean delivered, String devCode) {
    }

    public boolean isConfigured() {
        return smtpUsername != null && !smtpUsername.isBlank()
            && smtpPassword != null && !smtpPassword.isBlank()
            && props.getMail() != null;
    }

    public OtpMailResult sendOtp(String to, String code, String firstName, OtpPurpose purpose) {
        if (!isConfigured()) {
            log.info("");
            log.info("=========================================================");
            log.info("  [DEV OTP]  {}  ->  {}", to, code);
            log.info("=========================================================");
            log.info("");
            return new OtpMailResult(false, props.isDevShowOtp() ? code : null);
        }

        String fromEmail = (props.getMail().getFromEmail() == null || props.getMail().getFromEmail().isBlank())
            ? smtpUsername
            : props.getMail().getFromEmail();

        String subject = purpose == OtpPurpose.PASSWORD_RESET
            ? "StayTrack password reset code: " + code
            : "Your StayTrack verification code: " + code;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, props.getMail().getFromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(
                "Your StayTrack code is " + code + ". It expires in "
                    + props.getOtp().getTtlMinutes() + " minutes.",
                otpEmailHtml(code, firstName, purpose));
            mailSender.send(message);
            return new OtpMailResult(true, null);
        } catch (Exception ex) {
            log.error("Could not send OTP email to {}: {}", to, ex.getMessage());
            String hint = gmailHint(ex.getMessage());
            if (hint != null) {
                log.error("[mail] {}", hint);
            }
            return new OtpMailResult(false, props.isDevShowOtp() ? code : null);
        }
    }

    /** Resulta ng ibang email, gaya ng rent reminder. */
    public record ReminderResult(boolean delivered) {
    }

    /** Ipadala sa tenant ang reminder na malapit na o lagpas na ang rent. */
    public ReminderResult sendRentDueReminder(String to,
                                              String firstName,
                                              String amount,
                                              String dueDate,
                                              String roomNumber,
                                              boolean overdue) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : firstName;
        String subject = overdue
            ? "StayTrack: rent payment overdue (" + amount + ")"
            : "StayTrack: rent of " + amount + " is due soon";
        String roomLine = (roomNumber == null || roomNumber.isBlank()) ? "" : " for Room " + roomNumber;
        String text = "Hi " + name + ", this is a reminder that your rent" + roomLine
            + " of " + amount + " is due on " + dueDate + ".";
        String html = reminderHtml(name, amount, dueDate, roomNumber, overdue);

        if (!isConfigured()) {
            log.warn("[DEV EMAIL] Rent reminder for {} -> {} due {}{}",
                to, amount, dueDate, overdue ? " (OVERDUE)" : "");
            return new ReminderResult(false);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail(), props.getMail().getFromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
            return new ReminderResult(true);
        } catch (Exception ex) {
            log.error("Could not send rent reminder to {}: {}", to, ex.getMessage());
            String hint = gmailHint(ex.getMessage());
            if (hint != null) {
                log.error("[mail] {}", hint);
            }
            return new ReminderResult(false);
        }
    }

    private String fromEmail() {
        String configured = props.getMail().getFromEmail();
        return (configured == null || configured.isBlank()) ? smtpUsername : configured;
    }

    private String reminderHtml(String name, String amount, String dueDate,
                                String roomNumber, boolean overdue) {
        String roomLine = (roomNumber == null || roomNumber.isBlank())
            ? ""
            : "<p style=\"margin:0 0 6px;color:#a89d93;font-size:14px;\">Room " + roomNumber + "</p>";
        String heading = overdue ? "Your rent is overdue" : "Rent due soon";
        String lead = overdue
            ? "Our records show this payment is past its due date."
            : "This is a friendly reminder that your rent is almost due.";
        return "<!doctype html><html><body style=\"margin:0;background:#0d0b0a;"
            + "font-family:Inter,Arial,sans-serif;color:#f6efe8;\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:32px 12px;\">"
            + "<tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"max-width:460px;background:#16120f;border:1px solid rgba(255,255,255,.09);"
            + "border-radius:22px;overflow:hidden;\">"
            + "<tr><td style=\"padding:28px 30px 8px;font-size:20px;font-weight:700;\">"
            + "Stay<span style=\"color:#ff5a1f;font-style:italic;\">Track</span></td></tr>"
            + "<tr><td style=\"padding:8px 30px 0;\">"
            + "<h1 style=\"margin:0 0 8px;font-size:20px;font-weight:600;\">" + heading + "</h1>"
            + "<p style=\"margin:0 0 18px;color:#a89d93;font-size:14px;line-height:1.6;\">Hi " + name
            + ", " + lead + "</p>" + roomLine
            + "</td></tr>"
            + "<tr><td style=\"padding:0 30px 6px;\">"
            + "<div style=\"background:#ff5a1f;color:#fff;text-align:center;border-radius:16px;"
            + "padding:20px 10px;font-size:30px;font-weight:700;\">" + amount + "</div>"
            + "<p style=\"margin:14px 0 0;color:#f6efe8;text-align:center;font-size:14px;\">Due " + dueDate
            + "</p>"
            + "</td></tr>"
            + "<tr><td style=\"padding:22px 30px 30px;\">"
            + "<p style=\"margin:0;color:#6f645a;font-size:12px;line-height:1.6;\">"
            + "Please settle this through your StayTrack Pay page, or contact the dormitory admin "
            + "if you have already paid.</p>"
            + "</td></tr></table>"
            + "<p style=\"color:#6f645a;font-size:11px;margin:16px 0 0;\">"
            + "StayTrack · Dormitory Management System</p>"
            + "</td></tr></table></body></html>";
    }

    /** Check lang kung gumagana ang email pag startup. */
    public boolean verifyConnection() {
        if (!isConfigured()) {
            return false;
        }
        try {
            ((JavaMailSenderImpl) mailSender).testConnection();
            log.info("[mail] SMTP connection verified — OTP emails are ready to send.");
            return true;
        } catch (Exception ex) {
            log.error("[mail] SMTP verification failed: {}", ex.getMessage());
            String hint = gmailHint(ex.getMessage());
            if (hint != null) {
                log.error("[mail] {}", hint);
            }
            return false;
        }
    }

    /** Ginagawang madaling intindihin yung mga error ng Gmail. */
    private String gmailHint(String message) {
        if (message == null) {
            return null;
        }
        String m = message.toLowerCase();
        if (m.contains("535") || m.contains("username and password not accepted") || m.contains("badcredentials")) {
            return "Gmail rejected the login. Put a 16-character Google App Password in "
                + "spring.mail.password (not your normal password) and make sure 2-Step Verification is ON.";
        }
        if (m.contains("534")) {
            return "2-Step Verification is not enabled on that Google account, so no App Password can exist yet.";
        }
        if (m.contains("unknownhost") || m.contains("connect") || m.contains("timeout")) {
            return "Could not reach smtp.gmail.com — check your internet connection or firewall.";
        }
        return null;
    }

    private String otpEmailHtml(String code, String firstName, OtpPurpose purpose) {
        String label = purpose == OtpPurpose.PASSWORD_RESET ? "Reset your password" : "Verify your email";
        String name = (firstName == null || firstName.isBlank()) ? "there" : firstName;
        return "<!doctype html><html><body style=\"margin:0;background:#0d0b0a;"
            + "font-family:Inter,Arial,sans-serif;color:#f6efe8;\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding:32px 12px;\">"
            + "<tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"max-width:460px;background:#16120f;border:1px solid rgba(255,255,255,.09);"
            + "border-radius:22px;overflow:hidden;\">"
            + "<tr><td style=\"padding:28px 30px 8px;font-size:20px;font-weight:700;\">"
            + "Stay<span style=\"color:#ff5a1f;font-style:italic;\">Track</span></td></tr>"
            + "<tr><td style=\"padding:8px 30px 0;\">"
            + "<h1 style=\"margin:0 0 8px;font-size:20px;font-weight:600;\">" + label + "</h1>"
            + "<p style=\"margin:0 0 22px;color:#a89d93;font-size:14px;line-height:1.6;\">Hi " + name
            + ", use the code below to continue. It expires in " + props.getOtp().getTtlMinutes() + " minutes.</p>"
            + "</td></tr>"
            + "<tr><td style=\"padding:0 30px 6px;\">"
            + "<div style=\"background:#ff5a1f;color:#fff;text-align:center;border-radius:16px;"
            + "padding:20px 10px;font-size:34px;font-weight:700;letter-spacing:8px;\">" + code + "</div>"
            + "</td></tr>"
            + "<tr><td style=\"padding:22px 30px 30px;\">"
            + "<p style=\"margin:0;color:#6f645a;font-size:12px;line-height:1.6;\">"
            + "If you did not request this code, you can safely ignore this email. "
            + "Never share this code with anyone.</p>"
            + "</td></tr></table>"
            + "<p style=\"color:#6f645a;font-size:11px;margin:16px 0 0;\">"
            + "StayTrack · Dormitory Management System</p>"
            + "</td></tr></table></body></html>";
    }
}
