package com.ironwill.core.api;

import com.ironwill.core.api.dto.AuditResponseDto;
import com.ironwill.core.client.AgentClient;
import com.ironwill.core.model.*;
import com.ironwill.core.repository.AuditLogRepository;
import com.ironwill.core.repository.GoalRepository;
import com.ironwill.core.service.CurrentUserService;
import com.ironwill.core.service.ScoreService;
import com.ironwill.core.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/goals/{goalId}/audit")
@RequiredArgsConstructor
public class AuditController {

    private final CurrentUserService currentUserService;
    private final GoalRepository goalRepository;
    private final AuditLogRepository auditLogRepository;
    private final StorageService storageService;
    private final AgentClient agentClient;
    private final ScoreService scoreService;

    private static final long MAX_BYTES = 5 * 1024 * 1024;

    @PostMapping
    @Transactional
    public ResponseEntity<AuditResponseDto> submit(@PathVariable UUID goalId, MultipartFile file) throws IOException {
        User user = currentUserService.requireCurrentUser();
        Goal goal = goalRepository.findById(goalId)
                .filter(g -> g.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        if (goal.getStatus() != GoalStatus.ACTIVE) {
            return ResponseEntity.status(423).build(); // Locked or archived
        }
        if (user.getAccountabilityScore().compareTo(scoreServiceThreshold()) < 0) {
            return ResponseEntity.status(423).build();
        }

        validateFile(file);

        String proofUrl = storageService.uploadProof(user.getId(), goal.getId(), file);

        AgentClient.AgentRequest req = new AgentClient.AgentRequest();
        req.setRequest_id(UUID.randomUUID().toString());
        req.setUser_id(user.getId().toString());
        req.setGoal_id(goal.getId().toString());
        Map<String, Object> goalCtx = new HashMap<>();
        goalCtx.put("title", goal.getTitle());
        req.setGoal_context(goalCtx);
        req.setCriteria(Map.of(
                "config", goal.getCriteriaConfig()
        ));
        req.setProof_url(proofUrl);
        req.setTimezone(user.getTimezone());
        req.setCurrent_time_local(OffsetDateTime.now().toString());

        AgentClient.AgentResponse agentResp = agentClient.audit(req);

        AuditStatus status = AuditStatus.PENDING;
        double delta = 0.0;
        String remarks = null;
        Map<String, Object> extracted = null;

        if (agentResp != null) {
            status = "PASS".equalsIgnoreCase(agentResp.getVerdict()) ? AuditStatus.VERIFIED : AuditStatus.REJECTED;
            delta = agentResp.getScore_impact() != null ? agentResp.getScore_impact() : (status == AuditStatus.VERIFIED ? 0.5 : -0.2);
            remarks = agentResp.getRemarks();
            extracted = agentResp.getExtracted_metrics();
        } else {
            // Agent failure: treat as technical difficulty, no penalty, do not change status
            status = AuditStatus.PENDING;
            delta = 0.0;
            remarks = "Agent unavailable. Please retry.";
        }

        AuditLog log = auditLogRepository.findByGoalAndAuditDate(goal, LocalDate.now())
                .orElseGet(AuditLog::new);
        log.setGoal(goal);
        log.setAuditDate(LocalDate.now());
        log.setProofUrl(proofUrl);
        log.setStatus(status);
        log.setAgentRemarks(remarks);
        log.setScoreImpact(BigDecimal.valueOf(delta));
        log.setSubmittedAt(OffsetDateTime.now());
        auditLogRepository.save(log);

        if (status == AuditStatus.VERIFIED) {
            scoreService.applyPass(user);
        } else if (status == AuditStatus.REJECTED) {
            scoreService.applyFail(user);
        } // PENDING/tech difficulty: no score change

        return ResponseEntity.ok(new AuditResponseDto(
                status == AuditStatus.VERIFIED ? "PASS" : status == AuditStatus.REJECTED ? "FAIL" : "TECHNICAL_DIFFICULTY",
                remarks,
                extracted,
                delta
        ));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File too large");
        }
        String contentType = file.getContentType();
        if (contentType == null ||
                !(contentType.equals(MimeTypeUtils.IMAGE_JPEG_VALUE) || contentType.equals(MimeTypeUtils.IMAGE_PNG_VALUE))) {
            throw new IllegalArgumentException("Unsupported file type");
        }
    }

    private java.math.BigDecimal scoreServiceThreshold() {
        return java.math.BigDecimal.valueOf(3.0);
    }
}

