package com.ron.ronaiagent.agent.coordinator;

import com.ron.ronaiagent.agent.BaseAgent;
import com.ron.ronaiagent.agent.AgentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

class AgentManagerImplTest {
    private AgentManager agentManager;

    @BeforeEach
    void setUp() {
        agentManager = new AgentManagerImpl();
    }

    @Test
    void testRegisterAndGetAgent() {
        BaseAgent mockAgent = createMockAgent("test-agent");

        agentManager.registerAgent("agent1", mockAgent);

        Optional<BaseAgent> retrieved = agentManager.getAgent("agent1");
        assertTrue(retrieved.isPresent());
        assertEquals("test-agent", retrieved.get().getName());
    }

    @Test
    void testUnregisterAgent() {
        BaseAgent mockAgent = createMockAgent("agent1");
        agentManager.registerAgent("agent1", mockAgent);

        assertTrue(agentManager.isAgentRegistered("agent1"));

        agentManager.unregisterAgent("agent1");

        assertFalse(agentManager.isAgentRegistered("agent1"));
        assertTrue(agentManager.getAgent("agent1").isEmpty());
    }

    @Test
    void testGetAllAgents() {
        BaseAgent agent1 = createMockAgent("agent1");
        BaseAgent agent2 = createMockAgent("agent2");

        agentManager.registerAgent("agent1", agent1);
        agentManager.registerAgent("agent2", agent2);

        assertEquals(2, agentManager.getAgentCount());
        assertEquals(2, agentManager.getAllAgents().size());
        assertTrue(agentManager.getAgentNames().contains("agent1"));
        assertTrue(agentManager.getAgentNames().contains("agent2"));
    }

    @Test
    void testDuplicateRegistration() {
        BaseAgent agent1 = createMockAgent("agent1");
        BaseAgent agent2 = createMockAgent("agent2");

        agentManager.registerAgent("agent1", agent1);

        // Should replace the existing agent
        agentManager.registerAgent("agent1", agent2);

        Optional<BaseAgent> retrieved = agentManager.getAgent("agent1");
        assertTrue(retrieved.isPresent());
        assertEquals("agent2", retrieved.get().getName());
    }

    private BaseAgent createMockAgent(String agentName) {
        return new BaseAgent() {
            {
                setName(agentName);
            }

            @Override
            public String step() {
                return "Mock step result";
            }

            @Override
            public void cleanup() {
                // Mock cleanup
            }
        };
    }
}
