package com.ron.ronaiagent.chat.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultTest {

    @Test
    void success_shouldCreateValidResult() {
        ToolResult result = ToolResult.success("File written to /tmp/test.txt");
        assertEquals("success", result.status());
        assertEquals("File written to /tmp/test.txt", result.summary());
        assertTrue(result.nextActions().isEmpty());
        assertTrue(result.artifacts().isEmpty());
        assertNull(result.errorMessage());
    }

    @Test
    void error_shouldCreateErrorResult() {
        ToolResult result = ToolResult.error("File not found: missing.txt");
        assertEquals("error", result.status());
        assertEquals("File not found: missing.txt", result.errorMessage());
        assertNull(result.summary());
    }

    @Test
    void withArtifacts_shouldIncludePaths() {
        ToolResult result = ToolResult.success("PDF generated")
                .withArtifacts(List.of("/tmp/output/report.pdf"));
        assertEquals(List.of("/tmp/output/report.pdf"), result.artifacts());
    }

    @Test
    void withNextActions_shouldIncludeSuggestions() {
        ToolResult result = ToolResult.success("Search completed")
                .withNextActions(List.of("Read the first result", "Refine search terms"));
        assertEquals(2, result.nextActions().size());
    }

    @Test
    void toJson_shouldReturnStructuredString() {
        ToolResult result = ToolResult.success("Done")
                .withArtifacts(List.of("/tmp/a.txt"));
        String json = result.toJson();
        assertTrue(json.contains("\"status\":\"success\""));
        assertTrue(json.contains("/tmp/a.txt"));
    }
}
