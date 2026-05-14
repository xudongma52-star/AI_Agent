package com.max.ai_agent.rag.store;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;


import javax.print.Doc;
import java.util.List;
import java.util.Map;

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

        // 每批最多10个文档（符合DashScope API限制）

        int batchSize = 10; // DashScope 单次最大限制
        int total = documents.size();

        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            List<Document> batch = documents.subList(i, end);

            try {
                vectorStore.add(batch);
                log.info("成功存入向量库，批次: {} - {}, 数量: {}", i, end - 1, batch.size());
            } catch (Exception e) {
                log.error("向量库存储失败，批次: {} - {}", i, end - 1, e);
            }
        }


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

    /**
     * 带阈值的相似度检索
     * @param query 查询文本
     * @param topK  返回最大条数
     * @param threshold 相似度阈值(0.70),低于此值的结果会被丢弃
     * @return
     */
    public List<Document> similaritySearchWithContent(String query,int topK, double threshold){
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();//构建结束

        List<Document> results = vectorStore.similaritySearch(request);
        log.debug("检索查询:{},命中数:{}",query,results.size());
        return results;
    }

    public List<Document> similaritySearchWithBookName(String query,int topK,double threshold,String book){
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold);//构建还没结束
        // 书名标准化：去除空格、标点、统一大小写
        if(book != null && !book.isEmpty()){
            String normalizedBook = normalizeBookName(book);
            if (!normalizedBook.isBlank()) {
                FilterExpressionBuilder feb = new FilterExpressionBuilder();
                builder.filterExpression(feb.eq("book", normalizedBook).build());
            }
        }
        List<Document> results = vectorStore.similaritySearch(builder.build());
        log.debug("检索查询: {}, 书名过滤: {}, 命中数: {}", query, book, results.size());
        return results;

    }

    public String searchAndFormat(String query,int topK, double threshold){
        List<Document> documents = similaritySearchWithContent(query, topK, threshold);
        if(documents == null || documents.isEmpty()){
            return "未找到相关资料";
        }

        StringBuilder sb = new StringBuilder();
        for(Document document : documents){
            Map<String, Object> meta = document.getMetadata();

            String reference = (String)meta.getOrDefault("reference", "未知出处");
            String book = (String) meta.getOrDefault("book", " ");
            String chapter = (String) meta.getOrDefault("chapter", "");
            String section = (String)meta.getOrDefault("section","");

            sb.append("【出处：").append(reference).append("】\n");
            //TODO
            sb.append(document.getText());
            sb.append("\n---\n");
        }
        return sb.toString();
    }

    /**
     * 检索并返回原始文档（用于API返回，带完整元数据）
     */
    public List<Document> searchWithMetadata(String query, int topK, double threshold) {
        return similaritySearchWithContent(query, topK, threshold);
    }

    // 书名标准化方法
    private String normalizeBookName(String bookName) {
        if (bookName == null || bookName.isBlank()) {
            return null;
        }

        return bookName
                .trim()  // 去除首尾空格
                .replaceAll("[\\p{Punct}\\s]+", "")  // 去除所有标点和空格
                .toLowerCase()  // 转为小写
                .replaceAll("the", "")  // 去除常见冠词
                .replaceAll("of", "")
                .replaceAll("\\s+", "");  // 去除所有空格
    }
}
