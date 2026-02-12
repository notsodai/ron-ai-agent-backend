package com.ron.ronaiagent.integration;

import com.ron.ronaiagent.agent.coordinator.AgentManager;
import com.ron.ronaiagent.agent.coordinator.CollaborationPattern;
import com.ron.ronaiagent.agent.coordinator.TaskCoordinator;
import com.ron.ronaiagent.agent.communication.Message;
import com.ron.ronaiagent.agent.communication.MessageBus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class MultiAgentIntegrationTest {

    @Autowired
    private AgentManager agentManager;

    @Autowired
    private TaskCoordinator taskCoordinator;

    @Autowired
    private MessageBus messageBus;

    @Test
    void testAgentManagerIntegration() {
        assertTrue(agentManager.getAgentCount() > 0);
        assertTrue(agentManager.isAgentRegistered("file-processor"));
        assertTrue(agentManager.isAgentRegistered("search-agent"));
        assertTrue(agentManager.isAgentRegistered("analysis-agent"));
        assertTrue(agentManager.isAgentRegistered("coordinator"));
    }

    @Test
    void testMessageBusIntegration() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        String[] receivedContent = new String[1];

        messageBus.subscribe("test-topic", "test-agent", message -> {
            receivedContent[0] = message.getContent();
            latch.countDown();
        });

        Message testMessage = new Message.Builder()
            .from("sender")
            .to("receiver")
            .topic("test-topic")
            .content("Integration test message")
            .build();

        messageBus.publish("test-topic", testMessage);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("Integration test message", receivedContent[0]);
    }

    @Test
    void testChainPatternIntegration() {
        if (agentManager.getAgentCount() < 2) {
            return; // Skip if not enough agents registered
        }

        List<String> agentIds = agentManager.getAgentNames().stream()
            .limit(2)
            .toList();

        var result = taskCoordinator.execute(
            "Test integration task",
            CollaborationPattern.CHAIN,
            agentIds
        );

        assertNotNull(result);
        assertNotNull(result.getAgentOutputs());
    }

    @Test
    void testParallelPatternIntegration() {
        if (agentManager.getAgentCount() < 2) {
            return; // Skip if not enough agents registered
        }

        List<String> agentIds = agentManager.getAgentNames().stream()
            .limit(2)
            .toList();

        var result = taskCoordinator.execute(
            "Test parallel task",
            CollaborationPattern.PARALLEL,
            agentIds
        );

        assertNotNull(result);
        assertNotNull(result.getAgentOutputs());
    }
}