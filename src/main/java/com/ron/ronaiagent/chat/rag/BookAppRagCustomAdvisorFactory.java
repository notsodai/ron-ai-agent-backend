package com.ron.ronaiagent.chat.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * @author admin
 * @date 2025/9/9 下午10:20
 */
public class BookAppRagCustomAdvisorFactory {
    public static Advisor createCustomAdvisor(VectorStore vectorStore, String type){
        // 使用自定义文档过滤器
        FilterExpressionBuilder filterExpressionBuilder = new FilterExpressionBuilder();
        Filter.Expression expression = filterExpressionBuilder
                .eq("type", type)
                .build();

        VectorStoreDocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression) // 过滤条件
                .similarityThreshold(0.7)   // 相似度阈值
                .topK(3)                    // 返回结果数量
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(BookAppContextualQueryAugmenterFactory.createContextualQueryAugmenter())
                .build();
    }
}
