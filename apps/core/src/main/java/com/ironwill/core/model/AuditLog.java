package com.ironwill.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "audit_logs", uniqueConstraints = {
        @UniqueConstraint(name = "uq_goal_date", columnNames = {"goal_id", "audit_date"})
})
public class AuditLog {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @Column(name = "audit_date", nullable = false)
    private LocalDate auditDate;

    @Column(columnDefinition = "text")
    private String proofUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditStatus status = AuditStatus.PENDING;

    @Column(columnDefinition = "text")
    private String agentRemarks;

    @Column(precision = 4, scale = 2)
    private BigDecimal scoreImpact;

    @Column
    private OffsetDateTime submittedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private OffsetDateTime createdAt;
}

