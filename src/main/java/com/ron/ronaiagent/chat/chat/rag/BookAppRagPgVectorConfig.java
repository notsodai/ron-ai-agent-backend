package com.ron.ronaiagent.chat.chat.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * @author ron
 * @date 2025/09/07 11:04
 */
@Configuration
public class BookAppRagPgVectorConfig {
    @Resource
    private BookAppDocumentLoader bookAppDocumentLoader;
    @Bean
    public VectorStore bookAppPgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashScopeEmbeddingModel) {
        PgVectorStore pgVectorStore = PgVectorStore.builder(jdbcTemplate, dashScopeEmbeddingModel)
                .dimensions(1536)                    // 可选: 默认维度或1536
                .distanceType(COSINE_DISTANCE)       // 可选: 默认为 COSINE_DISTANCE
                .indexType(HNSW)                     // 可选: 默认为 HNSW
                .initializeSchema(true)              // 可选: 默认为 false
                .schemaName("public")                // 可选: 默认为 "public"
                .vectorTableName("book_app_vector_store")     // 可选: 默认为 "vector_store"
                .maxDocumentBatchSize(25)         // 可选: 默认为 to 10000，但DashScope API限制为25
                .build();

        List<Document> documents = bookAppDocumentLoader.loadMarkdownDocuments();
        // 分批处理文档，确保每批不超过25个
        for (int i = 0; i < documents.size(); i += 25) {
            int endIndex = Math.min(i + 25, documents.size());
            List<Document> batch = documents.subList(i, endIndex);
            pgVectorStore.doAdd(batch);
        }

        return pgVectorStore;

    }
}

