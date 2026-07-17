package com.example.ai_chat.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChunkResult {
    private String content;
    private String fileName;
    private float score;
}
