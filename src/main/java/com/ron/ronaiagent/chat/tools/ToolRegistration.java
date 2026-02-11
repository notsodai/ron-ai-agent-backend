package com.ron.ronaiagent.chat.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegistration {

    @Bean
    public FileOperationTool fileOperationTool() {
        return new FileOperationTool();
    }

    @Bean
    public PDFGenerationTool pdfGenerationTool() {
        return new PDFGenerationTool();
    }

    @Bean
    public ResourceDownloadTool resourceDownloadTool() {
        return new ResourceDownloadTool();
    }

    @Bean
    public WebScrapingTool webScrapingTool() {
        return new WebScrapingTool();
    }

    @Bean
    public WebSearchTool webSearchTool(@Value("${spring.search-api.api-key:}") String searchApiKey) {
        return new WebSearchTool(searchApiKey);
    }

    @Bean
    public TerminateTool terminateTool() {
        return new TerminateTool();
    }

    @Bean
    public ToolCallback[] allTools(
            FileOperationTool fileOperationTool,
            PDFGenerationTool pdfGenerationTool,
            ResourceDownloadTool resourceDownloadTool,
            WebScrapingTool webScrapingTool,
            WebSearchTool webSearchTool,
            ImageSearchTool imageSearchTool,
            TerminateTool terminateTool) {
        return ToolCallbacks.from(
                fileOperationTool,
                pdfGenerationTool,
                resourceDownloadTool,
                webScrapingTool,
                webSearchTool,
                imageSearchTool,
                terminateTool);
    }
}
