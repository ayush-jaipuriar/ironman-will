package com.ironwill.core.service;

import com.ironwill.core.model.Goal;
import com.ironwill.core.model.GoalStatus;
import com.ironwill.core.model.User;
import com.ironwill.core.repository.GoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;

    @Transactional(readOnly = true)
    public List<Goal> list(User user, GoalStatus status) {
        if (status != null) {
            return goalRepository.findByUserAndStatus(user, status);
        }
        return goalRepository.findByUser(user);
    }

    @Transactional
    public Goal create(Goal goal, User user) {
        goal.setUser(user);
        return goalRepository.save(goal);
    }

    @Transactional
    public Goal update(User user, UUID goalId, Goal updated) {
        Goal existing = goalRepository.findById(goalId)
                .filter(g -> g.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Goal not found for user"));
        existing.setTitle(updated.getTitle());
        existing.setReviewTime(updated.getReviewTime());
        existing.setFrequencyType(updated.getFrequencyType());
        existing.setCriteriaConfig(updated.getCriteriaConfig());
        existing.setStatus(updated.getStatus());
        existing.setLockedUntil(updated.getLockedUntil());
        return goalRepository.save(existing);
    }
}

