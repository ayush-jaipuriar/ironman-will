package com.ironwill.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class UserProfileResponse {
    private String email;
    private String fullName;
    private String timezone;
    private BigDecimal accountabilityScore;
    private boolean locked;
    private OffsetDateTime lockedUntil;
    private List<String> roles;
}

