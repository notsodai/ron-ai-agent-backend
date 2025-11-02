package com.ron.ronaiagent.chat.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author admin
 * @date 2025/9/14 下午3:16
 */
@Configuration
public class ToolRegistration {
    @Value("${spring.search-api.api-key}")
    private String searchApiKey;

    @Bean
    public ToolCallback[] allTools() {

        FileOperationTool fileOperationTool = new FileOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        ImageSearchTool imageSearchTool = new ImageSearchTool();
        TerminateTool terminateTool = new TerminateTool();

        return ToolCallbacks.from(fileOperationTool, pdfGenerationTool, resourceDownloadTool, webScrapingTool, webSearchTool, imageSearchTool, terminateTool);

    }
}
