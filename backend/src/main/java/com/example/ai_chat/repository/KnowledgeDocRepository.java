package com.example.ai_chat.repository;

import com.example.ai_chat.entity.KnowledgeDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface KnowledgeDocRepository extends JpaRepository<KnowledgeDoc, String> {
    List<KnowledgeDoc> findByAssistantId(String assistantId);
    void deleteByAssistantId(String assistantId);
}
