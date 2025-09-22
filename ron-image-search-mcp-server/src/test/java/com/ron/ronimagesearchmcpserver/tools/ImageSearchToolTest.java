package com.ron.ronimagesearchmcpserver.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author admin
 * @date 2025/9/22 下午10:10
 */
@SpringBootTest
class ImageSearchToolTest {

    @Resource
    private ImageSearchTool imageSearchTool;
    @Test
    void searchImage() {
        String result = imageSearchTool.searchImage("Beautiful Women");
        assertNotNull(result);
    }
}