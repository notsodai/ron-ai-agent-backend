package com.ron.ronaiagent.agent;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;

public class ContextWindowManager {
    private final int maxTokens;
    private final double compactionThreshold;
    private static final int CHARS_PER_TOKEN = 4;

    public ContextWindowManager(int maxTokens, double compactionThreshold) {
        this.maxTokens = maxTokens;
        this.compactionThreshold = compactionThreshold;
    }

    public static ContextWindowManager defaultManager() {
        return new ContextWindowManager(4000, 0.8);
    }

    public int estimateTokenCount(List<Message> messages) {
        return messages.stream()
                .mapToInt(msg -> {
                    String text = msg.getText() != null ? msg.getText() : "";
                    return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
                })
                .sum();
    }

    public boolean shouldCompact(List<Message> messages) {
        int estimated = estimateTokenCount(messages);
        return estimated > maxTokens * compactionThreshold;
    }

    public List<Message> compact(List<Message> messages, int keepRecentPairs) {
        if (messages.size() <= keepRecentPairs * 2 + 1) {
            return messages;
        }

        List<Message> compacted = new ArrayList<>();

        // Keep system messages at the front
        for (Message msg : messages) {
            if (msg instanceof SystemMessage) {
                compacted.add(msg);
            }
        }

        // Add a compaction notice
        compacted.add(new SystemMessage(
                "[Context was compacted. Earlier conversation history has been summarized.]"));

        // Keep the most recent N message pairs
        int recentStart = messages.size() - keepRecentPairs * 2;
        if (recentStart < 0) {
            recentStart = 0;
        }
        for (int i = recentStart; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (!(msg instanceof SystemMessage)) {
                compacted.add(msg);
            }
        }

        return compacted;
    }
}
