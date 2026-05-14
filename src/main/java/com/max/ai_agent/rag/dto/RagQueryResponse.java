package com.max.ai_agent.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryResponse {

    /**
     * LLM 生成的回答
     */
    private String answer;

    /**
     * 引用的参考资料列表（用于前端溯源展示）
     */
    private List<RagSearchResult> sources;
}