package com.ron.ronaiagent.persistence;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentExecutionRecord {
    private Long id;
    private String agentName;
    private String prompt;
    private String status;
    private int currentStep;
    private int maxSteps;
    private String result;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String errorMessage;
}
