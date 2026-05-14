package com.max.ai_agent.rag.config;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
@AllArgsConstructor
@NoArgsConstructor
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
    private int chunkSize = 800;

    /**
     * 重叠大小
     */
    private  int chunkOverlap = 200;

    /**
     * 检索返回的最大文档数
     */
    private int topK = 5;

    /**
     * 相似度阈值（0.0~1.0）
     * 低于此值的检索结果会被丢弃，保证回答质量
     */
    private double similarityThreshold = 0.70;
}
