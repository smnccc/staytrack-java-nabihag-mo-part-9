package com.staytrack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staytrack.domain.AuditLog;
import com.staytrack.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/** Naitatala dito yung mga ginawa ng admin. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(UUID actorId, String action, String entityType, UUID entityId, Map<String, Object> metadata) {
        String json = null;
        if (metadata != null) {
            try {
                json = objectMapper.writeValueAsString(metadata);
            } catch (Exception ex) {
                log.warn("Could not serialise audit metadata: {}", ex.getMessage());
            }
        }
        auditLogRepository.save(new AuditLog(actorId, action, entityType, entityId, json));
    }
}
