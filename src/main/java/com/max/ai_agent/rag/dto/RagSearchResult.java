package com.max.ai_agent.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSearchResult {

    /**
     * 原文内容
     */
    private String content;

    /**
     * 书名（如"传习录"、"道德经"）
     */
    private String book;

    /**
     * 章节（如"上卷"、"道经"）
     */
    private String chapter;

    /**
     * 小节（如"徐爱录"、"第一章"）
     */
    private String section;

    /**
     * 完整出处引用（如"《传习录·上卷·徐爱录》"）
     */
    private String reference;

    /**
     * 相似度分数（0.0~1.0）
     */
    private Double similarity;
}