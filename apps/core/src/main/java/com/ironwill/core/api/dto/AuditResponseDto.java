package com.ironwill.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class AuditResponseDto {
    private String verdict;
    private String remarks;
    private Map<String, Object> extractedMetrics;
    private Double scoreImpact;
}

