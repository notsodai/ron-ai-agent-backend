package com.ron.ronaiagent.agent;

public interface AgentExecutionListener {
    void onExecutionStart(String agentName, String prompt, int maxSteps);
    void onStepComplete(String agentName, int step, String result);
    void onExecutionComplete(String agentName, AgentState finalState, String result, long durationMs);
}
