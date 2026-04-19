package com.ron.ronaiagent.chat.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JdbcChatMemoryRepositoryTest {

    private JdbcChatMemoryRepository repository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new JdbcChatMemoryRepository(jdbcTemplate);
    }

    @Test
    void saveAndRetrieve_messages_shouldPersistAcrossInstances() {
        List<Message> messages = List.of(
                new UserMessage("Hello"),
                new AssistantMessage("Hi there!")
        );
        repository.saveAll("conv-1", messages);

        JdbcChatMemoryRepository newRepo = new JdbcChatMemoryRepository(jdbcTemplate);
        List<Message> retrieved = newRepo.findByConversationId("conv-1");
        assertEquals(2, retrieved.size());
        assertEquals("Hello", retrieved.get(0).getText());
    }

    @Test
    void deleteByConversationId_shouldRemoveAllMessages() {
        repository.saveAll("conv-2", List.of(new UserMessage("Test")));
        repository.deleteByConversationId("conv-2");
        assertTrue(repository.findByConversationId("conv-2").isEmpty());
    }

    @Test
    void findByConversationId_nonExistent_shouldReturnEmpty() {
        assertTrue(repository.findByConversationId("nonexistent").isEmpty());
    }

    @Test
    void findConversationIds_shouldReturnAllIds() {
        repository.saveAll("conv-a", List.of(new UserMessage("A")));
        repository.saveAll("conv-b", List.of(new UserMessage("B")));
        List<String> ids = repository.findConversationIds();
        assertTrue(ids.contains("conv-a"));
        assertTrue(ids.contains("conv-b"));
    }

    @Test
    void eviction_shouldEnforceMaxMessages() {
        JdbcChatMemoryRepository limitedRepo = new JdbcChatMemoryRepository(jdbcTemplate, 5);
        for (int i = 0; i < 10; i++) {
            limitedRepo.saveAll("conv-limited", List.of(new UserMessage("Msg " + i)));
        }
        List<Message> messages = limitedRepo.findByConversationId("conv-limited");
        assertTrue(messages.size() <= 5);
    }
}
