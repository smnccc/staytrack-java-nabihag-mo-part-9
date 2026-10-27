package com.staytrack.repository;

import com.staytrack.domain.Payment;
import com.staytrack.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    long countByStatus(PaymentStatus status);

    Optional<Payment> findByTenantIdAndBillingPeriod(UUID tenantId, LocalDate billingPeriod);

    Optional<Payment> findByIdAndTenantId(UUID id, UUID tenantId);

    void deleteByTenantId(UUID tenantId);

    @Query("select p from Payment p left join fetch p.room where p.tenant.id = :tenantId order by p.billingPeriod desc")
    List<Payment> findForTenant(@Param("tenantId") UUID tenantId);

    @Query("select p from Payment p join fetch p.tenant left join fetch p.room order by p.dueDate asc")
    List<Payment> findAllWithDetails();

    @Query("select coalesce(sum(p.totalAmount - p.amountPaid), 0) from Payment p "
        + "where p.tenant.id = :tenantId and p.status in :statuses")
    BigDecimal sumOutstandingForTenant(@Param("tenantId") UUID tenantId,
                                       @Param("statuses") Collection<PaymentStatus> statuses);

    @Query("select coalesce(sum(p.totalAmount - p.amountPaid), 0) from Payment p where p.status in :statuses")
    BigDecimal sumOutstanding(@Param("statuses") Collection<PaymentStatus> statuses);

    @Query("select coalesce(sum(p.amountPaid), 0) from Payment p where p.status = :status")
    BigDecimal sumAmountPaidByStatus(@Param("status") PaymentStatus status);

    @Query("select p.tenant.id, coalesce(sum(p.totalAmount - p.amountPaid), 0) from Payment p "
        + "where p.status in :statuses group by p.tenant.id")
    List<Object[]> sumOutstandingGroupedByTenant(@Param("statuses") Collection<PaymentStatus> statuses);
}
