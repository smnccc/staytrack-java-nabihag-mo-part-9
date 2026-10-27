package com.staytrack.service;

import com.staytrack.domain.Notification;
import com.staytrack.domain.Payment;
import com.staytrack.domain.Room;
import com.staytrack.domain.ServiceRequest;
import com.staytrack.domain.TenantProfile;
import com.staytrack.domain.User;
import com.staytrack.domain.enums.OtpPurpose;
import com.staytrack.domain.enums.PaymentStatus;
import com.staytrack.domain.enums.RequestStatus;
import com.staytrack.domain.enums.RequestType;
import com.staytrack.domain.enums.Role;
import com.staytrack.domain.enums.RoomStatus;
import com.staytrack.domain.enums.UserStatus;
import com.staytrack.dto.AdminRequests;
import com.staytrack.dto.DtoMapper;
import com.staytrack.exception.ApiException;
import com.staytrack.repository.AuditLogRepository;
import com.staytrack.repository.NotificationRepository;
import com.staytrack.repository.OtpCodeRepository;
import com.staytrack.repository.PaymentRepository;
import com.staytrack.repository.RoomRepository;
import com.staytrack.repository.ServiceRequestRepository;
import com.staytrack.repository.TenantProfileRepository;
import com.staytrack.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Dito naka-grupo lahat ng function ng admin console. */
@Service
public class AdminService {

    private static final List<PaymentStatus> OUTSTANDING =
        List.of(PaymentStatus.UNPAID, PaymentStatus.SUBMITTED, PaymentStatus.OVERDUE, PaymentStatus.REJECTED);

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final PaymentRepository paymentRepository;
    private final ServiceRequestRepository requestRepository;
    private final NotificationRepository notificationRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final StorageService storageService;

