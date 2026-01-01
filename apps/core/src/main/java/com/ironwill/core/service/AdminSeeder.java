package com.ironwill.core.service;

import com.ironwill.core.model.Role;
import com.ironwill.core.model.RoleType;
import com.ironwill.core.model.User;
import com.ironwill.core.repository.RoleRepository;
import com.ironwill.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("Admin seeding skipped: admin credentials not provided.");
            return;
        }

        Role adminRole = roleRepository.findByName(RoleType.ROLE_ADMIN)
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName(RoleType.ROLE_ADMIN);
                    return roleRepository.save(r);
                });
        roleRepository.findByName(RoleType.ROLE_USER)
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName(RoleType.ROLE_USER);
                    return roleRepository.save(r);
                });

        userRepository.findByEmail(adminEmail).ifPresentOrElse(
                u -> log.info("Admin user already exists: {}", adminEmail),
                () -> {
                    User u = new User();
                    u.setEmail(adminEmail);
                    u.setFullName("Admin");
                    u.setTimezone("UTC");
                    u.setAccountabilityScore(BigDecimal.valueOf(5.00));
                    u.setPasswordHash(passwordEncoder.encode(adminPassword));
                    u.setRoles(Set.of(adminRole));
                    userRepository.save(u);
                    log.info("Seeded admin user: {}", adminEmail);
                }
        );
    }
}

