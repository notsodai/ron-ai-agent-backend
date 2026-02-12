package com.ron.ronaiagent.agent.communication;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Map;

class MessageTest {
    @Test
    void testMessageCreation() {
        Message message = new Message(
            "agent1",
            "agent2",
            "test-topic",
            "Test content",
            Map.of("key", "value")
        );

        assertEquals("agent1", message.getFrom());
        assertEquals("agent2", message.getTo());
        assertEquals("test-topic", message.getTopic());
        assertEquals("Test content", message.getContent());
        assertFalse(message.isBroadcast());
        assertNotNull(message.getTimestamp());
    }

    @Test
    void testBroadcastMessage() {
        Message message = Message.createBroadcast(
            "agent1",
            "test-topic",
            "Broadcast content"
        );

        assertEquals("agent1", message.getFrom());
        assertTrue(message.isBroadcast());
        assertEquals("test-topic", message.getTopic());
    }

    @Test
    void testMessageWithReplyTo() {
        Message original = new Message("agent1", "agent2", "topic", "content");
        Message reply = original.createReply("Reply content");

        assertEquals("agent2", reply.getFrom());
        assertEquals("agent1", reply.getTo());
        assertEquals("topic", reply.getTopic());
        assertEquals("Reply content", reply.getContent());
        assertTrue(reply.isReply());
    }
}
