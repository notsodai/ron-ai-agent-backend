package com.ron.ronaiagent.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionRetryPolicyTest {

    @Test
    void shouldRetry_onFirstFailure() {
        ToolExecutionRetryPolicy policy = new ToolExecutionRetryPolicy(3, 100);
        assertTrue(policy.shouldRetry(1));
    }

    @Test
    void shouldNotRetry_whenMaxAttemptsExceeded() {
        ToolExecutionRetryPolicy policy = new ToolExecutionRetryPolicy(3, 100);
        assertTrue(policy.shouldRetry(3));
        assertFalse(policy.shouldRetry(4));
    }

    @Test
    void getBackoffMs_shouldIncreaseExponentially() {
        ToolExecutionRetryPolicy policy = new ToolExecutionRetryPolicy(3, 100);
        assertEquals(100, policy.getBackoffMs(1));
        assertEquals(200, policy.getBackoffMs(2));
        assertEquals(400, policy.getBackoffMs(3));
    }

    @Test
    void defaultPolicy_shouldHaveSaneDefaults() {
        ToolExecutionRetryPolicy policy = ToolExecutionRetryPolicy.defaultPolicy();
        assertTrue(policy.shouldRetry(1));
        assertTrue(policy.shouldRetry(2));
        assertFalse(policy.shouldRetry(3));
    }

    @Test
    void noRetry_shouldNeverRetry() {
        ToolExecutionRetryPolicy policy = ToolExecutionRetryPolicy.noRetry();
        assertFalse(policy.shouldRetry(1));
        assertEquals(0, policy.getBackoffMs(1));
    }
}
