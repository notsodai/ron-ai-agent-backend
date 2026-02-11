package com.ron.ronaiagent.chat.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceDownloadToolTest {

    @Test
    void downloadResourceShouldRejectPrivateAddress() {
        ResourceDownloadTool tool = new ResourceDownloadTool();
        String result = tool.downloadResource("http://127.0.0.1:8080/a.txt", "a.txt");
        assertTrue(result.startsWith("Error downloading resource:"));
    }

    @Test
    void downloadResourceShouldRejectInvalidFileName() {
        ResourceDownloadTool tool = new ResourceDownloadTool();
        String result = tool.downloadResource("https://example.com/a.txt", "../a.txt");
        assertTrue(result.startsWith("Error downloading resource:"));
    }
}
