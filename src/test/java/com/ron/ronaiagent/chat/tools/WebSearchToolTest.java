package com.ron.ronaiagent.chat.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchToolTest {

    @Test
    void searchWebShouldRejectBlankQuery() {
        WebSearchTool tool = new WebSearchTool("dummy-key");
        String result = tool.searchWeb(" ");
        assertTrue(result.contains("\"status\":\"error\""));
    }

    @Test
    void searchWebShouldRejectMissingApiKey() {
        WebSearchTool tool = new WebSearchTool("");
        String result = tool.searchWeb("ai agent");
        assertTrue(result.contains("\"status\":\"error\""));
    }
}
