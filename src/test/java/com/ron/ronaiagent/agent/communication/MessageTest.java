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
        assertEquals(original.getId(), reply.getReplyToMessageId());
    }

    @Test
    void testReplyValidationRequiresReplyToMessageId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Message.Builder()
                .from("agent1")
                .to("agent2")
                .topic("topic")
                .content("content")
                .reply(true)
                .build();
        });
    }

    @Test
    void testNonReplyValidationRequiresNullReplyToMessageId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Message.Builder()
                .from("agent1")
                .to("agent2")
                .topic("topic")
                .content("content")
                .reply(false)
                .replyToMessageId("some-id")
                .build();
        });
    }

    @Test
    void testBroadcastValidationRequiresNullTo() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Message.Builder()
                .from("agent1")
                .to("agent2")
                .topic("topic")
                .content("content")
                .broadcast(true)
                .build();
        });
    }

    @Test
    void testNonBroadcastValidationRequiresTo() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Message.Builder()
                .from("agent1")
                .topic("topic")
                .content("content")
                .broadcast(false)
                .build();
        });
    }

    @Test
    void testMetadataDefensiveCopy() {
        Map<String, Object> originalMetadata = Map.of("key1", "value1", "key2", "value2");
        Message message = new Message.Builder()
            .from("agent1")
            .to("agent2")
            .topic("topic")
            .content("content")
            .metadata(originalMetadata)
            .build();

        // Metadata should be accessible
        assertNotNull(message.getMetadata());
        assertEquals(2, message.getMetadata().size());

        // The returned map should be unmodifiable (Map.copyOf creates unmodifiable map)
        assertThrows(UnsupportedOperationException.class, () -> {
            message.getMetadata().put("key3", "value3");
        });
    }

    @Test
    void testToString() {
        Message message = new Message("agent1", "agent2", "topic", "content");
        String toString = message.toString();

        assertTrue(toString.contains("Message"));
        assertTrue(toString.contains("agent1"));
        assertTrue(toString.contains("agent2"));
        assertTrue(toString.contains("topic"));
    }
}
