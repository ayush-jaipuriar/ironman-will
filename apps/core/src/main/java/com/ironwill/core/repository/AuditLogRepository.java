package com.ironwill.core.repository;

import com.ironwill.core.model.AuditLog;
import com.ironwill.core.model.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Optional<AuditLog> findByGoalAndAuditDate(Goal goal, LocalDate auditDate);
}

