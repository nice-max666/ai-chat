package com.example.ai_chat.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_docs")
public class KnowledgeDoc {
    @Id
    private String id;
    private String assistantId;
    private String fileName;
    private String fileType;
    @Column(columnDefinition = "LONGTEXT")
    private String content;
    private Integer chunkCount;
    private LocalDateTime createdAt;
}
