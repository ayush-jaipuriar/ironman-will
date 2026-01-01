package com.ironwill.core.api;

import com.ironwill.core.api.dto.AuthRequest;
import com.ironwill.core.api.dto.AuthResponse;
import com.ironwill.core.api.dto.UserProfileResponse;
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
        var profile = new UserProfileResponse(ud.getUsername());
        return ResponseEntity.ok(profile);
    }
}

