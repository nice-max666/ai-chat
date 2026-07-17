package com.example.ai_chat.controller;

import com.example.ai_chat.common.Result;
import com.example.ai_chat.entity.KnowledgeDoc;
import com.example.ai_chat.service.KnowledgeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private HttpServletRequest request;

    /** 上传文档到指定助手 */
    @PostMapping("/upload")
    public Result<KnowledgeDoc> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam("assistantId") String assistantId) {
        KnowledgeDoc doc = knowledgeService.upload(file, assistantId);
        return Result.success(doc);
    }

    /** 列出助手下所有文档 */
    @GetMapping("/list")
    public Result<List<KnowledgeDoc>> list(@RequestParam("assistantId") String assistantId) {
        return Result.success(knowledgeService.listDocs(assistantId));
    }

    /** 删除单个文档 */
    @DeleteMapping("/{docId}")
    public Result<?> delete(@PathVariable String docId) {
        knowledgeService.deleteDoc(docId);
        return Result.success(null);
    }
}
