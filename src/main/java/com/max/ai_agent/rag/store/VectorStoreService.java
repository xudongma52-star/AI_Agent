package com.max.ai_agent.rag.store;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {
    private final VectorStore vectorStore;

    /**
     * 批量存入向量库
     * @param documents
     */
    public void save(List<Document> documents){
        if(documents == null || documents.isEmpty()){
            log.warn("没有需要存储的文档");
            return;
        }
        vectorStore.add(documents);
        log.info("成功存入向量库，数量: {}",documents.size());
    }

    /**
     * 相似度检索
     * @param query
     * @return
     */
    public List<Document> similaritySearch(String query){
        return vectorStore.similaritySearch(query);
    }

}
