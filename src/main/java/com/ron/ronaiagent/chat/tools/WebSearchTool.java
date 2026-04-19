package com.ron.ronaiagent.chat.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WebSearchTool {

    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";
    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(@ToolParam(description = "Search query keyword") String query) {
        if (StrUtil.isBlank(query)) {
            return ToolResult.error("Search query cannot be blank").toJson();
        }
        if (StrUtil.isBlank(apiKey)) {
            return ToolResult.error("Missing search API key").toJson();
        }

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");

        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            JSONObject jsonObject = JSONUtil.parseObj(response);
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (organicResults == null || organicResults.isEmpty()) {
                return ToolResult.success("No results found for: " + query)
                        .withNextActions(List.of("Try different search terms"))
                        .toJson();
            }

            int resultSize = Math.min(5, organicResults.size());
            List<Object> objects = organicResults.subList(0, resultSize);
            String results = objects.stream()
                    .map(obj -> (JSONObject) obj)
                    .map(JSONObject::toString)
                    .collect(Collectors.joining(","));
            return ToolResult.success("Found " + resultSize + " results for: " + query + "\n" + results)
                    .withNextActions(List.of("Read a specific result", "Refine search terms", "Scrape a result page"))
                    .toJson();
        } catch (Exception e) {
            return ToolResult.error("Search failed: " + e.getMessage()).toJson();
        }
    }
}
