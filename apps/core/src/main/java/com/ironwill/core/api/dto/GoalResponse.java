package com.ironwill.core.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ironwill.core.model.FrequencyType;
import com.ironwill.core.model.GoalStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class GoalResponse {
    private UUID id;
    private String title;
    private LocalTime reviewTime;
    private FrequencyType frequencyType;
    private GoalStatus status;
    private JsonNode criteriaConfig;
}

