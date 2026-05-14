package com.max.ai_agent.rag.service;

import com.max.ai_agent.rag.config.RagProperties;
import com.max.ai_agent.rag.dto.RagQueryResponse;
import com.max.ai_agent.rag.dto.RagSearchResult;
import com.max.ai_agent.rag.store.VectorStoreService;
import org.springframework.core.io.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
@Service
public class RagQueryService {
    private final VectorStoreService vectorStoreService;
    private final RagProperties properties;
    private final ChatClient.Builder chatClientBuilder;

    @Value("classpath:prompts/rag-prompt.st")
    private Resource promptTemplateResource;


    /**
     * RAG 问答核心方法
     *
     * @param question 用户问题
     * @return 包含答案和溯源信息的响应
     */
    public RagQueryResponse query(String question) {
        return queryWithBookNameOrContent(question,null);
    }


    /**
     * RAG 问答核心方法（支持按书名过滤）
     *
     * @param question 用户问题
     * @param book  书名过滤（可选，null=不限）
     * @return 包含答案和溯源信息的响应
     */
    public RagQueryResponse queryWithBookNameOrContent(String question, String book) {
        log.info("RAG查询: {}, 书名过滤: {}", question, book);

        // 1. 检索相关文档
        List<Document> documents;
        if (book != null && !book.isBlank()) {
            documents = vectorStoreService.similaritySearchWithBookName(
                    question,
                    properties.getTopK(),
                    properties.getSimilarityThreshold(),
                    book
            );
        } else {
            documents = vectorStoreService.similaritySearchWithContent(
                    question,
                    properties.getTopK(),
                    properties.getSimilarityThreshold()
            );
        }
        // 2. 格式化上下文（带出处）
        String context = formatContext(documents);

        // 3. 填充 Prompt 模板
        PromptTemplate promptTemplate = new PromptTemplate( promptTemplateResource);
        Map<String, Object> variables = new HashMap<>();
        variables.put("context", context);
        variables.put("question", question);

        Prompt prompt = promptTemplate.create(variables);

        // 4. 调用 LLM 生成回答
        String answer = chatClientBuilder.build()
                .prompt(prompt)
                .call()
                .content();

        // 5. 构建溯源信息
        List<RagSearchResult> sources = documents.stream()
                .map(this::toSearchResult)
                .toList();

        log.info("RAG查询完成，引用源数量: {}", sources.size());

        return RagQueryResponse.builder()
                .answer(answer)
                .sources(sources)
                .build();

    }

    /**
     * 格式化上下文，每段附上出处
     */
    private String formatContext(List<Document> documents) {
        if (documents.isEmpty()) {
            return "未找到与问题相关的参考资料。";
        }

        StringBuilder sb = new StringBuilder();
        for (Document doc : documents) {

            if (doc.getText() != null  && !doc.getText().isEmpty()) {
                Map<String, Object> meta = doc.getMetadata();
                String reference = (String) meta.getOrDefault("reference", "未知出处");

                sb.append("【出处：").append(reference).append("】\n");
                sb.append(doc.getText().trim());
                sb.append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * Document → RagSearchResult
     */
    private RagSearchResult toSearchResult(Document doc) {
        Map<String, Object> meta = doc.getMetadata();

        return RagSearchResult.builder()
                .content(doc.getText())
                .book((String) meta.getOrDefault("book", ""))
                .chapter((String) meta.getOrDefault("chapter", ""))
                .section((String) meta.getOrDefault("section", ""))
                .reference((String) meta.getOrDefault("reference", ""))
                .similarity(null) // 相似度分数由 VectorStore 实现决定，可能需要额外提取
                .build();
    }


}
