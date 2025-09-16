package com.ron.ronaiagent.chat.rag;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建一个 rag 检索增强的 Advisor
 */
@Configuration
public class BookAppRagCloudAdvisorConfig {

    /**
     * 创建一个 rag 检索增强的 Advisor
     * @return Advisor
     */
    @Bean
    public Advisor bookAppRagCloudAdvisor() {
        DashScopeApi dashscopeApiKey = DashScopeApi.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .workSpaceId("llm-q1wk2a9eblc8clvi")
                .build();

        String KNOWLEDGE_BASE = "读书助手";
        DocumentRetriever retriever = new DashScopeDocumentRetriever(dashscopeApiKey,
                DashScopeDocumentRetrieverOptions.builder()
                        .withIndexName(KNOWLEDGE_BASE)
                        .build());


        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .build();
    }
}
