package com.ironwill.core.service;

import com.ironwill.core.model.AuditLog;
import com.ironwill.core.model.Goal;
import com.ironwill.core.model.GoalStatus;
import com.ironwill.core.model.User;
import com.ironwill.core.repository.AuditLogRepository;
import com.ironwill.core.repository.GoalRepository;
import com.ironwill.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class NagScheduler {

    private static final Logger log = LoggerFactory.getLogger(NagScheduler.class);

    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final NotificationService notificationService;
    private final AuditLogRepository auditLogRepository;

    @Scheduled(cron = "0 0/15 * * * *") // every 15 minutes
    public void runNag() {
        List<User> users = userRepository.findAll();
        LocalDate today = LocalDate.now();

        for (User user : users) {
            ZoneId zoneId = ZoneId.of(user.getTimezone());
            ZonedDateTime nowUser = ZonedDateTime.now(zoneId);
            LocalTime nowTime = nowUser.toLocalTime();
            // Skip night hours (e.g., 23:00 - 06:00 local) to avoid spamming during sleep
            if (nowTime.isAfter(LocalTime.of(23, 0)) || nowTime.isBefore(LocalTime.of(6, 0))) {
                continue;
            }

            List<Goal> activeGoals = goalRepository.findByUserAndStatus(user, GoalStatus.ACTIVE);
            for (Goal goal : activeGoals) {
                // If current local time is after review_time and there's no audit for today, nag
                if (nowTime.isAfter(goal.getReviewTime())) {
                    boolean hasAuditToday = auditLogRepository.findByGoalAndAuditDate(goal, today).isPresent();
                    if (!hasAuditToday) {
                        notificationService.notify(user, "Pending audit for: " + goal.getTitle());
                    }
                }
            }
        }
        log.info("Nag scheduler completed");
    }
}

