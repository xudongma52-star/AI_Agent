package com.max.ai_agent.controller;

import com.max.ai_agent.app.MaxApp;
import com.max.ai_agent.dto.ChatRequest;
import com.max.ai_agent.dto.ChatResponse;
import com.max.ai_agent.rag.service.RagKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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
     * 重建知识库（管理接口）
     * POST /api/chat/rebuild
     */
    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, String>> rebuild() {
        ragKnowledgeService.rebuild();
        return ResponseEntity.ok(Map.of("message", "知识库重建完成"));
    }
}