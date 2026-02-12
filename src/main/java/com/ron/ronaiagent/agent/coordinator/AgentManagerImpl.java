package com.ron.ronaiagent.agent.coordinator;

import com.ron.ronaiagent.agent.BaseAgent;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe implementation of AgentManager using ConcurrentHashMap.
 * Manages agent registration, unregistration, and query operations.
 *
 * @author admin
 * @date 2025/10/12
 */
@Component
public class AgentManagerImpl implements AgentManager {

    private final Map<String, BaseAgent> agents = new ConcurrentHashMap<>();

    @Override
    public void registerAgent(String name, BaseAgent agent) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent name cannot be null or empty");
        }
        if (agent == null) {
            throw new IllegalArgumentException("Agent cannot be null");
        }
        agents.put(name, agent);
    }

    @Override
    public void unregisterAgent(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Agent name cannot be null");
        }
        agents.remove(name);
    }

    @Override
    public Optional<BaseAgent> getAgent(String name) {
        return Optional.ofNullable(agents.get(name));
    }

    @Override
    public List<String> getAgentNames() {
        return new ArrayList<>(agents.keySet());
    }

    @Override
    public List<BaseAgent> getAllAgents() {
        return new ArrayList<>(agents.values());
    }

    @Override
    public boolean isAgentRegistered(String name) {
        return agents.containsKey(name);
    }

    @Override
    public int getAgentCount() {
        return agents.size();
    }
}
