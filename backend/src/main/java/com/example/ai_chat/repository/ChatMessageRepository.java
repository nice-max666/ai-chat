package com.example.ai_chat.repository;

import com.example.ai_chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    List<ChatMessage> findByAssistantIdOrderByCreatedAtAsc(String assistantId);

    void deleteByAssistantId(String assistantId);
}
