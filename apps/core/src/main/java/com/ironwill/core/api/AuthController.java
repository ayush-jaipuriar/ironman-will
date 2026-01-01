package com.ironwill.core.api;

import com.ironwill.core.api.dto.AuthRequest;
import com.ironwill.core.api.dto.AuthResponse;
import com.ironwill.core.api.dto.UserProfileResponse;
import com.ironwill.core.model.GoalStatus;
import com.ironwill.core.model.User;
import com.ironwill.core.repository.UserRepository;
import com.ironwill.core.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", auth.getAuthorities());

        String token = jwtService.generate(request.getEmail(), claims);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PostMapping("/me")
    public ResponseEntity<UserProfileResponse> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails ud)) {
            return ResponseEntity.status(401).build();
        }
        User user = userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        boolean locked = user.getGoals().stream().anyMatch(g -> g.getStatus() == GoalStatus.LOCKED);
        var profile = new UserProfileResponse(
                user.getEmail(),
                user.getFullName(),
                user.getTimezone(),
                user.getAccountabilityScore(),
                locked,
                user.getGoals().stream()
                        .filter(g -> g.getLockedUntil() != null)
                        .map(g -> g.getLockedUntil())
                        .max(java.time.OffsetDateTime::compareTo)
                        .orElse(null),
                user.getRoles().stream().map(r -> r.getName().name()).toList()
        );
        return ResponseEntity.ok(profile);
    }
}

