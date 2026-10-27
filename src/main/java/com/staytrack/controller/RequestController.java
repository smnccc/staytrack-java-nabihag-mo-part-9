package com.staytrack.controller;

import com.staytrack.domain.User;
import com.staytrack.service.RequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> create(
        @AuthenticationPrincipal User user,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "category", required = false) String category,
        @RequestParam(value = "description", required = false) String description,
        @RequestParam(value = "photo", required = false) MultipartFile photo) {

        Map<String, Object> body = requestService.create(
            user.getId(), type, category, description, photo);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal User user,
                                    @RequestParam(value = "status", required = false) String status) {
        return requestService.listMine(user.getId(), status);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return requestService.getMine(user.getId(), id);
    }

    @PatchMapping("/{id}/cancel")
    public Map<String, Object> cancel(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return requestService.cancel(user.getId(), id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return requestService.deleteMine(user.getId(), id);
    }
}
