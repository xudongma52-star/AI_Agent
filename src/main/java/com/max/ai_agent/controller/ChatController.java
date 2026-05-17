package com.max.ai_agent.controller;

import com.max.ai_agent.app.MaxApp;
import com.max.ai_agent.dto.ChatRequest;
import com.max.ai_agent.dto.ChatResponse;
import com.max.ai_agent.rag.service.RagKnowledgeService;
import com.max.ai_agent.tools.PdfExportTool;
import com.max.ai_agent.tools.PdfExportTool.PdfEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final MaxApp maxApp;
    private final RagKnowledgeService ragKnowledgeService;

    /**
     * 开始新对话
     * POST /api/chat/start
     *
     * {
     *   "message": "知行合一是什么意思",
     *   "useRag": true
     * }
     */
    @PostMapping("/start")
    public ChatResponse startNewChat(@RequestBody ChatRequest chatRequest) {
        String chatId = UUID.randomUUID().toString();
        boolean useRag = Boolean.TRUE.equals(chatRequest.getUseRag());

        String reply = maxApp.nowChat(chatRequest.getMessage(), chatId, useRag);
        return new ChatResponse(chatId, reply, true);
    }

    /**
     * 继续对话
     * POST /api/chat/continue
     *
     * {
     *   "message": "那致良知呢？",
     *   "chatId": "xxx-xxx-xxx",
     *   "useRag": true
     * }
     */
    @PostMapping("/continue")
    public ChatResponse continueChat(@RequestBody ChatRequest chatRequest) {
        if (chatRequest.getChatId() == null || chatRequest.getChatId().isBlank()) {
            throw new IllegalArgumentException("chatId不能为空");
        }

        boolean useRag = Boolean.TRUE.equals(chatRequest.getUseRag());

        String reply = maxApp.nowChat(chatRequest.getMessage(), chatRequest.getChatId(), useRag);
        return new ChatResponse(chatRequest.getChatId(), reply, false);
    }

    /**
     * 流式开始新对话
     * POST /api/chat/start/stream
     *
     * {
     *   "message": "知行合一是什么意思",
     *   "useRag": true
     * }
     */
    @PostMapping(value = "/start/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> startNewChatStream(@RequestBody ChatRequest chatRequest) {
        String chatId = UUID.randomUUID().toString();
        boolean useRag = Boolean.TRUE.equals(chatRequest.getUseRag());

        return Flux.just("event:chatId\ndata:" + chatId + "\n")
                .concatWith(maxApp.nowChatStream(chatRequest.getMessage(), chatId, useRag)
                        .map(content -> "data:" + content + "\n\n"));
    }

    /**
     * 流式继续对话
     * POST /api/chat/continue/stream
     *
     * {
     *   "message": "那致良知呢？",
     *   "chatId": "xxx-xxx-xxx",
     *   "useRag": true
     * }
     */
    @PostMapping(value = "/continue/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> continueChatStream(@RequestBody ChatRequest chatRequest) {
        if (chatRequest.getChatId() == null || chatRequest.getChatId().isBlank()) {
            return Flux.just("event:error\ndata:chatId不能为空\n\n");
        }

        boolean useRag = Boolean.TRUE.equals(chatRequest.getUseRag());

        return maxApp.nowChatStream(chatRequest.getMessage(), chatRequest.getChatId(), useRag)
                .map(content -> "data:" + content + "\n\n");
    }

    /**
     * 下载 PDF
     * GET /api/chat/pdf/download/{downloadId}
     */
    @GetMapping("/pdf/download/{downloadId}")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String downloadId) {
        PdfEntry entry = PdfExportTool.takePdf(downloadId);
        if (entry == null) {
            return ResponseEntity.notFound().build();
        }

        String encodedFileName = URLEncoder.encode(entry.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .body(entry.bytes());
    }

    /**
     * 重建知识库（管理接口）
     * POST /api/chat/rebuild
     */
    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, String>> rebuild() {
        ragKnowledgeService.rebuild();
        return ResponseEntity.ok(Map.of("message", "知识库重建完成"));
    }
}