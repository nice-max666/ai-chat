package com.example.ai_chat.service;

import com.example.ai_chat.common.ChunkResult;
import com.example.ai_chat.entity.KnowledgeDoc;
import com.example.ai_chat.repository.KnowledgeDocRepository;
import jakarta.annotation.PostConstruct;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KnowledgeService {

    @Value("${knowledge.chunk-size:500}")
    private int chunkSize;
    @Value("${knowledge.chunk-overlap:50}")
    private int overlap;
    @Value("${knowledge.top-k:3}")
    private int topK;

    @Autowired
    private KnowledgeDocRepository knowledgeDocRepository;
    @Autowired
    private EmbeddingService embeddingService;

    // 内存向量索引: docId -> List<VectorChunk>
    private final ConcurrentHashMap<String, List<VectorChunk>> vectorStore = new ConcurrentHashMap<>();

    /** 启动时加载已有文档并重建向量索引 */
    @PostConstruct
    public void loadExistingDocs() {
        List<KnowledgeDoc> docs = knowledgeDocRepository.findAll();
        for (KnowledgeDoc doc : docs) {
            try {
                List<String> chunks = splitChunks(doc.getContent());
                float[][] embeddings = embeddingService.embedBatch(chunks.toArray(new String[0]));
                List<VectorChunk> vcList = new ArrayList<>();
                for (int i = 0; i < chunks.size(); i++) {
                    vcList.add(new VectorChunk(i, chunks.get(i), embeddings[i]));
                }
                vectorStore.put(doc.getId(), vcList);
            } catch (Exception e) {
                // 向量化失败跳过，不影响启动
            }
        }
    }

    /** 上传并处理文档 */
    public KnowledgeDoc upload(MultipartFile file, String assistantId) {
        String fileName = file.getOriginalFilename();
        String fileType = getFileType(fileName);
        String content = parseFile(file, fileType);

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(UUID.randomUUID().toString());
        doc.setAssistantId(assistantId);
        doc.setFileName(fileName);
        doc.setFileType(fileType);
        doc.setContent(content);
        doc.setCreatedAt(LocalDateTime.now());
        knowledgeDocRepository.save(doc);

        // 向量化并存入内存
        try {
            List<String> chunks = splitChunks(content);
            float[][] embeddings = embeddingService.embedBatch(chunks.toArray(new String[0]));
            List<VectorChunk> vcList = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                vcList.add(new VectorChunk(i, chunks.get(i), embeddings[i]));
            }
            vectorStore.put(doc.getId(), vcList);
            doc.setChunkCount(chunks.size());
        } catch (Exception e) {
            doc.setChunkCount(0);
        }
        return doc;
    }

    /** 获取助手的所有文档 */
    public List<KnowledgeDoc> listDocs(String assistantId) {
        return knowledgeDocRepository.findByAssistantId(assistantId);
    }

    /** 删除文档 */
    public void deleteDoc(String docId) {
        vectorStore.remove(docId);
        knowledgeDocRepository.deleteById(docId);
    }

    /** 删除助手的所有文档 */
    public void deleteByAssistant(String assistantId) {
        List<KnowledgeDoc> docs = knowledgeDocRepository.findByAssistantId(assistantId);
        for (KnowledgeDoc doc : docs) {
            vectorStore.remove(doc.getId());
        }
        knowledgeDocRepository.deleteByAssistantId(assistantId);
    }

    /** 检索：返回 top-K 最相关的文档片段 */
    public List<ChunkResult> search(String assistantId, String query) {
        List<KnowledgeDoc> docs = knowledgeDocRepository.findByAssistantId(assistantId);
        if (docs.isEmpty()) return Collections.emptyList();

        float[] queryVec = embeddingService.embed(query);

        List<ChunkResult> allResults = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            List<VectorChunk> chunks = vectorStore.get(doc.getId());
            if (chunks == null) continue;
            for (VectorChunk vc : chunks) {
                float score = EmbeddingService.cosineSimilarity(queryVec, vc.embedding);
                allResults.add(new ChunkResult(vc.content, doc.getFileName(), score));
            }
        }

        // 按相似度降序，取 top-K
        allResults.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return allResults.subList(0, Math.min(topK, allResults.size()));
    }

    /** 构建检索上下文文本 */
    public String buildContext(List<ChunkResult> results) {
        if (results == null || results.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("【参考知识】以下是与用户问题相关的知识库内容，请基于这些内容回答：\n\n");
        for (int i = 0; i < results.size(); i++) {
            ChunkResult r = results.get(i);
            sb.append("[文档: ").append(r.getFileName()).append("]\n");
            sb.append(r.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    // ==== 内部工具方法 ====

    private String getFileType(String fileName) {
        if (fileName == null) return "txt";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".docx")) return "docx";
        return "txt";
    }

    private String parseFile(MultipartFile file, String fileType) {
        try (InputStream is = file.getInputStream()) {
            if ("docx".equals(fileType)) {
                XWPFDocument doc = new XWPFDocument(is);
                XWPFWordExtractor extractor = new XWPFWordExtractor(doc);
                return extractor.getText();
            } else {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("文档解析失败: " + e.getMessage());
        }
    }

    List<String> splitChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += (chunkSize - overlap);
            if (start >= end) break;
        }
        return chunks;
    }

    /** 向量块内部类 */
    static class VectorChunk {
        int index;
        String content;
        float[] embedding;
        VectorChunk(int index, String content, float[] embedding) {
            this.index = index;
            this.content = content;
            this.embedding = embedding;
        }
    }
}
