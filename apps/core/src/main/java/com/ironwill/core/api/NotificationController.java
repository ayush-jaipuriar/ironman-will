package com.ironwill.core.api;

import com.ironwill.core.model.Notification;
import com.ironwill.core.model.User;
import com.ironwill.core.repository.NotificationRepository;
import com.ironwill.core.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    @GetMapping("/unread")
    public ResponseEntity<List<Notification>> unread() {
        User user = currentUserService.requireCurrentUser();
        List<Notification> unread = notificationRepository.findByUserAndReadFalse(user);
        return ResponseEntity.ok(unread);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, String>> markRead(@PathVariable UUID id) {
        User user = currentUserService.requireCurrentUser();
        Notification n = notificationRepository.findById(id)
                .filter(notif -> notif.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        n.setRead(true);
        notificationRepository.save(n);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllRead() {
        User user = currentUserService.requireCurrentUser();
        List<Notification> unread = notificationRepository.findByUserAndReadFalse(user);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}

