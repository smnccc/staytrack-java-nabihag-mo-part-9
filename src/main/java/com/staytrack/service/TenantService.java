package com.staytrack.service;

import com.staytrack.domain.Room;
import com.staytrack.domain.TenantProfile;
import com.staytrack.domain.User;
import com.staytrack.dto.ProfileRequests;
import com.staytrack.exception.ApiException;
import com.staytrack.repository.TenantProfileRepository;
import com.staytrack.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Profile na si tenant mismo ang nag-e-edit. */
@Service
public class TenantService {

    private final UserRepository userRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final StorageService storageService;

    public TenantService(UserRepository userRepository,
                         TenantProfileRepository tenantProfileRepository,
                         StorageService storageService) {
        this.userRepository = userRepository;
        this.tenantProfileRepository = tenantProfileRepository;
        this.storageService = storageService;
    }

    /** Palitan o i-upload ang profile picture ng tenant. */
    @Transactional
    public Map<String, Object> updateAvatar(UUID userId, MultipartFile avatar) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("Profile not found"));

        String url = storageService.store(avatar, "avatars");
        if (url == null) {
            throw ApiException.badRequest("Please choose an image to upload.");
        }

        user.setAvatarUrl(url);
        userRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Profile photo updated.");
        result.put("avatarUrl", url);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProfile(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("Profile not found"));
        TenantProfile profile = tenantProfileRepository.findByUserId(userId).orElse(null);
        Room room = profile == null ? null : profile.getRoom();

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", user.getId());
        p.put("email", user.getEmail());
        p.put("firstName", user.getFirstName());
        p.put("lastName", user.getLastName());
        p.put("phone", user.getPhone());
        p.put("status", user.getStatus().wire());
        p.put("emailVerified", user.isEmailVerified());
        p.put("schoolId", profile == null ? null : profile.getSchoolId());
        p.put("moveInDate", profile == null ? null : profile.getMoveInDate());
        p.put("emergencyContactName", profile == null ? null : profile.getEmergencyContactName());
        p.put("emergencyContactPhone", profile == null ? null : profile.getEmergencyContactPhone());
        p.put("contractUrl", profile == null ? null : profile.getContractUrl());

        if (room != null) {
            Map<String, Object> roomMap = new LinkedHashMap<>();
            roomMap.put("id", room.getId());
            roomMap.put("roomNumber", room.getRoomNumber());
            roomMap.put("building", room.getBuilding());
            roomMap.put("monthlyRent", room.getMonthlyRent());
            p.put("room", roomMap);
        } else {
            p.put("room", null);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", p);
        return result;
    }

    @Transactional
    public Map<String, Object> updateProfile(UUID userId, ProfileRequests.UpdateProfile req) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("Profile not found"));

        if (req.firstName() != null && !req.firstName().isBlank()) user.setFirstName(req.firstName().trim());
        if (req.lastName() != null && !req.lastName().isBlank()) user.setLastName(req.lastName().trim());
        if (req.phone() != null) user.setPhone(req.phone().isBlank() ? null : req.phone().trim());
        userRepository.save(user);

        TenantProfile profile = tenantProfileRepository.findByUserId(userId)
            .orElseGet(() -> new TenantProfile(user));
        if (req.emergencyContactName() != null) {
            profile.setEmergencyContactName(blankToNull(req.emergencyContactName()));
        }
        if (req.emergencyContactPhone() != null) {
            profile.setEmergencyContactPhone(blankToNull(req.emergencyContactPhone()));
        }
        if (req.schoolId() != null) {
            profile.setSchoolId(blankToNull(req.schoolId()));
        }
        tenantProfileRepository.save(profile);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Profile updated successfully.");
        return result;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
