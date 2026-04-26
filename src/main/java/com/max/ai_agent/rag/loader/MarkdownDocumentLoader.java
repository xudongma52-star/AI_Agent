package com.max.ai_agent.rag.loader;

import com.max.ai_agent.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarkdownDocumentLoader implements DocumentLoader {

    private final ResourcePatternResolver resolver;
    private final RagProperties properties;

    @Override
    public List<Document> load() {

        List<Document> documents = new ArrayList<>();

        try {
            Resource[] resources =
                    resolver.getResources(properties.getDocumentPath());

            MarkdownDocumentReaderConfig config =
                    MarkdownDocumentReaderConfig.builder()
                            .withHorizontalRuleCreateDocument(true)
                            .build();

            for (Resource resource : resources) {

                MarkdownDocumentReader reader =
                        new MarkdownDocumentReader(resource, config);

                List<Document> docList = reader.get();

                //  添加元数据
                docList.forEach(doc ->
                        doc.getMetadata().put("source", resource.getFilename())
                );

                documents.addAll(docList);
            }

            log.info("RAG加载文档完成，数量：{}", documents.size());

        } catch (IOException e) {
            log.error("加载Markdown文档失败", e);
            throw new RuntimeException(e);
        }

        return documents;
    }
}