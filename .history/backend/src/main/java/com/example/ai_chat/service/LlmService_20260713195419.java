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

    /**
     * 流式对话:一边接收大模型输出,一边通过回调把片段吐出去
     * @param systemPrompt  系统人设
     * @param historyMessages 历史对话(JSON数组)
     * @param userMsg       当前用户提问
     * @param onChunk       每收到一段文本就回调一次
     * @return 完整回复全文(供上层落库)
     */
    public String chatStream(String systemPrompt, JSONArray historyMessages, String userMsg,
                             java.util.function.Consumer<String> onChunk) {
        // 1. 构建请求体,关键是打开 stream 开关
        JSONObject requestBody = new JSONObject();
        requestBody.set("model", model);
        requestBody.set("temperature", 0.7);
        requestBody.set("stream", true); // 开启流式

        JSONArray messagesArray = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JSONObject systemMsg = new JSONObject();
            systemMsg.set("role", "system");
            systemMsg.set("content", systemPrompt);
            messagesArray.add(systemMsg);
        }
        if (historyMessages != null) {
            messagesArray.addAll(historyMessages);
        }
        JSONObject currentUserMsg = new JSONObject();
        currentUserMsg.set("role", "user");
        currentUserMsg.set("content", userMsg);
        messagesArray.add(currentUserMsg);
        requestBody.set("messages", messagesArray);

        StringBuilder full = new StringBuilder();
        try {
            // 2. 用 JDK 自带 HttpClient 发请求,按行读取 SSE 流
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(API_URL))
                    .header("Authorization", "Bearer " + API_KEY)
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(600))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            java.net.http.HttpResponse<java.util.stream.Stream<String>> response =
                    client.send(request, java.net.http.HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() != 200) {
                // 出错时把返回的内容拼起来抛出
                String err = response.body().reduce("", (a, b) -> a + b);
                throw new RuntimeException("大模型流式调用失败: " + err);
            }

            // 3. 逐行解析 data: 开头的 SSE 帧
            response.body().forEach(line -> {
                if (line == null || !line.startsWith("data:")) {
                    return;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    return;
                }
                // 提取本片段增量文本 choices[0].delta.content
                String content = JSONUtil.parseObj(data)
                        .getByPath("choices[0].delta.content", String.class);
                if (content != null && !content.isEmpty()) {
                    full.append(content);
                    onChunk.accept(content); // 把片段吐给上层
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("大模型流式调用异常: " + e.getMessage(), e);
        }
        return full.toString();
    }
}
