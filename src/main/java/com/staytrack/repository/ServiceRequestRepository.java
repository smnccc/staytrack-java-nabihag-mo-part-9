package com.staytrack.repository;

import com.staytrack.domain.ServiceRequest;
import com.staytrack.domain.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    List<ServiceRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    void deleteByTenantId(UUID tenantId);

    @Query("select sr from ServiceRequest sr join fetch sr.tenant order by sr.createdAt desc")
    List<ServiceRequest> findAllWithTenant();

    long countByStatus(RequestStatus status);
}
