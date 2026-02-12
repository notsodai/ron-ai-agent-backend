package com.ron.ronaiagent.agent.coordinator;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents the result of a multi-agent coordination operation.
 *
 * <p>This class encapsulates the outcomes of agent coordination, including:
 * <ul>
 *   <li>Success/failure status</li>
 *   <li>Final output from the coordination</li>
 *   <li>Individual agent outputs</li>
 *   <li>Execution timing information</li>
 *   <li>Error details (if applicable)</li>
 * </ul>
 *
 * <p>Uses Builder pattern for flexible object construction.
 *
 * @see TaskCoordinator
 */
public class CoordinationResult {
    private final boolean success;
    private final String finalOutput;
    private final Map<String, String> agentOutputs;
    private final long executionTimeMs;
    private final LocalDateTime timestamp;
    private final String errorMessage;

    private CoordinationResult(Builder builder) {
        this.success = builder.success;
        this.finalOutput = builder.finalOutput;
        this.agentOutputs = builder.agentOutputs;
        this.executionTimeMs = builder.executionTimeMs;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.errorMessage = builder.errorMessage;
    }

    /**
     * Check if the coordination was successful.
     *
     * @return true if coordination succeeded, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Get the final output from the coordination.
     *
     * @return the final output string, or null if not available
     */
    public String getFinalOutput() {
        return finalOutput;
    }

    /**
     * Get the outputs from individual agents.
     *
     * @return a map of agent IDs to their outputs, or empty map if not available
     */
    public Map<String, String> getAgentOutputs() {
        return agentOutputs;
    }

    /**
     * Get the execution time in milliseconds.
     *
     * @return the time taken for coordination in milliseconds
     */
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    /**
     * Get the timestamp when the coordination completed.
     *
     * @return the completion timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Get the error message if coordination failed.
     *
     * @return the error message, or null if no error occurred
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Create a new builder for constructing CoordinationResult instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing CoordinationResult instances.
     */
    public static class Builder {
        private boolean success = true;
        private String finalOutput;
        private Map<String, String> agentOutputs = Map.of();
        private long executionTimeMs;
        private LocalDateTime timestamp;
        private String errorMessage;

        /**
         * Set the success status of the coordination.
         *
         * @param success true if successful, false otherwise
         * @return this builder for method chaining
         */
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        /**
         * Set the final output from the coordination.
         *
         * @param finalOutput the final output string
         * @return this builder for method chaining
         */
        public Builder finalOutput(String finalOutput) {
            this.finalOutput = finalOutput;
            return this;
        }

        /**
         * Set the outputs from individual agents.
         *
         * @param agentOutputs a map of agent IDs to their outputs
         * @return this builder for method chaining
         */
        public Builder agentOutputs(Map<String, String> agentOutputs) {
            this.agentOutputs = agentOutputs;
            return this;
        }

        /**
         * Set the execution time in milliseconds.
         *
         * @param executionTimeMs the time taken for coordination
         * @return this builder for method chaining
         */
        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        /**
         * Set the timestamp when the coordination completed.
         *
         * @param timestamp the completion timestamp
         * @return this builder for method chaining
         */
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Set the error message for failed coordination.
         *
         * @param errorMessage the error message
         * @return this builder for method chaining
         */
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        /**
         * Build the CoordinationResult instance.
         *
         * @return a new CoordinationResult with the configured values
         */
        public CoordinationResult build() {
            return new CoordinationResult(this);
        }
    }
}
