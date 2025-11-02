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

/**
 * 图片搜索工具 - 集成Pexels API功能
 * @author admin
 * @date 2025/11/2 下午8:00
 */
@Component
public class ImageSearchTool {

    // 使用配置文件中的API密钥
    @Value("${pexels.api.key:81O6Ef1MSfsbUkAeeoowEMfqjTVFyDO7lg9Feg7NwIjfA6sRHpX9h1FX}")
    private String apiKey;

    // Pexels API URL
    private static final String API_URL = "https://api.pexels.com/v1/search";

    @Tool(description = "Search for images from the web based on keywords")
    public String searchImages(@ToolParam(description = "Search query keywords for images") String query) {
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

    /**
     * 搜索中等尺寸的图片列表
     *
     * @param query 查询关键字
     * @return 图片URL列表
     */
    public List<String> searchMediumImages(String query) {
        if (StrUtil.isBlank(query)) {
            return List.of();
        }

        try {
            // 设置请求头（包含API密钥）
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", apiKey);

            // 设置请求参数
            Map<String, Object> params = new HashMap<>();
            params.put("query", query);
            params.put("per_page", 10); // 限制返回数量

            // 发送 GET 请求
            String response = HttpUtil.createGet(API_URL)
                    .addHeaders(headers)
                    .form(params)
                    .execute()
                    .body();

            if (StrUtil.isBlank(response)) {
                return List.of();
            }

            // 解析响应JSON
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
            System.err.println("Error searching images: " + e.getMessage());
        }

        return List.of();
    }
}