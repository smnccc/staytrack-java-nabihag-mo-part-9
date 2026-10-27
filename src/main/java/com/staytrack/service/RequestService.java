package com.staytrack.service;

import com.staytrack.domain.ServiceRequest;
import com.staytrack.domain.User;
import com.staytrack.domain.enums.RequestStatus;
import com.staytrack.domain.enums.RequestType;
import com.staytrack.dto.DtoMapper;
import com.staytrack.exception.ApiException;
import com.staytrack.repository.ServiceRequestRepository;
import com.staytrack.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mga reklamo at repair request ng tenant. */
@Service
public class RequestService {

    private final ServiceRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public RequestService(ServiceRequestRepository requestRepository,
                          UserRepository userRepository,
                          StorageService storageService) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    @Transactional
    public Map<String, Object> create(UUID userId,
                                      String typeRaw,
                                      String category,
                                      String description,
                                      MultipartFile photo) {
        if (category == null || category.isBlank()) {
            throw ApiException.badRequest("Please choose a category.");
        }
        if (description == null || description.trim().length() < 3) {
            throw ApiException.badRequest("Please describe the issue first.");
        }

        User tenant = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("Account not found"));

        RequestType type = RequestType.MAINTENANCE;
        if (typeRaw != null && !typeRaw.isBlank()) {
            try {
                type = RequestType.fromWire(typeRaw);
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("type must be maintenance or neighbor");
            }
        }

        String photoUrl = storageService.store(photo, "requests");

        ServiceRequest request = new ServiceRequest();
        request.setTenant(tenant);
        request.setType(type);
        request.setCategory(category.trim());
        request.setDescription(description.trim());
        request.setAnonymous(false);
        request.setPhotoUrl(photoUrl);
        request.setStatus(RequestStatus.PENDING);
        request = requestRepository.save(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Request submitted successfully.");
        result.put("request", DtoMapper.serviceRequest(request, false));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listMine(UUID userId, String statusRaw) {
        List<ServiceRequest> requests = requestRepository.findByTenantIdOrderByCreatedAtDesc(userId);

        RequestStatus filter = null;
        if (statusRaw != null && !statusRaw.isBlank()) {
            try {
                filter = RequestStatus.fromWire(statusRaw);
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("Unknown status filter");
            }
        }

        List<Map<String, Object>> mapped = new ArrayList<>();
        for (ServiceRequest request : requests) {
            if (filter == null || request.getStatus() == filter) {
                mapped.add(DtoMapper.serviceRequest(request, false));
            }
        }
        return single("requests", mapped);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMine(UUID userId, UUID requestId) {
        ServiceRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> ApiException.notFound("Request not found"));
        if (!request.getTenant().getId().equals(userId)) {
            throw ApiException.forbidden("You can only view your own requests.");
        }
        return single("request", DtoMapper.serviceRequest(request, false));
    }

    @Transactional
    public Map<String, Object> cancel(UUID userId, UUID requestId) {
        ServiceRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> ApiException.notFound("Request not found"));

        if (!request.getTenant().getId().equals(userId)) {
            throw ApiException.forbidden("You can only cancel your own requests.");
        }
        if (request.getStatus() != RequestStatus.PENDING && request.getStatus() != RequestStatus.IN_PROGRESS) {
            throw ApiException.notFound("Request not found or can no longer be cancelled.");
        }

        request.setStatus(RequestStatus.CANCELLED);
        request = requestRepository.save(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Request cancelled.");
        result.put("request", DtoMapper.serviceRequest(request, false));
        return result;
    }

    /** Pwede nang burahin ng tenant ang request kapag resolved na. */
    @Transactional
    public Map<String, Object> deleteMine(UUID userId, UUID requestId) {
        ServiceRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> ApiException.notFound("Request not found"));

        if (!request.getTenant().getId().equals(userId)) {
            throw ApiException.forbidden("You can only delete your own requests.");
        }
        if (request.getStatus() != RequestStatus.RESOLVED) {
            throw ApiException.badRequest("You can only delete a request once it has been resolved.");
        }

        requestRepository.delete(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Request deleted.");
        return result;
    }

    private Map<String, Object> single(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }
}
