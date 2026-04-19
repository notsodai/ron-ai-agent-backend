package com.ron.ronaiagent.examples.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * @author admin
 * @date 2025/9/9 下午9:20
 */
public class BookAppQueryErichDemo {

    private final Logger logger = LoggerFactory.getLogger(BookAppQueryErichDemo.class);

    private final ChatClient.Builder chatClientBuilder;

    public BookAppQueryErichDemo(ChatModel dashScopeChatModel) {
        this.chatClientBuilder = ChatClient.builder(dashScopeChatModel);
    }

    @Bean
    public List<Query> multiQueryExpander() {
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .numberOfQueries(3)
                .build();

        List<Query> queries = queryExpander.expand(new Query("悬疑类书籍和推理类书籍的区别是什么？"));
        logger.info("queries: {}", queries);
        return queries;
    }

}
