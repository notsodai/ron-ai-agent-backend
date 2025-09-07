package com.ron.ronaiagent.chat.chat.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author admin
 * @date 2025/9/7 上午11:12
 */
@SpringBootTest
class BookAppRagPgVectorConfigTest {

    @Resource
    private VectorStore bookAppPgVectorStore;

    @Test
    void bookAppPgVectorStore() {
        List<Document> documents = List.of(
                new Document("成年之后进行审美再教育的方法", Map.of("fileName", "《成年之后进行审美再教育的方法》")),
                new Document("下班之后可以继续学习一些自己感兴趣的东西", Map.of("fileName", "《下班后的生活》")),
                new Document("吉他应该是这样学的", Map.of("fileName", "《吉他攻略》"))
        );

        bookAppPgVectorStore.add(documents);

        List<Document> result = bookAppPgVectorStore.similaritySearch(SearchRequest.builder().query("成年后如何进行审美再教育").topK(5).build());
        assertFalse(result.isEmpty());
    }
}