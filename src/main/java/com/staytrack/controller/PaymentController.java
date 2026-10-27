package com.staytrack.controller;

import com.staytrack.domain.User;
import com.staytrack.service.PaymentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal User user) {
        return paymentService.listMine(user.getId());
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return paymentService.getMine(user.getId(), id);
    }

    @PostMapping(value = "/{id}/proof", consumes = {"multipart/form-data"})
    public Map<String, Object> submitProof(
        @AuthenticationPrincipal User user,
        @PathVariable UUID id,
        @RequestParam(value = "proof", required = false) MultipartFile proof,
        @RequestParam(value = "referenceNo", required = false) String referenceNo,
        @RequestParam(value = "amountPaid", required = false) BigDecimal amountPaid) {

        return paymentService.submitProof(user.getId(), id, proof, referenceNo, amountPaid);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return paymentService.deleteMine(user.getId(), id);
    }
}
