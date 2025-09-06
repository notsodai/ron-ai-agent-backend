package com.ron.ronaiagent.chat.chat.rag;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * BookAppRag配置
 */
@Configuration
public class BookAppRagConfig {
    private final BookAppDocumentLoader bookAppDocumentLoader;
    private final EmbeddingModel dashscopeEmbeddingModel;

    public BookAppRagConfig(BookAppDocumentLoader bookAppDocumentLoader,
                            DashScopeEmbeddingModel dashscopeEmbeddingModel) {
        this.bookAppDocumentLoader = bookAppDocumentLoader;
        this.dashscopeEmbeddingModel = dashscopeEmbeddingModel;
    }

    /**
     * 创建向量存储
     * @return 向量存储
     */
    @Bean
    public VectorStore bookAppVectorStore() {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();

        List<Document> markdownDocuments = bookAppDocumentLoader.loadMarkdownDocuments();

        vectorStore.doAdd(markdownDocuments);

        return vectorStore;
    }
}
