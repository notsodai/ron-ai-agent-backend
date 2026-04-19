package com.ron.ronaiagent.chat.memory;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

public class JdbcChatMemoryRepository implements ChatMemoryRepository {
    private final JdbcTemplate jdbcTemplate;
    private final int maxMessagesPerConversation;

    public JdbcChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, 100);
    }

    public JdbcChatMemoryRepository(JdbcTemplate jdbcTemplate, int maxMessagesPerConversation) {
        this.jdbcTemplate = jdbcTemplate;
        this.maxMessagesPerConversation = maxMessagesPerConversation;
    }

    @Override
    public @NotNull List<String> findConversationIds() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT conversation_id FROM chat_memory", String.class);
    }

    @Override
    public @NotNull List<Message> findByConversationId(@NotNull String conversationId) {
        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT message_type, content FROM chat_memory WHERE conversation_id = ? ORDER BY created_at ASC",
                conversationId);

        List<Message> messages = new ArrayList<>();
        for (java.util.Map<String, Object> row : rows) {
            String type = (String) row.get("message_type");
            String content = (String) row.get("content");
            messages.add(createMessage(type, content));
        }
        return messages;
    }

    @Override
    public void saveAll(@NotNull String conversationId, @NotNull List<Message> messages) {
        for (Message message : messages) {
            jdbcTemplate.update(
                    "INSERT INTO chat_memory (conversation_id, message_type, content) VALUES (?, ?, ?)",
                    conversationId, getMessageType(message), message.getText());
        }
        evictOldMessages(conversationId);
    }

    @Override
    public void deleteByConversationId(@NotNull String conversationId) {
        jdbcTemplate.update("DELETE FROM chat_memory WHERE conversation_id = ?", conversationId);
    }

    private void evictOldMessages(String conversationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_memory WHERE conversation_id = ?", Integer.class, conversationId);
        if (count != null && count > maxMessagesPerConversation) {
            int toDelete = count - maxMessagesPerConversation;
            jdbcTemplate.update(
                    "DELETE FROM chat_memory WHERE conversation_id = ? AND id IN " +
                    "(SELECT id FROM chat_memory WHERE conversation_id = ? ORDER BY created_at ASC LIMIT ?)",
                    conversationId, conversationId, toDelete);
        }
    }

    private String getMessageType(Message message) {
        if (message instanceof UserMessage) return "USER";
        if (message instanceof AssistantMessage) return "ASSISTANT";
        if (message instanceof SystemMessage) return "SYSTEM";
        return "UNKNOWN";
    }

    private Message createMessage(String type, String content) {
        return switch (type) {
            case "USER" -> new UserMessage(content);
            case "ASSISTANT" -> new AssistantMessage(content);
            case "SYSTEM" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }
}
