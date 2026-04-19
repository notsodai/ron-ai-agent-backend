package com.ron.ronaiagent.chat.memory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class MemoryConfig {

    @Bean
    @Profile({"dev", "local", "test"})
    public ChatMemoryRepository inMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    @Profile("prod")
    public ChatMemoryRepository jdbcRepository(DataSource dataSource) {
        return new JdbcChatMemoryRepository(new JdbcTemplate(dataSource));
    }
}
