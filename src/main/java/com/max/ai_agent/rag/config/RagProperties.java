package com.max.ai_agent.rag.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /**
     * 是否启用RAG
     */
    private boolean enabled;

    /**
     * 文档扫描路径
     */
    private String documentPath;

    /**
     * 切块大小
     */
    private int chunkSize;

    /**
     * 重叠大小
     */
    private  int chunkOverlap;
}