    public AdminService(UserRepository userRepository,
                        RoomRepository roomRepository,
                        TenantProfileRepository tenantProfileRepository,
                        PaymentRepository paymentRepository,
                        ServiceRequestRepository requestRepository,
                        NotificationRepository notificationRepository,
                        OtpCodeRepository otpCodeRepository,
                        AuditLogRepository auditLogRepository,
                        PasswordEncoder passwordEncoder,
                        OtpService otpService,
                        EmailService emailService,
                        AuditService auditService,
                        StorageService storageService) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.tenantProfileRepository = tenantProfileRepository;
        this.paymentRepository = paymentRepository;
        this.requestRepository = requestRepository;
        this.notificationRepository = notificationRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.emailService = emailService;
        this.auditService = auditService;
        this.storageService = storageService;
    }

    // --- Dashboard ---
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        Map<String, Object> tenants = new LinkedHashMap<>();
        tenants.put("total", userRepository.countByRole(Role.TENANT));
        tenants.put("active", userRepository.countByRoleAndStatus(Role.TENANT, UserStatus.ACTIVE));
        tenants.put("pending", userRepository.countByRoleAndStatus(Role.TENANT, UserStatus.PENDING));
        tenants.put("suspended", userRepository.countByRoleAndStatus(Role.TENANT, UserStatus.SUSPENDED));

        Map<String, Object> requests = new LinkedHashMap<>();
        requests.put("total", requestRepository.count());
        requests.put("pending", requestRepository.countByStatus(RequestStatus.PENDING));
        requests.put("inProgress", requestRepository.countByStatus(RequestStatus.IN_PROGRESS));
        requests.put("resolved", requestRepository.countByStatus(RequestStatus.RESOLVED));

        Map<String, Object> payments = new LinkedHashMap<>();
        payments.put("awaitingVerification", paymentRepository.countByStatus(PaymentStatus.SUBMITTED));
        payments.put("unpaid", paymentRepository.countByStatus(PaymentStatus.UNPAID));
        payments.put("verified", paymentRepository.countByStatus(PaymentStatus.VERIFIED));
        payments.put("outstanding", paymentRepository.sumOutstanding(OUTSTANDING));
        payments.put("collected", paymentRepository.sumAmountPaidByStatus(PaymentStatus.VERIFIED));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenants", tenants);
        result.put("requests", requests);
        result.put("payments", payments);
        return result;
    }

    // --- Tenant accounts ---
    @Transactional(readOnly = true)
    public Map<String, Object> listTenants(String search, String statusRaw) {
        UserStatus statusFilter = null;
        if (statusRaw != null && !statusRaw.isBlank()) {
            try {
                statusFilter = UserStatus.fromWire(statusRaw);
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("Unknown status filter");
            }
        }

        Map<UUID, BigDecimal> balances = outstandingByTenant();
        String searchLower = (search == null || search.isBlank()) ? null : search.trim().toLowerCase();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (User tenant : userRepository.findByRoleOrderByCreatedAtDesc(Role.TENANT)) {
            if (statusFilter != null && tenant.getStatus() != statusFilter) {
                continue;
            }
            if (searchLower != null) {
                String haystack = (tenant.getFirstName() + " " + tenant.getLastName() + " " + tenant.getEmail())
                    .toLowerCase();
                if (!haystack.contains(searchLower)) {
                    continue;
                }
            }
            rows.add(DtoMapper.tenantRow(tenant, balances.get(tenant.getId())));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenants", rows);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTenant(UUID id) {
        User tenant = userRepository.findById(id)
            .filter(u -> u.getRole() == Role.TENANT)
            .orElseThrow(() -> ApiException.notFound("Tenant not found"));

        List<Map<String, Object>> payments = new ArrayList<>();
        for (Payment p : paymentRepository.findForTenant(id)) {
            payments.add(DtoMapper.payment(p));
        }
        List<Map<String, Object>> requests = new ArrayList<>();
        for (ServiceRequest r : requestRepository.findByTenantIdOrderByCreatedAtDesc(id)) {
            requests.add(DtoMapper.serviceRequest(r, false));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenant", DtoMapper.tenantDetail(tenant));
        result.put("payments", payments);
        result.put("requests", requests);
        return result;
    }

    @Transactional
    public Map<String, Object> createTenant(AdminRequests.CreateTenant req, UUID actorId) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("A user with that email already exists.");
        }

        Room room = null;
        if (req.roomId() != null && !req.roomId().isBlank()) {
            room = roomRepository.findById(parseUuid(req.roomId(), "Room"))
                .orElseThrow(() -> ApiException.notFound("Room not found"));
        }

        String tempPassword = "ST-" + UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setEmail(email);
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setPhone(blankToNull(req.phone()));
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setRole(Role.TENANT);
        user.setStatus(UserStatus.PENDING);
        user.setEmailVerified(false);
        user = userRepository.save(user);

        TenantProfile profile = new TenantProfile(user);
        profile.setRoom(room);
        profile.setMoveInDate(req.moveInDate());
        tenantProfileRepository.save(profile);
        if (room != null) {
            room.setStatus(RoomStatus.OCCUPIED);
            roomRepository.save(room);
        }

        var mail = otpService.issue(user, OtpPurpose.SIGNUP);

        auditService.record(actorId, "tenant.create", "user", user.getId(),
            Map.of("email", email));

        Map<String, Object> tenant = new LinkedHashMap<>();
        tenant.put("id", user.getId());
        tenant.put("email", user.getEmail());
        tenant.put("firstName", user.getFirstName());
        tenant.put("lastName", user.getLastName());
        tenant.put("status", user.getStatus().wire());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Tenant account created. A verification code was emailed to them.");
        result.put("tenant", tenant);
        if (mail.devCode() != null) {
            result.put("devOtp", mail.devCode());
        }
        return result;
    }

    /** Update na hindi kailangang kumpleto, yung binago lang ang itatama. */
    @Transactional
    public Map<String, Object> updateTenant(UUID id, Map<String, Object> body, UUID actorId) {
        User tenant = userRepository.findById(id)
            .filter(u -> u.getRole() == Role.TENANT)
            .orElseThrow(() -> ApiException.notFound("Tenant not found"));

        if (body.containsKey("firstName") && body.get("firstName") != null) {
            tenant.setFirstName(String.valueOf(body.get("firstName")).trim());
        }
        if (body.containsKey("lastName") && body.get("lastName") != null) {
            tenant.setLastName(String.valueOf(body.get("lastName")).trim());
        }
        if (body.containsKey("phone")) {
            tenant.setPhone(blankToNull(body.get("phone")));
        }
        if (body.containsKey("status") && body.get("status") != null) {
            tenant.setStatus(parseStatus(String.valueOf(body.get("status"))));
        }
        userRepository.save(tenant);

        TenantProfile profile = tenantProfileRepository.findByUserId(id)
            .orElseGet(() -> new TenantProfile(tenant));

        if (body.containsKey("roomId")) {
            Object rawRoom = body.get("roomId");
            Room previous = profile.getRoom();
            Room next = (rawRoom == null || String.valueOf(rawRoom).isBlank())
                ? null
                : roomRepository.findById(parseUuid(String.valueOf(rawRoom), "Room"))
                    .orElseThrow(() -> ApiException.notFound("Room not found"));

            profile.setRoom(next);
            tenantProfileRepository.save(profile);
            tenantProfileRepository.flush();

            if (previous != null) {
                refreshRoomStatus(previous);
            }
            if (next != null) {
                refreshRoomStatus(next);
            }
        } else if (profile.getUserId() == null) {
            tenantProfileRepository.save(profile);
        }

        if (body.containsKey("moveInDate") && body.get("moveInDate") != null
            && !String.valueOf(body.get("moveInDate")).isBlank()) {
            try {
                profile.setMoveInDate(LocalDate.parse(String.valueOf(body.get("moveInDate"))));
                tenantProfileRepository.save(profile);
            } catch (Exception ex) {
                throw ApiException.badRequest("moveInDate must be a valid date (YYYY-MM-DD).");
            }
        }

        auditService.record(actorId, "tenant.update", "user", id, body);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Tenant account updated.");
        return result;
    }

    /** Admin nag-upload ng signed contract PDF para sa isang tenant. */
    @Transactional
    public Map<String, Object> uploadContract(UUID id, MultipartFile file, UUID actorId) {
        User tenant = userRepository.findById(id)
            .filter(u -> u.getRole() == Role.TENANT)
            .orElseThrow(() -> ApiException.notFound("Tenant not found"));

        TenantProfile profile = tenantProfileRepository.findByUserId(id)
            .orElseGet(() -> new TenantProfile(tenant));

        String url = storageService.storePdf(file, "contracts");
        profile.setContractUrl(url);
        tenantProfileRepository.save(profile);

        auditService.record(actorId, "tenant.contract", "user", id, Map.of("url", url));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Contract uploaded.");
        result.put("contractUrl", url);
        return result;
    }

    @Transactional
    public Map<String, Object> deactivateTenant(UUID id, UUID actorId) {
        User tenant = userRepository.findById(id)
            .filter(u -> u.getRole() == Role.TENANT)
            .orElseThrow(() -> ApiException.notFound("Tenant not found"));

        tenant.setStatus(UserStatus.REJECTED);
        userRepository.save(tenant);

        auditService.record(actorId, "tenant.deactivate", "user", id, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Tenant account deactivated.");
        Map<String, Object> tenantMap = new LinkedHashMap<>();
        tenantMap.put("id", tenant.getId());
        tenantMap.put("email", tenant.getEmail());
        result.put("tenant", tenantMap);
        return result;
    }

    /** Buong-burang delete ng tenant at lahat ng record niya. */
    @Transactional
    public Map<String, Object> deleteTenantPermanently(UUID id, UUID actorId) {
        User tenant = userRepository.findById(id)
            .filter(u -> u.getRole() == Role.TENANT)
            .orElseThrow(() -> ApiException.notFound("Tenant not found"));

        String email = tenant.getEmail();
        TenantProfile profile = tenant.getProfile();
        Room room = profile == null ? null : profile.getRoom();

        // Manual na binubura yung mga naka-link, para gumana kahit walang cascade.
        notificationRepository.deleteByUserId(id);
        otpCodeRepository.deleteByUserId(id);
        requestRepository.deleteByTenantId(id);
        paymentRepository.deleteByTenantId(id);
        if (profile != null) {
            tenantProfileRepository.delete(profile);
        }
        userRepository.deleteById(id);

        // Ibinalik sa available yung room na iniwan niya.
        if (room != null) {
            refreshRoomStatus(room);
        }

        auditService.record(actorId, "tenant.delete", "user", id, Map.of("email", email));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Tenant account and all related records were deleted.");
        return result;
    }

    // --- Rooms ---
    @Transactional(readOnly = true)
    public Map<String, Object> listRooms() {
        List<Map<String, Object>> rooms = new ArrayList<>();
        for (Room room : roomRepository.findAllByOrderByBuildingAscRoomNumberAsc()) {
            rooms.add(DtoMapper.room(room, tenantProfileRepository.countByRoomId(room.getId())));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rooms", rooms);
        return result;
    }

    @Transactional
    public Map<String, Object> createRoom(AdminRequests.CreateRoom req) {
        String building = (req.building() == null || req.building().isBlank()) ? "North Hall" : req.building().trim();
        if (roomRepository.findByBuildingAndRoomNumber(building, req.roomNumber().trim()).isPresent()) {
            throw ApiException.conflict("That room already exists in this building.");
        }

        Room room = new Room();
        room.setRoomNumber(req.roomNumber().trim());
        room.setBuilding(building);
        room.setFloor(req.floor());
        room.setMonthlyRent(req.monthlyRent() == null ? BigDecimal.ZERO : req.monthlyRent());
        room.setCapacity(req.capacity() == null ? 1 : req.capacity());
        room = roomRepository.save(room);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Room created.");
        result.put("room", DtoMapper.room(room, 0));
        return result;
    }

    @Transactional
    public Map<String, Object> updateRoom(UUID id, Map<String, Object> body) {
        Room room = roomRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Room not found"));

        if (body.containsKey("monthlyRent") && body.get("monthlyRent") != null) {
            room.setMonthlyRent(new BigDecimal(String.valueOf(body.get("monthlyRent"))));
        }
        if (body.containsKey("capacity") && body.get("capacity") != null) {
            room.setCapacity(Integer.parseInt(String.valueOf(body.get("capacity"))));
        }
        if (body.containsKey("status") && body.get("status") != null) {
            try {
                room.setStatus(RoomStatus.fromWire(String.valueOf(body.get("status"))));
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("status must be available, occupied or maintenance");
            }
        }
        room = roomRepository.save(room);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Room updated.");
        result.put("room", DtoMapper.room(room, tenantProfileRepository.countByRoomId(room.getId())));
        return result;
    }

    @Transactional
    public Map<String, Object> deleteRoom(UUID id, UUID actorId) {
        Room room = roomRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Room not found"));

        // Di pwedeng burahin ang room kung may tenant pa.
        long occupants = tenantProfileRepository.countByRoomId(id);
        if (occupants > 0) {
            throw ApiException.conflict("This room still has " + occupants
                + " tenant(s) assigned. Reassign them to another room before deleting it.");
        }

        String label = room.getBuilding() + " · " + room.getRoomNumber();
        roomRepository.delete(room);

        auditService.record(actorId, "room.delete", "room", id, Map.of("label", label));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Room " + label + " was deleted.");
        return result;
    }

    // --- Requests ---
    @Transactional(readOnly = true)
    public Map<String, Object> listRequests(String statusRaw, String typeRaw, String search) {
        RequestStatus statusFilter = parseRequestStatus(statusRaw);
        RequestType typeFilter = parseRequestType(typeRaw);
        String searchLower = (search == null || search.isBlank()) ? null : search.trim().toLowerCase();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ServiceRequest request : requestRepository.findAllWithTenant()) {
            if (statusFilter != null && request.getStatus() != statusFilter) continue;
            if (typeFilter != null && request.getType() != typeFilter) continue;
            if (searchLower != null) {
                String haystack = (request.getDescription() + " " + request.getCategory()).toLowerCase();
                if (!haystack.contains(searchLower)) continue;
            }
            rows.add(DtoMapper.serviceRequest(request, false));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requests", rows);
        return result;
    }

    @Transactional
    public Map<String, Object> updateRequest(UUID id, AdminRequests.UpdateRequestBody req, UUID actorId) {
        ServiceRequest request = requestRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Request not found"));

        if (req.status() == null && req.adminNotes() == null) {
            throw ApiException.badRequest("Provide a status and/or admin notes to update.");
        }

        if (req.status() != null) {
            RequestStatus status = parseRequestStatus(req.status());
            if (status == null) {
                throw ApiException.badRequest("Unknown status");
            }
            request.setStatus(status);
            if (status == RequestStatus.RESOLVED) {
                request.setResolvedAt(Instant.now());
            }
        }
        if (req.adminNotes() != null) {
            request.setAdminNotes(req.adminNotes().isBlank() ? null : req.adminNotes().trim());
        }
        request.setHandledBy(actorId);
        request = requestRepository.save(request);

        notificationRepository.save(new Notification(
            request.getTenant().getId(),
            notificationTitleFor(request.getStatus()),
            request.getAdminNotes() != null
                ? request.getAdminNotes()
                : "Your " + request.getType().wire() + " request was updated.",
            "info"));

        auditService.record(actorId, "request.update", "service_request", id,
            Map.of("status", request.getStatus().wire()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Request updated.");
        result.put("request", DtoMapper.serviceRequest(request, false));
        return result;
    }

    /** Burahin ang request, pero resolved lang ang pwede. */
    @Transactional
    public Map<String, Object> deleteRequest(UUID requestId, UUID actorId) {
        ServiceRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> ApiException.notFound("Request not found"));

        if (request.getStatus() != RequestStatus.RESOLVED) {
            throw ApiException.badRequest("Only resolved requests can be deleted.");
        }

        String category = request.getCategory();
        String tenantEmail = request.getTenant() == null ? "unknown" : request.getTenant().getEmail();

        requestRepository.delete(request);

        auditService.record(actorId, "request.delete", "service_request", requestId,
            Map.of("tenant", tenantEmail, "category", category));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Request deleted.");
        return result;
    }

    // --- Payments ---
    @Transactional(readOnly = true)
    public Map<String, Object> listPayments(String statusRaw, String search, String period) {
        PaymentStatus statusFilter = null;
        if (statusRaw != null && !statusRaw.isBlank()) {
            try {
                statusFilter = PaymentStatus.fromWire(statusRaw);
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("Unknown status filter");
            }
        }
        String searchLower = (search == null || search.isBlank()) ? null : search.trim().toLowerCase();
        LocalDate periodFilter = null;
        if (period != null && !period.isBlank()) {
            try {
                periodFilter = LocalDate.parse(period.length() >= 7 ? period.substring(0, 7) + "-01" : period);
            } catch (Exception ex) {
                throw ApiException.badRequest("period must be a date like 2026-09-01");
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Payment payment : paymentRepository.findAllWithDetails()) {
            if (statusFilter != null && payment.getStatus() != statusFilter) continue;
            if (periodFilter != null) {
                if (payment.getBillingPeriod() == null
                    || payment.getBillingPeriod().getYear() != periodFilter.getYear()
                    || payment.getBillingPeriod().getMonthValue() != periodFilter.getMonthValue()) {
                    continue;
                }
            }
            if (searchLower != null) {
                User tenant = payment.getTenant();
                String haystack = (tenant.getFirstName() + " " + tenant.getLastName() + " " + tenant.getEmail())
                    .toLowerCase();
                if (!haystack.contains(searchLower)) continue;
            }
            rows.add(DtoMapper.payment(payment));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("payments", rows);
        return result;
    }

    @Transactional
    public Map<String, Object> createPayment(AdminRequests.CreatePayment req, UUID actorId) {
        if (req.billingPeriod() == null || req.dueDate() == null) {
            throw ApiException.badRequest("billingPeriod and dueDate are required.");
        }
        UUID tenantId = parseUuid(req.tenantId(), "Tenant");
        User tenant = userRepository.findById(tenantId)
            .filter(u -> u.getRole() == Role.TENANT)
            .orElseThrow(() -> ApiException.notFound("Tenant not found"));

        LocalDate period = req.billingPeriod().withDayOfMonth(1);
        if (paymentRepository.findByTenantIdAndBillingPeriod(tenantId, period).isPresent()) {
            throw ApiException.conflict("A bill already exists for that tenant and month.");
        }

        Room room = tenant.getProfile() == null ? null : tenant.getProfile().getRoom();
        BigDecimal rent = req.rentAmount() != null
            ? req.rentAmount()
            : (room != null && room.getMonthlyRent() != null ? room.getMonthlyRent() : BigDecimal.ZERO);

        Payment payment = new Payment();
        payment.setTenant(tenant);
        payment.setRoom(room);
        payment.setBillingPeriod(period);
        payment.setRentAmount(rent);
        payment.setUtilitiesAmount(req.utilitiesAmount() == null ? BigDecimal.ZERO : req.utilitiesAmount());
        payment.setOtherAmount(req.otherAmount() == null ? BigDecimal.ZERO : req.otherAmount());
        payment.setAmountPaid(BigDecimal.ZERO);
        payment.setDueDate(req.dueDate());
        payment.setStatus(PaymentStatus.UNPAID);
        payment = paymentRepository.save(payment);

        notificationRepository.save(new Notification(
            tenantId, "New bill posted", "A new rent bill has been added to your account.", "info"));

        AuditService audit = auditService;
        audit.record(actorId, "payment.create", "payment", payment.getId(), null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Bill created.");
        result.put("payment", DtoMapper.payment(payment));
        return result;
    }

    @Transactional
    public Map<String, Object> generateBilling(AdminRequests.GenerateBilling req, UUID actorId) {
        if (req.billingPeriod() == null || req.dueDate() == null) {
            throw ApiException.badRequest("billingPeriod and dueDate are required.");
        }
        LocalDate period = req.billingPeriod().withDayOfMonth(1);
        BigDecimal utilities = req.utilitiesAmount() == null ? BigDecimal.ZERO : req.utilitiesAmount();
        BigDecimal other = req.otherAmount() == null ? BigDecimal.ZERO : req.otherAmount();

        int generated = 0;
        for (User tenant : userRepository.findByRoleOrderByCreatedAtDesc(Role.TENANT)) {
            if (tenant.getStatus() != UserStatus.ACTIVE) continue;
            Room room = tenant.getProfile() == null ? null : tenant.getProfile().getRoom();
            if (room == null) continue;
            if (paymentRepository.findByTenantIdAndBillingPeriod(tenant.getId(), period).isPresent()) continue;

            Payment payment = new Payment();
            payment.setTenant(tenant);
            payment.setRoom(room);
            payment.setBillingPeriod(period);
            payment.setRentAmount(room.getMonthlyRent() == null ? BigDecimal.ZERO : room.getMonthlyRent());
            payment.setUtilitiesAmount(utilities);
            payment.setOtherAmount(other);
            payment.setAmountPaid(BigDecimal.ZERO);
            payment.setDueDate(req.dueDate());
            payment.setStatus(PaymentStatus.UNPAID);
            paymentRepository.save(payment);
            generated++;
        }

        auditService.record(actorId, "payments.generate", "payment", null,
            Map.of("billingPeriod", period.toString(), "generated", generated));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Generated " + generated + " bill(s).");
        result.put("generated", generated);
        return result;
    }

    @Transactional
    public Map<String, Object> verifyPayment(UUID id, AdminRequests.VerifyPayment req, UUID actorId) {
        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (payment.getStatus() == PaymentStatus.VERIFIED) {
            throw ApiException.badRequest("Payment is already verified.");
        }

        BigDecimal paid = req.amountPaid() != null ? req.amountPaid() : payment.getTotalAmount();
        payment.setStatus(PaymentStatus.VERIFIED);
        payment.setAmountPaid(paid);
        if (req.remarks() != null && !req.remarks().isBlank()) {
            payment.setRemarks(req.remarks().trim());
        }
        payment.setVerifiedBy(actorId);
        payment.setVerifiedAt(Instant.now());
        payment = paymentRepository.save(payment);

        notificationRepository.save(new Notification(
            payment.getTenant().getId(),
            "Payment verified",
            "Your payment of PHP " + paid + " has been verified. Thank you!",
            "success"));

        auditService.record(actorId, "payment.verify", "payment", id,
            Map.of("amountPaid", paid.toString()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Payment verified.");
        result.put("payment", DtoMapper.payment(payment));
        return result;
    }

    @Transactional
    public Map<String, Object> rejectPayment(UUID id, AdminRequests.RejectPayment req, UUID actorId) {
        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Payment not found"));

        String remarks = (req.remarks() == null || req.remarks().isBlank())
            ? "Proof rejected. Please re-upload."
            : req.remarks().trim();

        payment.setStatus(PaymentStatus.REJECTED);
        payment.setRemarks(remarks);
        payment.setVerifiedBy(actorId);
        payment.setVerifiedAt(Instant.now());
        payment = paymentRepository.save(payment);

        notificationRepository.save(new Notification(
            payment.getTenant().getId(),
            "Payment rejected",
            remarks,
            "warning"));

        auditService.record(actorId, "payment.reject", "payment", id, Map.of("remarks", remarks));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Payment rejected.");
        result.put("payment", DtoMapper.payment(payment));
        return result;
    }

    /** Padalhan ng reminder sa email ang tenant para sa isang bill. */
    @Transactional
    public Map<String, Object> sendPaymentReminder(UUID paymentId, UUID actorId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> ApiException.notFound("Payment not found"));

        if (payment.getStatus() == PaymentStatus.VERIFIED) {
            throw ApiException.badRequest("This bill is already paid and verified — no reminder needed.");
        }
        if (payment.getStatus() == PaymentStatus.SUBMITTED) {
            throw ApiException.badRequest("This payment is awaiting verification — no reminder needed.");
        }

        User tenant = payment.getTenant();
        Room room = payment.getRoom();
        boolean overdue = payment.getDueDate() != null && payment.getDueDate().isBefore(LocalDate.now());

        EmailService.ReminderResult sent = emailService.sendRentDueReminder(
            tenant.getEmail(),
            tenant.getFirstName(),
            "PHP " + payment.balance().toPlainString(),
            payment.getDueDate() == null ? "soon" : payment.getDueDate().toString(),
            room == null ? null : room.getRoomNumber(),
            overdue);

        auditService.record(actorId, "payment.remind", "payment", paymentId,
            Map.of("tenant", tenant.getEmail(), "delivered", sent.delivered()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("delivered", sent.delivered());
        result.put("message", sent.delivered()
            ? "Reminder emailed to " + tenant.getEmail() + "."
            : "Email is not configured, so the reminder was printed in the server console instead.");
        return result;
    }

    /** Burahin ang payment, pero verified lang para di mawala ang unpaid. */
    @Transactional
    public Map<String, Object> deletePayment(UUID paymentId, UUID actorId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> ApiException.notFound("Payment not found"));

        if (payment.getStatus() != PaymentStatus.VERIFIED) {
            throw ApiException.badRequest(
                "Only approved (verified) payments can be deleted.");
        }

        String tenantEmail = payment.getTenant() == null ? "unknown" : payment.getTenant().getEmail();
        String period = payment.getBillingPeriod() == null ? "unknown" : payment.getBillingPeriod().toString();
        String amount = payment.getAmountPaid() == null ? "0" : payment.getAmountPaid().toPlainString();

        paymentRepository.delete(payment);

        auditService.record(actorId, "payment.delete", "payment", paymentId,
            Map.of("tenant", tenantEmail, "period", period, "amountPaid", amount));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Payment record deleted.");
        return result;
    }

    // --- Audit log ---
    @Transactional(readOnly = true)
    public Map<String, Object> listAuditLogs() {
        List<Map<String, Object>> logs = new ArrayList<>();
        for (var entry : auditLogRepository.findTop200ByOrderByCreatedAtDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", entry.getId());
            m.put("action", entry.getAction());
            m.put("entityType", entry.getEntityType());
            m.put("entityId", entry.getEntityId());
            m.put("metadata", entry.getMetadata());
            m.put("createdAt", entry.getCreatedAt());

            if (entry.getActorId() != null) {
                Optional<User> actor = userRepository.findById(entry.getActorId());
                if (actor.isPresent()) {
                    Map<String, Object> actorMap = new LinkedHashMap<>();
                    actorMap.put("name", actor.get().fullNameTrimmed());
                    actorMap.put("email", actor.get().getEmail());
                    m.put("actor", actorMap);
                } else {
                    m.put("actor", null);
                }
            } else {
                m.put("actor", null);
            }
            logs.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logs", logs);
        return result;
    }

    // --- helpers ---
    private Map<UUID, BigDecimal> outstandingByTenant() {
        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (Object[] row : paymentRepository.sumOutstandingGroupedByTenant(OUTSTANDING)) {
            UUID tenantId = (UUID) row[0];
            BigDecimal value = row[1] == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(row[1]));
            balances.put(tenantId, value);
        }
        return balances;
    }

    private void refreshRoomStatus(Room room) {
        long occupants = tenantProfileRepository.countByRoomId(room.getId());
        if (occupants > 0) {
            room.setStatus(RoomStatus.OCCUPIED);
        } else if (room.getStatus() == RoomStatus.OCCUPIED) {
            room.setStatus(RoomStatus.AVAILABLE);
        }
        roomRepository.save(room);
    }

    private String notificationTitleFor(RequestStatus status) {
        return switch (status) {
            case RESOLVED -> "Request resolved";
            case IN_PROGRESS -> "Request in progress";
            case REJECTED -> "Request closed";
            default -> "Request updated";
        };
    }

    private UserStatus parseStatus(String raw) {
        try {
            return UserStatus.fromWire(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("status must be pending, active, suspended or rejected");
        }
    }

    private RequestStatus parseRequestStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return RequestStatus.fromWire(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown status filter");
        }
    }

    private RequestType parseRequestType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return RequestType.fromWire(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown type filter");
        }
    }

    private UUID parseUuid(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest(label + " is required.");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(label + " is invalid.");
        }
    }

    private String blankToNull(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
