package com.ironwill.core.security;

import com.ironwill.core.model.Role;
import com.ironwill.core.model.RoleType;
import com.ironwill.core.model.User;
import com.ironwill.core.repository.RoleRepository;
import com.ironwill.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attrs = oAuth2User.getAttributes();
        String email = (String) attrs.get("email");
        String name = (String) attrs.getOrDefault("name", email);

        Role userRole = roleRepository.findByName(RoleType.ROLE_USER)
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName(RoleType.ROLE_USER);
                    return roleRepository.save(r);
                });

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setFullName(name);
            u.setTimezone("UTC");
            u.setAccountabilityScore(BigDecimal.valueOf(5.00));
            u.setRoles(Set.of(userRole));
            return userRepository.save(u);
        });

        return new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority(RoleType.ROLE_USER.name())),
                attrs,
                "email"
        );
    }
}

