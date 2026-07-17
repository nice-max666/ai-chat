package com.example.ai_chat.controller;

import com.example.ai_chat.common.Result;
import com.example.ai_chat.entity.Assistant;
import com.example.ai_chat.entity.ChatMessage;
import com.example.ai_chat.repository.AssistantRepository;
import com.example.ai_chat.repository.ChatMessageRepository;
import com.example.ai_chat.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import cn.hutool.json.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;

@RestController
@RequestMapping("/api") // 所有接口都加 /api 前缀，方便管理
public class ChatController { // 改名为更具业务意义的名字

    @Autowired
    private AssistantRepository assistantRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatService chatService;

    @Autowired
    private HttpServletRequest request;

    /** 从请求中获取当前登录用户的ID */
    private String getCurrentUserId() {
        return (String) request.getAttribute("userId");
    }

    // 1. 助手管理接口
    @PostMapping("/assistant") // 改用 POST，更符合 RESTful 规范
    public Result<Assistant> createAssistant(@RequestBody Assistant assistant) {
        assistant.setId(UUID.randomUUID().toString());
        assistant.setCreatedAt(LocalDateTime.now());
        assistant.setUpdatedAt(LocalDateTime.now());
        assistant.setUserId(getCurrentUserId());
        assistantRepository.save(assistant);
        return Result.success(assistant);
    }

    @GetMapping("/assistant/list")
    public Result<List<Assistant>> listAssistants() {
        String userId = getCurrentUserId();
        List<Assistant> list = assistantRepository.findAll();
        // 内存过滤：只返回当前用户的助手
        list = list.stream()
                .filter(a -> userId.equals(a.getUserId()))
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @GetMapping("/assistant/{id}")
    public Result<Assistant> getAssistant(@PathVariable String id) {
        Assistant assistant = assistantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("助手不存在"));
        if (!getCurrentUserId().equals(assistant.getUserId())) {
            throw new RuntimeException("无权访问该助手");
        }
        return Result.success(assistant);
    }

    //删除助手
    @DeleteMapping("/assistant/{id}")
    @Transactional
    public Result<Void> deleteAssistant(@PathVariable String id) {
        Assistant assistant = assistantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("助手不存在"));
        if (!getCurrentUserId().equals(assistant.getUserId())) {
            throw new RuntimeException("无权删除该助手");
        }
        // 删除关联的聊天记录
        chatMessageRepository.deleteByAssistantId(id);
        // 删除助手
        assistantRepository.deleteById(id);
        return Result.success(null);
    }

    // 2. 对话接口（核心）
    @PostMapping("/chat/completions")
    public Result<String> chat(@RequestParam String assistantId, @RequestBody ChatMessage userMessage) {
        // 校验助手归属
        checkAssistantOwnership(assistantId);
        // 从请求体中获取用户消息
        String userMsg = userMessage.getMessage();
        if (userMsg == null || userMsg.trim().isEmpty()) {
            return Result.error("消息内容不能为空");
        }

        // 调用 ChatService 完成对话
        String aiReply = chatService.chat(assistantId, userMsg);
        return Result.success(aiReply);
    }

    // 2.1 流式对话接口(SSE):AI 边生成边推送
    @PostMapping("/chat/completions/stream")
    public SseEmitter chatStream(@RequestParam String assistantId, @RequestBody ChatMessage userMessage) {
        // 校验助手归属
        checkAssistantOwnership(assistantId);
        SseEmitter emitter = new SseEmitter(600000L); // 超时 10 分钟
        String userMsg = userMessage.getMessage();

        // 空消息直接以错误结束
        if (userMsg == null || userMsg.trim().isEmpty()) {
            emitter.completeWithError(new RuntimeException("消息内容不能为空"));
            return emitter;
        }

        // 另起线程执行,避免阻塞请求线程;片段随生成随推给前端
        new Thread(() -> {
            try {
                chatService.chatStream(assistantId, userMsg, chunk -> {
                    try {
                        // 每段用 JSON 包一层,避免内容里的换行破坏 SSE 帧格式
                        JSONObject data = new JSONObject();
                        data.set("c", chunk);
                        emitter.send(SseEmitter.event().data(data.toString()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                emitter.complete(); // 全部生成完,正常关闭流
            } catch (Exception e) {
                emitter.completeWithError(e); // 出错关闭流
            }
        }).start();

        return emitter;
    }

    // 3. 聊天历史接口
    @GetMapping("/chat/history/{assistantId}")
    public Result<List<ChatMessage>> getChatHistory(@PathVariable String assistantId) {
        // 校验助手归属
        checkAssistantOwnership(assistantId);
        List<ChatMessage> history = chatMessageRepository.findByAssistantIdOrderByCreatedAtAsc(assistantId);
        return Result.success(history);
    }

    @GetMapping("/chat/findAll")
    public Result<List<ChatMessage>> findAllChatMessages() {
        String userId = getCurrentUserId();
        // 先获取当前用户的所有助手ID
        List<String> userAssistantIds = assistantRepository.findAll().stream()
                .filter(a -> userId.equals(a.getUserId()))
                .map(Assistant::getId)
                .collect(Collectors.toList());
        // 再根据助手ID过滤消息
        List<ChatMessage> list = chatMessageRepository.findAll();
        list = list.stream()
                .filter(m -> userAssistantIds.contains(m.getAssistantId()))
                .collect(Collectors.toList());
        return Result.success(list);
    }

    /** 校验助手是否属于当前用户，不属于则抛出异常 */
    private void checkAssistantOwnership(String assistantId) {
        Assistant assistant = assistantRepository.findById(assistantId)
                .orElseThrow(() -> new RuntimeException("助手不存在"));
        if (!getCurrentUserId().equals(assistant.getUserId())) {
            throw new RuntimeException("无权访问该助手");
        }
    }
}
