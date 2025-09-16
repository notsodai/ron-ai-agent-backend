package com.ron.ronaiagent.chat.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author admin
 * @date 2025/9/14 下午2:55
 */
class WebSearchToolTest {

    @Value("${spring.search-api.api-key}")
    private String searchApiKey;
    @Test
    void searchWeb() {
        WebSearchTool tool = new WebSearchTool(searchApiKey);
        String query = "星际穿越 https://movie.douban.com/";
        String result = tool.searchWeb(query);
        assertNotNull(result);
    }
}