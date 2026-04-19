package com.ron.ronaiagent.agent;

import com.ron.ronaiagent.persistence.AgentExecutionRecord;
import com.ron.ronaiagent.persistence.AgentExecutionRepository;

import java.time.LocalDateTime;

public class JdbcAgentExecutionListener implements AgentExecutionListener {
    private final AgentExecutionRepository repository;
    private final ThreadLocal<Long> currentExecutionId = new ThreadLocal<>();

    public JdbcAgentExecutionListener(AgentExecutionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onExecutionStart(String agentName, String prompt, int maxSteps) {
        AgentExecutionRecord record = AgentExecutionRecord.builder()
                .agentName(agentName)
                .prompt(prompt)
                .status("RUNNING")
                .maxSteps(maxSteps)
                .currentStep(0)
                .startTime(LocalDateTime.now())
                .build();
        Long id = repository.save(record);
        currentExecutionId.set(id);
    }

    @Override
    public void onStepComplete(String agentName, int step, String result) {
        Long id = currentExecutionId.get();
        if (id != null) {
            repository.updateStatus(id, "RUNNING", step, result);
        }
    }

    @Override
    public void onExecutionComplete(String agentName, AgentState finalState, String result, long durationMs) {
        Long id = currentExecutionId.get();
        if (id != null) {
            repository.updateStatus(id, finalState.name(), -1, result);
            currentExecutionId.remove();
        }
    }
}
