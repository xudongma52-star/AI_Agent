package com.max.ai_agent.rag.service;

import com.max.ai_agent.rag.config.RagProperties;
import com.max.ai_agent.rag.loader.DocumentLoader;
import com.max.ai_agent.rag.store.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagKnowledgeService implements CommandLineRunner {

    private final DocumentLoader documentLoader;
    private final VectorStoreService  vectorStoreService;
    private final RagProperties properties;

    @Override
    public void run(String... args){
        if(!properties.isEnabled()){
            log.info("RAG未启用,跳过知识库构建");
            return;
        }

        try {
            buildKnowledgeBase();
        } catch (Exception e) {
            log.error("RAG知识库构建失败，应用将继续启动但RAG功能不可用", e);
        }

    }

    public void rebuild(){
        log.info("手动触发RAG知识库重建");
        buildKnowledgeBase();
    }

    private void buildKnowledgeBase(){
        long startTime = System.currentTimeMillis();
        //加载文档(带元数据)
        List<Document> rawDocuments = documentLoader.load();
        if(rawDocuments.isEmpty()){
            log.warn("未加载到任何文档，请检查路径:{}",properties.getDocumentPath());
            return;
        }

        log.info("原始文档段落数:{}", rawDocuments.size());

        //2.切块(元数据自动继承到子块)
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.getChunkSize())
                .withMinChunkSizeChars(properties.getChunkOverlap())
                .build();

        List<Document> chunks = splitter.apply(rawDocuments);
        log.info("切块完成，总块数:{}",chunks.size());

        //3.存入向量数据库(会自动调用Embedding模型向量化)
        vectorStoreService.save(chunks);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("RAG知识库构建完成，切块数:{}ms",chunks.size(),elapsed);

    }

}