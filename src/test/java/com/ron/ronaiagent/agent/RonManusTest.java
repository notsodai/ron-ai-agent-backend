package com.ron.ronaiagent.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;


/**
 * @author admin
 * @date 2025/11/2 下午5:52
 */
@SpringBootTest
@ActiveProfiles("test")
class RonManusTest {
    @Resource
    private RonManus ronManus;

    @Test
    void run() {
        String userPrompt = "请用中文写一个关于机器学习算法的简介,配上几张算法说明图片，并且以PDF格式输出，名称为：机器学习算法举例说明";
        String result = ronManus.run(userPrompt);
        Assertions.assertNotNull(result);
    }
}