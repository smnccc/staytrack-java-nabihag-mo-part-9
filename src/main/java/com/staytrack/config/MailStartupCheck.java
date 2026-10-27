package com.staytrack.config;

import com.staytrack.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Nagsasabi sa startup kung makakapagpadala ba ng email. */
@Component
public class MailStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MailStartupCheck.class);

    private final EmailService emailService;
    private final AppProperties props;

    public MailStartupCheck(EmailService emailService, AppProperties props) {
        this.emailService = emailService;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (emailService.isConfigured()) {
            emailService.verifyConnection();
            return;
        }

        log.warn("========================================================================");
        log.warn(" [mail] Gmail SMTP is NOT configured yet.");
        log.warn("        OTP codes will be printed in THIS console (development mode).");
        log.warn("        To email real codes, edit src/main/resources/application.properties:");
        log.warn("            spring.mail.username=youremail@gmail.com");
        log.warn("            spring.mail.password=your_16_char_app_password");
        log.warn("        -> Turn ON 2-Step Verification, then create an App Password at");
        log.warn("           https://myaccount.google.com/apppasswords");
        log.warn("        Optionally set app.dev-show-otp=false once email works.");
        log.warn("========================================================================");
    }
}
