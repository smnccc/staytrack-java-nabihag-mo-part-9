package com.staytrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Kinukuha lahat ng "app.*" settings galing application.properties. */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Otp otp = new Otp();
    private Mail mail = new Mail();
    private Seed seed = new Seed();
    private boolean devShowOtp = true;
    private String uploadDir = "uploads";
    private long maxUploadMb = 5;
    private String[] corsOrigins = new String[0];

    public static class Jwt {
        private String secret;
        private int expirationDays = 7;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getExpirationDays() {
            return expirationDays;
        }

        public void setExpirationDays(int expirationDays) {
            this.expirationDays = expirationDays;
        }
    }

    public static class Otp {
        private int length = 6;
        private int ttlMinutes = 10;
        private int maxAttempts = 5;
        private int resendCooldownSeconds = 60;

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public int getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getResendCooldownSeconds() {
            return resendCooldownSeconds;
        }

        public void setResendCooldownSeconds(int resendCooldownSeconds) {
            this.resendCooldownSeconds = resendCooldownSeconds;
        }
    }

    public static class Mail {
        private String fromName = "StayTrack";
        private String fromEmail;

        public String getFromName() {
            return fromName;
        }

        public void setFromName(String fromName) {
            this.fromName = fromName;
        }

        public String getFromEmail() {
            return fromEmail;
        }

        public void setFromEmail(String fromEmail) {
            this.fromEmail = fromEmail;
        }
    }

    public static class Seed {
        private boolean enabled = true;
        private String adminEmail = "admin@staytrack.local";
        private String adminPassword = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAdminEmail() {
            return adminEmail;
        }

        public void setAdminEmail(String adminEmail) {
            this.adminEmail = adminEmail;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }
    }

    // --- getters / setters ---

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Otp getOtp() {
        return otp;
    }

    public void setOtp(Otp otp) {
        this.otp = otp;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public Seed getSeed() {
        return seed;
    }

    public void setSeed(Seed seed) {
        this.seed = seed;
    }

    public boolean isDevShowOtp() {
        return devShowOtp;
    }

    public void setDevShowOtp(boolean devShowOtp) {
        this.devShowOtp = devShowOtp;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public long getMaxUploadMb() {
        return maxUploadMb;
    }

    public void setMaxUploadMb(long maxUploadMb) {
        this.maxUploadMb = maxUploadMb;
    }

    public String[] getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(String[] corsOrigins) {
        this.corsOrigins = corsOrigins;
    }
}
