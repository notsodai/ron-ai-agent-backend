package com.ron.ronaiagent.chat.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebScrapingToolTest {

    @Test
    void scrapeWebPageShouldRejectPrivateAddress() {
        WebScrapingTool tool = new WebScrapingTool();
        String result = tool.scrapeWebPage("http://127.0.0.1:8080");
        assertTrue(result.contains("\"status\":\"error\""));
    }

    @Test
    void scrapeWebPageShouldRejectUnsupportedScheme() {
        WebScrapingTool tool = new WebScrapingTool();
        String result = tool.scrapeWebPage("file:///tmp/a.html");
        assertTrue(result.contains("\"status\":\"error\""));
    }
}
