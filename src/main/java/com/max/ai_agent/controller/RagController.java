package com.max.ai_agent.controller;

import com.max.ai_agent.rag.dto.RagQueryResponse;
import com.max.ai_agent.rag.service.RagKnowledgeService;
import com.max.ai_agent.rag.service.RagQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagQueryService ragQueryService;
    private final RagKnowledgeService ragKnowledgeService;

    /**
     * RAG 问答
     * POST /api/rag/question
     * Body: { "question": "心即理和道法自然有什么异同？" }
     * 可选: { "question": "...", "book": "传习录" }
     */
    @PostMapping("/question")
    public ResponseEntity<RagQueryResponse> query(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        String book = body.get("book");  // 可选，为null时不限书名

        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (book == null || book.isEmpty()) {
            return ResponseEntity.ok(ragQueryService.query(question));
        } else {
            return ResponseEntity.ok(ragQueryService.queryWithBookNameOrContent(question, book));
        }

    }

    /**
     * 手动重建知识库
     * POST /api/rag/rebuild
     */
    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, String>> rebuild() {
        ragKnowledgeService.rebuild();
        return ResponseEntity.ok(Map.of("message", "知识库重建完成"));
    }
}