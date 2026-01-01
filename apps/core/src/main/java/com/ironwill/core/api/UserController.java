package com.ironwill.core.api;

import com.ironwill.core.model.User;
import com.ironwill.core.repository.UserRepository;
import com.ironwill.core.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    public record TimezoneRequest(String timezone) {}

    @PutMapping("/timezone")
    public ResponseEntity<Map<String, String>> updateTimezone(@RequestBody TimezoneRequest req) {
        User user = currentUserService.requireCurrentUser();
        user.setTimezone(req.timezone());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("timezone", req.timezone()));
    }
}

