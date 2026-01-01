package com.ironwill.core.repository;

import com.ironwill.core.model.Notification;
import com.ironwill.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserAndReadFalse(User user);
}

