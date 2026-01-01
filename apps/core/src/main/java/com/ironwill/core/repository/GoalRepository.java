package com.ironwill.core.repository;

import com.ironwill.core.model.Goal;
import com.ironwill.core.model.GoalStatus;
import com.ironwill.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    List<Goal> findByUserAndStatus(User user, GoalStatus status);
    List<Goal> findByUser(User user);
}

