package com.max.ai_agent.rag.service;

import com.max.ai_agent.rag.config.RagProperties;
import com.max.ai_agent.rag.loader.DocumentLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagKnowledgeService {

    private final DocumentLoader documentLoader;
    private final RagProperties properties;

    private List<Document> knowledgeBase;

    /**
     * 项目启动时自动加载
     */
    @PostConstruct
    public void init() {

        if (!properties.isEnabled()) {
            log.info("RAG未启用");
            return;
        }

        log.info("开始构建RAG知识库...");

        List<Document> rawDocuments = documentLoader.load();

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.getChunkSize())//切块大小
                .withMinChunkSizeChars(properties.getChunkOverlap())//重叠大小
                .build();

        knowledgeBase = splitter.apply(rawDocuments);

        log.info("RAG知识库构建完成，切块后数量：{}", knowledgeBase.size());
    }

    public List<Document> getKnowledgeBase() {
        return knowledgeBase;
    }
}