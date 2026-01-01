package com.ironwill.core.api;

import com.ironwill.core.api.dto.GoalRequest;
import com.ironwill.core.api.dto.GoalResponse;
import com.ironwill.core.model.Goal;
import com.ironwill.core.model.GoalStatus;
import com.ironwill.core.model.User;
import com.ironwill.core.service.CurrentUserService;
import com.ironwill.core.service.GoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<List<GoalResponse>> list(@RequestParam(value = "status", required = false) GoalStatus status) {
        User user = currentUserService.requireCurrentUser();
        List<GoalResponse> goals = goalService.list(user, status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(goals);
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(@RequestBody GoalRequest request) {
        User user = currentUserService.requireCurrentUser();
        Goal goal = toEntity(request);
        Goal saved = goalService.create(goal, user);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> update(@PathVariable UUID id, @RequestBody GoalRequest request) {
        User user = currentUserService.requireCurrentUser();
        Goal updated = goalService.update(user, id, toEntity(request));
        return ResponseEntity.ok(toResponse(updated));
    }

    private Goal toEntity(GoalRequest req) {
        Goal g = new Goal();
        g.setTitle(req.getTitle());
        g.setReviewTime(req.getReviewTime());
        g.setFrequencyType(req.getFrequencyType());
        g.setCriteriaConfig(req.getCriteriaConfig());
        g.setStatus(req.getStatus());
        return g;
    }

    private GoalResponse toResponse(Goal g) {
        return new GoalResponse(
                g.getId(),
                g.getTitle(),
                g.getReviewTime(),
                g.getFrequencyType(),
                g.getStatus(),
                g.getCriteriaConfig()
        );
    }
}

