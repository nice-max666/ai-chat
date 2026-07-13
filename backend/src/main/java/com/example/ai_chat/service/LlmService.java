package com.example.ai_chat.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LlmService {

    // 1. 配置大模型的 API 地址和密钥 (这里以兼容 OpenAI 格式的智谱为例)
    // TODO: 后面进阶阶段，我们会把硬编码挪到 application.yml 里
    @Value("${llm.api_url}")
    private String API_URL;
    @Value("${llm.api_key}")
    private  String API_KEY; // 去智谱开放平台免费申请
    @Value("${llm.model}")
    private String model;

    /**
     * 与大模型对话
     * @param systemPrompt 系统提示词（AI的人设）
     * @param historyMessages 历史对话记录 (JSON数组格式)
     * @param userMsg 当前用户的提问
     * @return AI的回复文本
     */
    public String chat(String systemPrompt, JSONArray historyMessages, String userMsg) {

        // 2. 构建请求体 JSON
        JSONObject requestBody = new JSONObject();
        requestBody.set("model", model); // 使用免费或你有的模型
        requestBody.set("temperature", 0.7);     // 创造性参数

        JSONArray messagesArray = new JSONArray();

        // 2.1 注入系统人设 (如果有的话)
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JSONObject systemMsg = new JSONObject();
            systemMsg.set("role", "system");
            systemMsg.set("content", systemPrompt);
            messagesArray.add(systemMsg);
        }

        // 2.2 注入历史对话 (让AI有记忆)
        if (historyMessages != null) {
            messagesArray.addAll(historyMessages);
        }

        // 2.3 注入当前用户提问
        JSONObject currentUserMsg = new JSONObject();
        currentUserMsg.set("role", "user");
        currentUserMsg.set("content", userMsg);
        messagesArray.add(currentUserMsg);

        requestBody.set("messages", messagesArray);

        // 3. 发送 HTTP POST 请求
        HttpResponse response = HttpRequest.post(API_URL)
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(600000)
                .execute();

        // 4. 解析大模型返回的结果
        if (response.isOk()) { // HTTP 状态码 200
            JSONObject responseBody = JSONUtil.parseObj(response.body());
            // 按照标准格式提取 AI 的回复
            return responseBody.getByPath("choices[0].message.content", String.class);
        } else {
            // 调用失败的处理
            throw new RuntimeException("大模型调用失败: " + response.body());
        }
    }
}
