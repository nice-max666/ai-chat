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

        // 2.1 注入系统人设 + 系统权威时间 (时间始终注入,避免模型瞎编日期)
        JSONObject systemMsg = new JSONObject();
        systemMsg.set("role", "system");
        systemMsg.set("content", buildSystemContent(systemPrompt));
        messagesArray.add(systemMsg);

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
        // 组装初始消息:系统人设 + 历史 + 当前提问
        JSONArray messagesArray = new JSONArray();
        // 系统人设 + 系统权威时间 (时间始终注入)
        JSONObject systemMsg = new JSONObject();
        systemMsg.set("role", "system");
        systemMsg.set("content", buildSystemContent(systemPrompt));
        messagesArray.add(systemMsg);
        if (historyMessages != null) {
            messagesArray.addAll(historyMessages);
        }
        JSONObject currentUserMsg = new JSONObject();
        currentUserMsg.set("role", "user");
        currentUserMsg.set("content", userMsg);
        messagesArray.add(currentUserMsg);

        JSONArray tools = buildTools();
        StringBuilder full = new StringBuilder();

        try {
            // 最多 3 轮:允许"调工具→回填→再调"的多步流程
            for (int round = 0; round < 3; round++) {
                JSONObject requestBody = new JSONObject();
                requestBody.set("model", model);
                requestBody.set("temperature", 0.7);
                requestBody.set("stream", true);
                requestBody.set("messages", messagesArray);
                requestBody.set("tools", tools);

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
                    String err = response.body().reduce("", (a, b) -> a + b);
                    throw new RuntimeException("大模型流式调用失败: " + err);
                }

                // 累积本轮的 tool_calls:index -> [id, name, arguments]
                java.util.Map<Integer, String[]> toolCalls = new java.util.LinkedHashMap<>();

                response.body().forEach(line -> {
                    if (line == null || !line.startsWith("data:")) return;
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) return;

                    JSONObject delta = JSONUtil.parseObj(data).getByPath("choices[0].delta", JSONObject.class);
                    if (delta == null) return;

                    // 普通文本增量:实时推给前端
                    String content = delta.getStr("content");
                    if (content != null && !content.isEmpty()) {
                        full.append(content);
                        onChunk.accept(content);
                    }

                    // 工具调用增量:按 index 累积 id/name/参数片段
                    JSONArray tcArr = delta.getJSONArray("tool_calls");
                    if (tcArr != null) {
                        for (Object o : tcArr) {
                            JSONObject tc = (JSONObject) o;
                            int idx = tc.getInt("index", 0);
                            String[] acc = toolCalls.computeIfAbsent(idx, k -> new String[]{"", "", ""});
                            if (tc.getStr("id") != null) acc[0] = tc.getStr("id");
                            JSONObject func = tc.getJSONObject("function");
                            if (func != null) {
                                if (func.getStr("name") != null) acc[1] = func.getStr("name");
                                if (func.getStr("arguments") != null) acc[2] = acc[2] + func.getStr("arguments");
                            }
                        }
                    }
                });

                // 本轮没有工具调用 → 已经是最终回答,结束
                if (toolCalls.isEmpty()) {
                    break;
                }

                // 有工具调用:先把 assistant 的 tool_calls 消息加回上下文
                JSONObject assistantMsg = new JSONObject();
                assistantMsg.set("role", "assistant");
                assistantMsg.set("content", "");
                JSONArray tcList = new JSONArray();
                for (String[] acc : toolCalls.values()) {
                    JSONObject one = new JSONObject();
                    one.set("id", acc[0]);
                    one.set("type", "function");
                    JSONObject func = new JSONObject();
                    func.set("name", acc[1]);
                    func.set("arguments", acc[2]);
                    one.set("function", func);
                    tcList.add(one);
                }
                assistantMsg.set("tool_calls", tcList);
                messagesArray.add(assistantMsg);

                // 逐个执行工具,把结果作为 tool 消息回填
                for (String[] acc : toolCalls.values()) {
                    String result = executeTool(acc[1], acc[2]);
                    JSONObject toolMsg = new JSONObject();
                    toolMsg.set("role", "tool");
                    toolMsg.set("tool_call_id", acc[0]);
                    toolMsg.set("content", result);
                    messagesArray.add(toolMsg);
                }
                // 进入下一轮,让模型基于工具结果继续作答
            }
        } catch (Exception e) {
            throw new RuntimeException("大模型流式调用异常: " + e.getMessage(), e);
        }
        return full.toString();
    }

    /**
     * 构建带"系统权威时间"的 system 内容:
     * 把当前真实日期时间前置注入,作为模型回答时间类问题的唯一准绳,
     * 避免依赖工具调用,也压制历史对话里出现的错误日期。
     */
    private String buildSystemContent(String systemPrompt) {
        String timeStr = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter
                        .ofPattern("yyyy年MM月dd日 HH:mm:ss EEEE", java.util.Locale.CHINA));
        String timeContext = "【系统权威时间,请以此为准】当前真实日期时间为:" + timeStr
                + "。回答任何与日期、星期、时间相关的问题时,必须以该时间为唯一准确依据,"
                + "忽略历史对话中出现的其它日期。";
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            return timeContext + "\n\n" + systemPrompt;
        }
        return timeContext;
    }

    /**
     * 定义可供大模型调用的工具清单(OpenAI function 格式)
     */
    private JSONArray buildTools() {
        JSONArray tools = new JSONArray();

        // 工具1:获取当前时间
        JSONObject timeTool = new JSONObject();
        timeTool.set("type", "function");
        JSONObject timeFunc = new JSONObject();
        timeFunc.set("name", "get_current_time");
        timeFunc.set("description", "获取当前的日期和时间");
        JSONObject timeParams = new JSONObject();
        timeParams.set("type", "object");
        timeParams.set("properties", new JSONObject());
        timeParams.set("required", new JSONArray());
        timeFunc.set("parameters", timeParams);
        timeTool.set("function", timeFunc);
        tools.add(timeTool);

        // 工具2:查询天气
        JSONObject weatherTool = new JSONObject();
        weatherTool.set("type", "function");
        JSONObject weatherFunc = new JSONObject();
        weatherFunc.set("name", "get_weather");
        weatherFunc.set("description", "查询指定城市的实时天气");
        JSONObject weatherParams = new JSONObject();
        weatherParams.set("type", "object");
        JSONObject props = new JSONObject();
        JSONObject cityProp = new JSONObject();
        cityProp.set("type", "string");
        cityProp.set("description", "城市名称,例如:北京");
        props.set("city", cityProp);
        weatherParams.set("properties", props);
        JSONArray required = new JSONArray();
        required.add("city");
        weatherParams.set("required", required);
        weatherFunc.set("parameters", weatherParams);
        weatherTool.set("function", weatherFunc);
        tools.add(weatherTool);

        return tools;
    }

    /**
     * 执行工具,返回结果文本(回填给大模型)
     */
    private String executeTool(String name, String argsJson) {
        try {
            JSONObject args = (argsJson == null || argsJson.isEmpty())
                    ? new JSONObject() : JSONUtil.parseObj(argsJson);

            if ("get_current_time".equals(name)) {
                return java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else if ("get_weather".equals(name)) {
                String city = args.getStr("city", "北京");
                // format 里的 % 写成 %25:既能过 URI.create 的转义校验,wttr.in 又会解码回 %l/%C/%t 生效
                String url = "https://wttr.in/" + cn.hutool.core.util.URLUtil.encode(city)
                        + "?format=%25l:+%25C+%25t&lang=zh";
                // 用 JDK HttpClient 发送已编码好的 URL,避免 Hutool 对 % 二次编码导致城市名/format 损坏
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(10))
                        .GET()
                        .build();
                java.net.http.HttpResponse<String> resp =
                        client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                String body = resp.body();
                return (body == null || body.isBlank()) ? "未获取到天气数据" : body.trim();
            }
            return "未知工具: " + name;
        } catch (Exception e) {
            return "工具执行失败: " + e.getMessage();
        }
    }
}
