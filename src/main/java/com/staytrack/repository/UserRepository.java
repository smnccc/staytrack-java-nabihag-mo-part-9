package com.staytrack.repository;

import com.staytrack.domain.User;
import com.staytrack.domain.enums.Role;
import com.staytrack.domain.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByRoleOrderByCreatedAtDesc(Role role);

    long countByRole(Role role);

    long countByRoleAndStatus(Role role, UserStatus status);
}
