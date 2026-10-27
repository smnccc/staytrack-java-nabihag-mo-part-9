package com.staytrack.service;

import com.staytrack.domain.Notification;
import com.staytrack.domain.Payment;
import com.staytrack.domain.enums.PaymentStatus;
import com.staytrack.dto.DtoMapper;
import com.staytrack.exception.ApiException;
import com.staytrack.repository.NotificationRepository;
import com.staytrack.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mga bill ng tenant at yung proof na ina-upload niya. */
@Service
public class PaymentService {

    /** Mga status na may utang pa; VERIFIED lang ang nag-aalis. */
    private static final List<PaymentStatus> OUTSTANDING =
        List.of(PaymentStatus.UNPAID, PaymentStatus.SUBMITTED, PaymentStatus.OVERDUE, PaymentStatus.REJECTED);
    /** Mga status na pwedeng galawin agad ng tenant. */
    private static final List<PaymentStatus> DUE_NOW =
        List.of(PaymentStatus.UNPAID, PaymentStatus.OVERDUE, PaymentStatus.REJECTED);

    private final PaymentRepository paymentRepository;
    private final NotificationRepository notificationRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository,
                          NotificationRepository notificationRepository,
                          StorageService storageService,
                          AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.notificationRepository = notificationRepository;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listMine(UUID userId) {
        List<Payment> payments = paymentRepository.findForTenant(userId);

        List<Map<String, Object>> mapped = new ArrayList<>();
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal dueNow = BigDecimal.ZERO;
        BigDecimal totalBilled = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        Payment nextDue = null;

        for (Payment p : payments) {
            mapped.add(DtoMapper.payment(p));

            BigDecimal balance = p.balance();
            totalBilled = totalBilled.add(p.getTotalAmount() == null ? BigDecimal.ZERO : p.getTotalAmount());
            totalPaid = totalPaid.add(p.getAmountPaid() == null ? BigDecimal.ZERO : p.getAmountPaid());

            if (OUTSTANDING.contains(p.getStatus())) {
                outstanding = outstanding.add(balance);

                // Pinakaunang bill na may utang pa, kasama yung for review o rejected.
                if (nextDue == null || p.getDueDate().isBefore(nextDue.getDueDate())) {
                    nextDue = p;
                }

                if (DUE_NOW.contains(p.getStatus())) {
                    dueNow = dueNow.add(balance);
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("outstanding", outstanding);
        summary.put("dueNow", dueNow);
        summary.put("totalBilled", totalBilled);
        summary.put("totalPaid", totalPaid);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("payments", mapped);
        result.put("summary", summary);
        result.put("nextDue", nextDue == null ? null : DtoMapper.payment(nextDue));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMine(UUID userId, UUID paymentId) {
        Payment payment = paymentRepository.findByIdAndTenantId(paymentId, userId)
            .orElseThrow(() -> ApiException.notFound("Payment not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("payment", DtoMapper.payment(payment));
        return result;
    }

    @Transactional
    public Map<String, Object> submitProof(UUID userId,
                                           UUID paymentId,
                                           MultipartFile proof,
                                           String referenceNo,
                                           BigDecimal amountPaid) {
        Payment payment = paymentRepository.findByIdAndTenantId(paymentId, userId)
            .orElseThrow(() -> ApiException.notFound("Payment not found"));

        if (payment.getStatus() == PaymentStatus.VERIFIED) {
            throw ApiException.badRequest("This payment has already been verified.");
        }

        if (referenceNo == null || referenceNo.isBlank()) {
            throw ApiException.badRequest("Please enter the reference number from your payment receipt.");
        }

        String proofUrl = storageService.store(proof, "payments");
        if (proofUrl == null) {
            proofUrl = payment.getProofUrl();
        }
        if (proofUrl == null) {
            throw ApiException.badRequest("Please attach a screenshot of your payment.");
        }

        payment.setProofUrl(proofUrl);
        payment.setReferenceNo(referenceNo.trim());
        if (amountPaid != null) {
            payment.setAmountPaid(amountPaid);
        }
        payment.setStatus(PaymentStatus.SUBMITTED);
        payment.setSubmittedAt(Instant.now());
        payment.setRemarks(null);
        payment = paymentRepository.save(payment);

        notificationRepository.save(new Notification(
            userId,
            "Payment submitted",
            "Your payment proof was received and is awaiting verification.",
            "info"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Payment proof submitted. An admin will verify it shortly.");
        result.put("payment", DtoMapper.payment(payment));
        return result;
    }

    /** Tenant pwedeng magbura ng sariling payment, pero verified lang. */
    @Transactional
    public Map<String, Object> deleteMine(UUID userId, UUID paymentId) {
        Payment payment = paymentRepository.findByIdAndTenantId(paymentId, userId)
            .orElseThrow(() -> ApiException.notFound("Payment not found"));

        if (payment.getStatus() != PaymentStatus.VERIFIED) {
            throw ApiException.badRequest(
                "You can only delete a payment record once it has been verified.");
        }

        String period = payment.getBillingPeriod() == null
            ? "unknown"
            : payment.getBillingPeriod().toString();

        paymentRepository.delete(payment);

        auditService.record(userId, "payment.delete.self", "payment", paymentId,
            Map.of("period", period));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Payment record deleted.");
        return result;
    }
}
