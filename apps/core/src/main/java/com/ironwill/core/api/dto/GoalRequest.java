package com.ironwill.core.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ironwill.core.model.FrequencyType;
import com.ironwill.core.model.GoalStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
public class GoalRequest {
    private String title;
    private LocalTime reviewTime;
    private FrequencyType frequencyType = FrequencyType.DAILY;
    private JsonNode criteriaConfig;
    private GoalStatus status = GoalStatus.ACTIVE;
}

