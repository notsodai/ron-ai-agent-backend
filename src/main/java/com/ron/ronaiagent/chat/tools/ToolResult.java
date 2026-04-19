package com.ron.ronaiagent.chat.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

public record ToolResult(
        String status,
        String summary,
        String errorMessage,
        List<String> nextActions,
        List<String> artifacts
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ToolResult {
        nextActions = nextActions == null ? Collections.emptyList() : List.copyOf(nextActions);
        artifacts = artifacts == null ? Collections.emptyList() : List.copyOf(artifacts);
    }

    public static ToolResult success(String summary) {
        return new ToolResult("success", summary, null, List.of(), List.of());
    }

    public static ToolResult error(String errorMessage) {
        return new ToolResult("error", null, errorMessage, List.of(), List.of());
    }

    public static ToolResult warning(String summary) {
        return new ToolResult("warning", summary, null, List.of(), List.of());
    }

    public ToolResult withNextActions(List<String> actions) {
        return new ToolResult(status, summary, errorMessage, actions, artifacts);
    }

    public ToolResult withArtifacts(List<String> paths) {
        return new ToolResult(status, summary, errorMessage, nextActions, paths);
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"error\",\"errorMessage\":\"JSON serialization failed\"}";
        }
    }
}
