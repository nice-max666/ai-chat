package com.example.ai_chat.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.example.ai_chat.entity.Assistant;
import com.example.ai_chat.entity.ChatMessage;
import com.example.ai_chat.repository.AssistantRepository;
import com.example.ai_chat.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    @Autowired
    private AssistantRepository assistantRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private LlmService llmService;

    /**
     * 核心对话方法
     */
    public String chat(String assistantId, String userMsg) {

        // 1. 查出这个助手是谁？（获取他的人设）
        Assistant assistant = assistantRepository.findById(assistantId)
                .orElseThrow(() -> new RuntimeException("助手不存在，ID: " + assistantId));

        // 2. 把【用户的提问】存入数据库
        ChatMessage userMessage = new ChatMessage();
        userMessage.setId(UUID.randomUUID().toString());
        userMessage.setAssistantId(assistantId);
        userMessage.setRole("user");
        userMessage.setMessage(userMsg);
        userMessage.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(userMessage);

        // 3. 查出该助手的历史对话记录（用于构建上下文记忆）
        List<ChatMessage> historyList = chatMessageRepository.findByAssistantIdOrderByCreatedAtAsc(assistantId);

        // 将历史记录转换为大模型需要的 JSON 数组格式
        JSONArray historyMessages = new JSONArray();
        for (ChatMessage msg : historyList) {
            JSONObject historyMsg = new JSONObject();
            historyMsg.set("role", msg.getRole());
            historyMsg.set("content", msg.getMessage());
            historyMessages.add(historyMsg);
        }

        // 4. 调用大模型，获取 AI 的回复
        String aiReply = llmService.chat(assistant.getPersonality(), historyMessages, userMsg);

        // 5. 把【AI的回复】存入数据库
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setId(UUID.randomUUID().toString());
        assistantMessage.setAssistantId(assistantId);
        assistantMessage.setRole("assistant");
        assistantMessage.setMessage(aiReply);
        assistantMessage.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(assistantMessage);

        // 6. 返回结果
        return aiReply;
    }
}
