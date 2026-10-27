package com.staytrack.repository;

import com.staytrack.domain.OtpCode;
import com.staytrack.domain.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findTopByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(UUID userId, OtpPurpose purpose);

    List<OtpCode> findAllByUserIdAndPurposeAndConsumedAtIsNull(UUID userId, OtpPurpose purpose);

    Optional<OtpCode> findTopByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, OtpPurpose purpose);

    void deleteByUserId(UUID userId);
}
