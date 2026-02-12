package com.ron.ronaiagent.agent.coordinator;

import com.ron.ronaiagent.agent.BaseAgent;
import java.util.List;
import java.util.Optional;

public interface AgentManager {
    /**
     * Register an agent with a unique name
     */
    void registerAgent(String name, BaseAgent agent);

    /**
     * Unregister an agent by name
     */
    void unregisterAgent(String name);

    /**
     * Get an agent by name
     */
    Optional<BaseAgent> getAgent(String name);

    /**
     * Get all registered agent names
     */
    List<String> getAgentNames();

    /**
     * Get all registered agents
     */
    List<BaseAgent> getAllAgents();

    /**
     * Check if an agent is registered
     */
    boolean isAgentRegistered(String name);

    /**
     * Get agent count
     */
    int getAgentCount();
}
