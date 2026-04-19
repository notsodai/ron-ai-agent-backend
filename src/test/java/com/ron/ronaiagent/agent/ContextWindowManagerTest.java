package com.ron.ronaiagent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextWindowManagerTest {

    @Test
    void estimateTokenCount_shouldReturnNonZero() {
        ContextWindowManager manager = new ContextWindowManager(4000, 0.8);
        List<Message> messages = List.of(new UserMessage("Hello, how are you?"));
        int tokens = manager.estimateTokenCount(messages);
        assertTrue(tokens > 0);
    }

    @Test
    void shouldCompact_shouldReturnTrue_whenOverThreshold() {
        ContextWindowManager manager = new ContextWindowManager(100, 0.8);
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            messages.add(new UserMessage("This is a somewhat longer message to consume tokens number " + i));
        }
        assertTrue(manager.shouldCompact(messages));
    }

    @Test
    void shouldCompact_shouldReturnFalse_whenUnderThreshold() {
        ContextWindowManager manager = new ContextWindowManager(10000, 0.8);
        List<Message> messages = List.of(new UserMessage("Short message"));
        assertFalse(manager.shouldCompact(messages));
    }

    @Test
    void compact_shouldKeepSystemAndRecentMessages() {
        ContextWindowManager manager = new ContextWindowManager(100, 0.8);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("You are a helpful assistant."));
        for (int i = 0; i < 20; i++) {
            messages.add(new UserMessage("Message " + i));
            messages.add(new AssistantMessage("Response " + i));
        }
        List<Message> compacted = manager.compact(messages, 4);
        assertTrue(compacted.size() < messages.size());
        // First non-system message should be the compaction notice
        assertTrue(compacted.stream().anyMatch(m -> m instanceof SystemMessage && m.getText().contains("compacted")));
        // Last message should be a recent assistant message
        assertTrue(compacted.getLast() instanceof AssistantMessage);
        assertTrue(compacted.getLast().getText().startsWith("Response"));
    }

    @Test
    void compact_shouldNotCompact_whenBelowMinimumSize() {
        ContextWindowManager manager = new ContextWindowManager(10000, 0.8);
        List<Message> messages = List.of(
                new UserMessage("Hi"),
                new AssistantMessage("Hello!"));
        List<Message> compacted = manager.compact(messages, 4);
        assertEquals(messages.size(), compacted.size());
    }

    @Test
    void defaultManager_shouldHaveReasonableDefaults() {
        ContextWindowManager manager = ContextWindowManager.defaultManager();
        List<Message> messages = List.of(new UserMessage("Short message"));
        assertFalse(manager.shouldCompact(messages));
    }
}
