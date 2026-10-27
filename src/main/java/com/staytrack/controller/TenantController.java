package com.staytrack.controller;

import com.staytrack.domain.User;
import com.staytrack.dto.ProfileRequests;
import com.staytrack.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/tenant")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/profile")
    public Map<String, Object> getProfile(@AuthenticationPrincipal User user) {
        return tenantService.getProfile(user.getId());
    }

    @PatchMapping("/profile")
    public Map<String, Object> updateProfile(@AuthenticationPrincipal User user,
                                             @Valid @RequestBody ProfileRequests.UpdateProfile req) {
        return tenantService.updateProfile(user.getId(), req);
    }

    @PostMapping(value = "/avatar", consumes = {"multipart/form-data"})
    public Map<String, Object> updateAvatar(@AuthenticationPrincipal User user,
                                            @RequestParam(value = "avatar", required = false) MultipartFile avatar) {
        return tenantService.updateAvatar(user.getId(), avatar);
    }
}
