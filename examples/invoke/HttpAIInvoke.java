package com.ron.ronaiagent.examples.invoke;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

public class HttpAIInvoke {
    public static void main(String[] args) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请先设置环境变量 DASHSCOPE_API_KEY");
        }

        String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

        // 构造 JSON 请求体
        JSONObject systemMsg = new JSONObject()
                .set("role", "system")
                .set("content", "You are a helpful assistant.");
        JSONObject userMsg = new JSONObject()
                .set("role", "user")
                .set("content", "你是谁？");

        JSONArray messages = new JSONArray()
                .put(systemMsg)
                .put(userMsg);

        JSONObject input = new JSONObject()
                .set("messages", messages);

        JSONObject parameters = new JSONObject()
                .set("result_format", "message");

        JSONObject body = new JSONObject()
                .set("model", "qwen-plus")
                .set("input", input)
                .set("parameters", parameters);

        // 发起请求
        try (HttpResponse resp = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(ContentType.JSON.toString())
                .body(body.toString())
                .timeout(30_000)
                .execute()) {

            System.out.println("Status: " + resp.getStatus());
            System.out.println("Body: " + resp.body());
        }
    }
}
