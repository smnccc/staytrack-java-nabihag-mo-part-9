package com.staytrack.controller;

import com.staytrack.domain.User;
import com.staytrack.dto.AuthRequests;
import com.staytrack.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody AuthRequests.Signup req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(req));
    }

    @PostMapping("/verify-otp")
    public Map<String, Object> verifyOtp(@Valid @RequestBody AuthRequests.VerifyOtp req) {
        return authService.verifyOtp(req);
    }

    @PostMapping("/resend-otp")
    public Map<String, Object> resendOtp(@Valid @RequestBody AuthRequests.ResendOtp req) {
        return authService.resendOtp(req);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody AuthRequests.Login req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal User user) {
        return authService.me(user.getId());
    }

    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@Valid @RequestBody AuthRequests.ForgotPassword req) {
        return authService.forgotPassword(req);
    }

    @PostMapping("/reset-password")
    public Map<String, Object> resetPassword(@Valid @RequestBody AuthRequests.ResetPassword req) {
        return authService.resetPassword(req);
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@AuthenticationPrincipal User user,
                                              @Valid @RequestBody AuthRequests.ChangePassword req) {
        return authService.changePassword(user.getId(), req);
    }
}
