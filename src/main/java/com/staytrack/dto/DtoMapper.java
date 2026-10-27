package com.staytrack.dto;

import com.staytrack.domain.Payment;
import com.staytrack.domain.Room;
import com.staytrack.domain.ServiceRequest;
import com.staytrack.domain.TenantProfile;
import com.staytrack.domain.User;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dito binubuo yung JSON na binabasa ng front-end. */
public final class DtoMapper {

    private DtoMapper() {
    }

    private static Map<String, Object> map() {
        return new LinkedHashMap<>();
    }

    /** Simpleng user info para sa login at sa me. */
    public static Map<String, Object> user(User u) {
        Map<String, Object> m = map();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("firstName", u.getFirstName());
        m.put("lastName", u.getLastName());
        m.put("phone", u.getPhone());
        m.put("avatarUrl", u.getAvatarUrl());
        m.put("role", u.getRole().wire());
        m.put("status", u.getStatus().wire());
        m.put("emailVerified", u.isEmailVerified());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }

    /** Kumpletong profile para sa GET /api/auth/me. */
    public static Map<String, Object> me(User u, Room room, BigDecimal outstandingBalance) {
        Map<String, Object> m = user(u);
        m.put("roomNumber", room == null ? null : room.getRoomNumber());
        m.put("building", room == null ? null : room.getBuilding());
        m.put("monthlyRent", room == null ? null : room.getMonthlyRent());
        TenantProfile profile = u.getProfile();
        m.put("moveInDate", profile == null ? null : profile.getMoveInDate());
        m.put("contractUrl", profile == null ? null : profile.getContractUrl());
        m.put("outstandingBalance", outstandingBalance == null ? BigDecimal.ZERO : outstandingBalance);
        return m;
    }

    public static Map<String, Object> payment(Payment p) {
        Map<String, Object> m = map();
        m.put("id", p.getId());
        m.put("tenantId", p.getTenant() == null ? null : p.getTenant().getId());
        m.put("tenantName", p.getTenant() == null ? null : p.getTenant().fullNameTrimmed());
        Room room = p.getRoom();
        m.put("roomNumber", room == null ? null : room.getRoomNumber());
        m.put("billingPeriod", p.getBillingPeriod());
        m.put("rentAmount", nz(p.getRentAmount()));
        m.put("utilitiesAmount", nz(p.getUtilitiesAmount()));
        m.put("otherAmount", nz(p.getOtherAmount()));
        m.put("totalAmount", nz(p.getTotalAmount()));
        m.put("amountPaid", nz(p.getAmountPaid()));
        m.put("balance", nz(p.balance()));
        m.put("status", p.getStatus().wire());
        m.put("dueDate", p.getDueDate());
        m.put("proofUrl", p.getProofUrl());
        m.put("referenceNo", p.getReferenceNo());
        m.put("remarks", p.getRemarks());
        m.put("submittedAt", p.getSubmittedAt());
        m.put("verifiedAt", p.getVerifiedAt());
        m.put("createdAt", p.getCreatedAt());
        return m;
    }

    public static Map<String, Object> serviceRequest(ServiceRequest r, boolean hideAnonymousIdentity) {
        Map<String, Object> m = map();
        m.put("id", r.getId());
        m.put("type", r.getType().wire());
        m.put("category", r.getCategory());
        m.put("description", r.getDescription());
        m.put("photoUrl", r.getPhotoUrl());
        m.put("isAnonymous", r.isAnonymous());
        m.put("status", r.getStatus().wire());
        m.put("adminNotes", r.getAdminNotes());
        m.put("resolvedAt", r.getResolvedAt());
        m.put("createdAt", r.getCreatedAt());
        m.put("updatedAt", r.getUpdatedAt());

        Map<String, Object> tenant = map();
        if (r.isAnonymous() && hideAnonymousIdentity) {
            tenant.put("id", null);
            tenant.put("name", "Anonymous");
            tenant.put("roomNumber", null);
        } else {
            User t = r.getTenant();
            tenant.put("id", t == null ? null : t.getId());
            tenant.put("name", t == null ? null : t.fullNameTrimmed());
            tenant.put("roomNumber", roomNumberOf(t));
        }
        m.put("tenant", tenant);
        return m;
    }

    public static Map<String, Object> room(Room r, long occupants) {
        Map<String, Object> m = map();
        m.put("id", r.getId());
        m.put("roomNumber", r.getRoomNumber());
        m.put("building", r.getBuilding());
        m.put("floor", r.getFloor());
        m.put("monthlyRent", nz(r.getMonthlyRent()));
        m.put("capacity", r.getCapacity());
        m.put("status", r.getStatus().wire());
        m.put("occupants", occupants);
        return m;
    }

    /** Isang tenant sa listahan ng admin. */
    public static Map<String, Object> tenantRow(User u, BigDecimal outstandingBalance) {
        Map<String, Object> m = map();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("firstName", u.getFirstName());
        m.put("lastName", u.getLastName());
        m.put("phone", u.getPhone());
        m.put("status", u.getStatus().wire());
        m.put("emailVerified", u.isEmailVerified());
        TenantProfile profile = u.getProfile();
        Room room = profile == null ? null : profile.getRoom();
        m.put("roomId", room == null ? null : room.getId());
        m.put("roomNumber", room == null ? null : room.getRoomNumber());
        m.put("building", room == null ? null : room.getBuilding());
        m.put("monthlyRent", room == null ? null : room.getMonthlyRent());
        m.put("moveInDate", profile == null ? null : profile.getMoveInDate());
        m.put("schoolId", profile == null ? null : profile.getSchoolId());
        m.put("emergencyContactName", profile == null ? null : profile.getEmergencyContactName());
        m.put("emergencyContactPhone", profile == null ? null : profile.getEmergencyContactPhone());
        m.put("contractUrl", profile == null ? null : profile.getContractUrl());
        m.put("outstandingBalance", nz(outstandingBalance));
        m.put("createdAt", u.getCreatedAt());
        return m;
    }

    /** Buong detalye ng tenant para sa admin. */
    public static Map<String, Object> tenantDetail(User u) {
        Map<String, Object> m = map();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("firstName", u.getFirstName());
        m.put("lastName", u.getLastName());
        m.put("phone", u.getPhone());
        m.put("status", u.getStatus().wire());
        m.put("emailVerified", u.isEmailVerified());
        TenantProfile profile = u.getProfile();
        Room room = profile == null ? null : profile.getRoom();
        m.put("roomId", room == null ? null : room.getId());
        m.put("roomNumber", room == null ? null : room.getRoomNumber());
        m.put("building", room == null ? null : room.getBuilding());
        m.put("monthlyRent", room == null ? null : room.getMonthlyRent());
        m.put("moveInDate", profile == null ? null : profile.getMoveInDate());
        m.put("schoolId", profile == null ? null : profile.getSchoolId());
        m.put("emergencyContactName", profile == null ? null : profile.getEmergencyContactName());
        m.put("emergencyContactPhone", profile == null ? null : profile.getEmergencyContactPhone());
        m.put("contractUrl", profile == null ? null : profile.getContractUrl());
        m.put("createdAt", u.getCreatedAt());
        m.put("lastLoginAt", u.getLastLoginAt());
        return m;
    }

    public static String roomNumberOf(User u) {
        if (u == null || u.getProfile() == null || u.getProfile().getRoom() == null) return null;
        return u.getProfile().getRoom().getRoomNumber();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
