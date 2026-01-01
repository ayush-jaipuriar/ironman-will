package com.ironwill.core.service;

import com.ironwill.core.model.Goal;
import com.ironwill.core.model.GoalStatus;
import com.ironwill.core.model.User;
import com.ironwill.core.repository.GoalRepository;
import com.ironwill.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final UserRepository userRepository;
    private final GoalRepository goalRepository;

    private static final BigDecimal PASS_DELTA = BigDecimal.valueOf(0.5);
    private static final BigDecimal FAIL_DELTA = BigDecimal.valueOf(-0.2);
    private static final BigDecimal MISSED_DELTA = BigDecimal.valueOf(-1.0);
    private static final BigDecimal LOCK_THRESHOLD = BigDecimal.valueOf(3.0);

    @Transactional
    public void applyPass(User user) {
        applyDelta(user, PASS_DELTA);
    }

    @Transactional
    public void applyFail(User user) {
        applyDelta(user, FAIL_DELTA);
    }

    @Transactional
    public void applyMissed(User user) {
        applyDelta(user, MISSED_DELTA);
    }

    private void applyDelta(User user, BigDecimal delta) {
        user.setAccountabilityScore(user.getAccountabilityScore().add(delta));
        userRepository.save(user);
        if (user.getAccountabilityScore().compareTo(LOCK_THRESHOLD) < 0) {
            lockAllActiveGoals(user);
        }
    }

    private void lockAllActiveGoals(User user) {
        List<Goal> goals = goalRepository.findByUserAndStatus(user, GoalStatus.ACTIVE);
        OffsetDateTime lockUntil = OffsetDateTime.now().plusHours(24);
        goals.forEach(g -> {
            g.setStatus(GoalStatus.LOCKED);
            g.setLockedUntil(lockUntil);
        });
        goalRepository.saveAll(goals);
    }

    public BigDecimal getLockThreshold() {
        return LOCK_THRESHOLD;
    }
}

