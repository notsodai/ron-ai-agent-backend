package com.ron.ronaiagent.chat.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.URI;
import java.util.List;

public class WebScrapingTool {

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            URI safeUri = ToolSecurityUtils.validatePublicHttpUrl(url);
            Document doc = Jsoup.connect(safeUri.toString())
                    .timeout(10_000)
                    .get();
            return ToolResult.success("Scraped content from: " + safeUri + "\n" + doc.html())
                    .withNextActions(List.of("Extract specific information", "Summarize the page content"))
                    .toJson();
        } catch (Exception e) {
            return ToolResult.error("Error scraping web page: " + e.getMessage()).toJson();
        }
    }
}
