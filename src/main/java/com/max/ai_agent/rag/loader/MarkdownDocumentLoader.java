package com.max.ai_agent.rag.loader;

import com.max.ai_agent.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            Resource[] resources = resolver.getResources(properties.getDocumentPath());

            for (Resource resource : resources) {
                //获取文件名
                String filename = resource.getFilename();
                //获取书名
                String bookName = extractBookName(filename);

                log.info("正在加载文档: {}", filename);

                List<Document> fileDocs = parseMarkdownWithMetadata(resource, bookName, filename);
                documents.addAll(fileDocs);

                log.info("文档 {} 加载完成，产出段落数: {}", filename, fileDocs.size());
            }

            log.info("RAG文档加载完成，总段落数: {}", documents.size());

        } catch (IOException e) {
            log.error("加载Markdown文档失败", e);
            throw new RuntimeException("加载Markdown文档失败", e);
        }

        return documents;
    }

    /**
     * 核心：按标题层级解析Markdown，提取元数据
     *
     * 解析规则：
     *   # 一级标题  → 书名（覆盖文件名推导的书名）
     *   ## 二级标题 → 章节（如"上卷"、"道经"）
     *   ### 三级标题 → 小节（如"徐爱录"、"第一章"）
     *   --- 水平线  → 分段符，在当前标题下新建一段
     *
     * 每个段落都会继承当前所在的所有标题层级信息
     */
    private List<Document> parseMarkdownWithMetadata(Resource resource,
                                                     String bookName,
                                                     String filename) throws IOException {
        List<Document> docs = new ArrayList<>();

        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String[] lines = content.split("\n");

        // 当前标题层级状态
        String currentBook = bookName;
        String currentChapter = "";
        String currentSection = "";

        // 当前段落内容收集器
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            //移除行首尾空白
            String trimmedLine = line.trim();

            if (trimmedLine.startsWith("### ")) {
                // 遇到三级标题 → 保存上一段，开始新段
                flushSection(docs, currentContent, currentBook, currentChapter, currentSection, filename);
                currentSection = trimmedLine.substring(4).trim();
                currentContent = new StringBuilder();

            } else if (trimmedLine.startsWith("## ")) {
                // 遇到二级标题 → 保存上一段，开始新段
                flushSection(docs, currentContent, currentBook, currentChapter, currentSection, filename);
                currentChapter = trimmedLine.substring(3).trim();
                currentSection = "";
                currentContent = new StringBuilder();

            } else if (trimmedLine.startsWith("# ")) {
                // 遇到一级标题 → 保存上一段，更新书名
                flushSection(docs, currentContent, currentBook, currentChapter, currentSection, filename);
                currentBook = trimmedLine.substring(2).trim();
                currentChapter = "";
                currentSection = "";
                currentContent = new StringBuilder();

            } else if (trimmedLine.equals("---")) {
                // 水平线 → 在同一标题下分段
                flushSection(docs, currentContent, currentBook, currentChapter, currentSection, filename);
                currentContent = new StringBuilder();

            } else {
                currentContent.append(line).append("\n");
            }
        }

        // 别忘了最后一段
        flushSection(docs, currentContent, currentBook, currentChapter, currentSection, filename);

        return docs;
    }

    /**
     * 将当前收集的内容保存为一个Document
     */
    private void flushSection(List<Document> docs,
                              StringBuilder content,
                              String book,
                              String chapter,
                              String section,
                              String source) {
        String text = content.toString().trim();
        if (text.isEmpty()) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("book", book);//顶层分类
        metadata.put("chapter", chapter);//中级分类
        metadata.put("section", section);//细粒度分类
        metadata.put("source", source);//原始来源

        // 构建可读的出处字符串，方便直接引用
        String reference = buildReference(book, chapter, section);
        metadata.put("reference", reference);

        docs.add(new Document(text, metadata));
    }

    /**
     * 构建出处引用字符串
     * 例：《传习录·上卷·徐爱录》《道德经·道经·第一章》
     */
    private String buildReference(String book, String chapter, String section) {
        StringBuilder sb = new StringBuilder("《");
        sb.append(book);
        if (!chapter.isEmpty()) {
            sb.append("·").append(chapter);
        }
        if (!section.isEmpty()) {
            sb.append("·").append(section);
        }
        sb.append("》");
        return sb.toString();
    }

    /**
     * 从文件名提取书名
     * "传习录.md" → "传习录"
     */
    private String extractBookName(String filename) {
        if (filename != null && filename.endsWith(".md")) {
            return filename.substring(0, filename.length() - 3);
        }
        return filename != null ? filename : "未知";
    }
}