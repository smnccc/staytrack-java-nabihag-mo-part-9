package com.staytrack.config;

import com.staytrack.domain.User;
import com.staytrack.domain.enums.Role;
import com.staytrack.domain.enums.UserStatus;
import com.staytrack.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Gumagawa ng admin account sa first run, walang demo data. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppProperties props;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public DataSeeder(AppProperties props,
                      PasswordEncoder passwordEncoder,
                      UserRepository userRepository) {
        this.props = props;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        AppProperties.Seed seed = props.getSeed();
        if (!seed.isEnabled()) {
            return;
        }

        String adminEmail = seed.getAdminEmail() == null ? "" : seed.getAdminEmail().trim().toLowerCase();
        String adminPassword = seed.getAdminPassword() == null ? "" : seed.getAdminPassword();

        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("app.seed.admin-email / app.seed.admin-password are blank — no admin account created.");
            return;
        }

        User admin = userRepository.findByEmailIgnoreCase(adminEmail).orElse(null);
        if (admin != null) {
            log.info("Admin account {} already exists — leaving it unchanged.", adminEmail);
            return;
        }

        admin = new User();
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFirstName("Dorm");
        admin.setLastName("Admin");
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setEmailVerified(true);
        userRepository.save(admin);

        log.info("Admin account created: {}", adminEmail);
    }
}
