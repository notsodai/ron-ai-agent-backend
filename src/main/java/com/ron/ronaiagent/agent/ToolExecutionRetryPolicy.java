package com.ron.ronaiagent.agent;

public class ToolExecutionRetryPolicy {
    private final int maxRetries;
    private final long initialBackoffMs;

    public ToolExecutionRetryPolicy(int maxRetries, long initialBackoffMs) {
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
    }

    public static ToolExecutionRetryPolicy defaultPolicy() {
        return new ToolExecutionRetryPolicy(2, 500);
    }

    public static ToolExecutionRetryPolicy noRetry() {
        return new ToolExecutionRetryPolicy(0, 0);
    }

    public boolean shouldRetry(int attemptNumber) {
        return attemptNumber <= maxRetries;
    }

    public long getBackoffMs(int attemptNumber) {
        return initialBackoffMs * (1L << (attemptNumber - 1));
    }
}
