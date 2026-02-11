package com.ron.ronaiagent.chat.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSearchToolTest {

    @Test
    void searchWebShouldRejectBlankQuery() {
        WebSearchTool tool = new WebSearchTool("dummy-key");
        String result = tool.searchWeb(" ");
        assertEquals("Error searching Baidu: query cannot be blank", result);
    }

    @Test
    void searchWebShouldRejectMissingApiKey() {
        WebSearchTool tool = new WebSearchTool("");
        String result = tool.searchWeb("ai agent");
        assertEquals("Error searching Baidu: missing search api key", result);
    }
}
