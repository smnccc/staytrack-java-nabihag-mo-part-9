package com.staytrack.controller;

import com.staytrack.domain.User;
import com.staytrack.dto.AdminRequests;
import com.staytrack.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // --- dashboard ---
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return adminService.dashboard();
    }

    // --- tenants ---
    @GetMapping("/tenants")
    public Map<String, Object> listTenants(@RequestParam(value = "search", required = false) String search,
                                           @RequestParam(value = "status", required = false) String status) {
        return adminService.listTenants(search, status);
    }

    @PostMapping("/tenants")
    public ResponseEntity<Map<String, Object>> createTenant(@AuthenticationPrincipal User admin,
                                                            @Valid @RequestBody AdminRequests.CreateTenant req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createTenant(req, admin.getId()));
    }

    @GetMapping("/tenants/{id}")
    public Map<String, Object> getTenant(@PathVariable UUID id) {
        return adminService.getTenant(id);
    }

    @PatchMapping("/tenants/{id}")
    public Map<String, Object> updateTenant(@AuthenticationPrincipal User admin,
                                            @PathVariable UUID id,
                                            @RequestBody Map<String, Object> body) {
        return adminService.updateTenant(id, body, admin.getId());
    }

    @DeleteMapping("/tenants/{id}")
    public Map<String, Object> deactivateTenant(@AuthenticationPrincipal User admin, @PathVariable UUID id) {
        return adminService.deactivateTenant(id, admin.getId());
    }

    @DeleteMapping("/tenants/{id}/permanent")
    public Map<String, Object> deleteTenantPermanently(@AuthenticationPrincipal User admin, @PathVariable UUID id) {
        return adminService.deleteTenantPermanently(id, admin.getId());
    }

    @PostMapping(value = "/tenants/{id}/contract", consumes = {"multipart/form-data"})
    public Map<String, Object> uploadContract(@AuthenticationPrincipal User admin,
                                              @PathVariable UUID id,
                                              @RequestParam(value = "contract", required = false) MultipartFile contract) {
        return adminService.uploadContract(id, contract, admin.getId());
    }

    // --- rooms ---
    @GetMapping("/rooms")
    public Map<String, Object> listRooms() {
        return adminService.listRooms();
    }

    @PostMapping("/rooms")
    public ResponseEntity<Map<String, Object>> createRoom(@Valid @RequestBody AdminRequests.CreateRoom req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createRoom(req));
    }

    @PatchMapping("/rooms/{id}")
    public Map<String, Object> updateRoom(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return adminService.updateRoom(id, body);
    }

    @DeleteMapping("/rooms/{id}")
    public Map<String, Object> deleteRoom(@AuthenticationPrincipal User admin, @PathVariable UUID id) {
        return adminService.deleteRoom(id, admin.getId());
    }

    // --- requests ---
    @GetMapping("/requests")
    public Map<String, Object> listRequests(@RequestParam(value = "status", required = false) String status,
                                            @RequestParam(value = "type", required = false) String type,
                                            @RequestParam(value = "search", required = false) String search) {
        return adminService.listRequests(status, type, search);
    }

    @PatchMapping("/requests/{id}")
    public Map<String, Object> updateRequest(@AuthenticationPrincipal User admin,
                                             @PathVariable UUID id,
                                             @Valid @RequestBody AdminRequests.UpdateRequestBody req) {
        return adminService.updateRequest(id, req, admin.getId());
    }

    @DeleteMapping("/requests/{id}")
    public Map<String, Object> deleteRequest(@AuthenticationPrincipal User admin, @PathVariable UUID id) {
        return adminService.deleteRequest(id, admin.getId());
    }

    // --- payments ---
    @GetMapping("/payments")
    public Map<String, Object> listPayments(@RequestParam(value = "status", required = false) String status,
                                            @RequestParam(value = "search", required = false) String search,
                                            @RequestParam(value = "period", required = false) String period) {
        return adminService.listPayments(status, search, period);
    }

    @PostMapping("/payments")
    public ResponseEntity<Map<String, Object>> createPayment(@AuthenticationPrincipal User admin,
                                                             @Valid @RequestBody AdminRequests.CreatePayment req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createPayment(req, admin.getId()));
    }

    @PostMapping("/payments/generate")
    public ResponseEntity<Map<String, Object>> generateBilling(@AuthenticationPrincipal User admin,
                                                               @Valid @RequestBody AdminRequests.GenerateBilling req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.generateBilling(req, admin.getId()));
    }

    @PatchMapping("/payments/{id}/verify")
    public Map<String, Object> verifyPayment(@AuthenticationPrincipal User admin,
                                             @PathVariable UUID id,
                                             @Valid @RequestBody AdminRequests.VerifyPayment req) {
        return adminService.verifyPayment(id, req, admin.getId());
    }

    @PatchMapping("/payments/{id}/reject")
    public Map<String, Object> rejectPayment(@AuthenticationPrincipal User admin,
                                             @PathVariable UUID id,
                                             @Valid @RequestBody AdminRequests.RejectPayment req) {
        return adminService.rejectPayment(id, req, admin.getId());
    }

    @PostMapping("/payments/{id}/remind")
    public Map<String, Object> remindPayment(@AuthenticationPrincipal User admin, @PathVariable UUID id) {
        return adminService.sendPaymentReminder(id, admin.getId());
    }

    @DeleteMapping("/payments/{id}")
    public Map<String, Object> deletePayment(@AuthenticationPrincipal User admin, @PathVariable UUID id) {
        return adminService.deletePayment(id, admin.getId());
    }

    // --- audit ---
    @GetMapping("/audit-logs")
    public Map<String, Object> auditLogs() {
        return adminService.listAuditLogs();
    }
}
