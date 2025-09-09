package com.ron.ronaiagent.chat.chat.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * @author admin
 * @date 2025/9/9 下午10:05
 */
@Component
public class BookAppQueryRewriter {
    private final QueryTransformer queryTransformer;

    public BookAppQueryRewriter(ChatModel dashScopeChatModel) {
        ChatClient.Builder builder = ChatClient.builder(dashScopeChatModel);
        queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();
    }

    /**
     * 重写query
     * @param question 查询问题
     * @return 重写后的query
     */
    public String rewrite(String question) {
        Query query = new Query(question);
        // 获取重写后的query
        Query transformedQuery = queryTransformer.transform(query);
        return transformedQuery.text();
    }
}
