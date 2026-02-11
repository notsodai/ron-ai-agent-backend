package com.ron.ronaiagent.chat.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ImageSearchTool {

    @Value("${pexels.api.key:}")
    private String apiKey;

    private static final String API_URL = "https://api.pexels.com/v1/search";

    @Tool(description = "Search for images from the web based on keywords")
    public String searchImages(@ToolParam(description = "Search query keywords for images") String query) {
        if (StrUtil.isBlank(apiKey)) {
            return "Error searching images: missing pexels api key";
        }
        try {
            List<String> imageUrls = searchMediumImages(query);
            if (imageUrls.isEmpty()) {
                return "No images found for query: " + query;
            }
            return String.join(",", imageUrls);
        } catch (Exception e) {
            return "Error searching images: " + e.getMessage();
        }
    }

    public List<String> searchMediumImages(String query) {
        if (StrUtil.isBlank(query) || StrUtil.isBlank(apiKey)) {
            return List.of();
        }
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", apiKey);

            Map<String, Object> params = new HashMap<>();
            params.put("query", query);
            params.put("per_page", 10);

            String response = HttpUtil.createGet(API_URL)
                    .addHeaders(headers)
                    .form(params)
                    .execute()
                    .body();

            if (StrUtil.isBlank(response)) {
                return List.of();
            }

            JSONObject jsonResponse = JSONUtil.parseObj(response);
            if (jsonResponse.containsKey("photos") && jsonResponse.getJSONArray("photos") != null) {
                return jsonResponse.getJSONArray("photos")
                        .stream()
                        .map(photoObj -> (JSONObject) photoObj)
                        .map(photoObj -> photoObj.getJSONObject("src"))
                        .filter(Objects::nonNull)
                        .map(photo -> photo.getStr("medium"))
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            return List.of();
        }
        return List.of();
    }
}
